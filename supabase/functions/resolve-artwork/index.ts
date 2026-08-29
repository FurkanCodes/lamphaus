import {
  combineProviderResults,
  createProviderRegistry,
  executeProviderAdapters,
  failedProviderResolution,
  seedRequestIdentifiers,
  type ArtworkProviderId,
  type ArtworkProviderResult,
  type ArtworkRequest,
  type ArtworkMediaType,
} from "./provider.ts";
import { fanartAdapter } from "./providers/fanart.ts";
import { tmdbAdapter } from "./providers/tmdb.ts";

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
const ID_PATTERN = /^[a-z][a-z0-9_-]{0,63}$/;
const registry = createProviderRegistry([tmdbAdapter, fanartAdapter]);

type CatalogProvider = {
  id: ArtworkProviderId;
  display_name: string;
  purpose: string;
  help_text: string;
  key_page_url: string;
  sort_order: number;
  enabled: boolean;
};

type ConfiguredProvider = {
  id: ArtworkProviderId;
  encrypted_config: string;
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

async function requireUser(req: Request): Promise<{ id: string } | null> {
  const authorization = req.headers.get("Authorization");
  if (!authorization) return null;
  const response = await fetch(`${SB_URL}/auth/v1/user`, {
    headers: { apikey: ANON_KEY, Authorization: authorization },
  });
  if (!response.ok) return null;
  const user = await response.json();
  return typeof user?.id === "string" ? user : null;
}

const encoder = new TextEncoder();
const decoder = new TextDecoder();
let encryptionKeyPromise: Promise<CryptoKey> | null = null;

function b64Decode(text: string): Uint8Array {
  return Uint8Array.from(atob(text), (character) => character.charCodeAt(0));
}
const bufferSource = (bytes: Uint8Array): BufferSource => bytes as unknown as BufferSource;

function encryptionKey(): Promise<CryptoKey> {
  const secret = Deno.env.get("PROVIDER_CONFIG_KEY");
  if (!secret) return Promise.reject(new Error("encryption_key_unavailable"));
  encryptionKeyPromise ??= crypto.subtle.importKey(
    "raw",
    bufferSource(b64Decode(secret)),
    "AES-GCM",
    false,
    ["decrypt"],
  );
  return encryptionKeyPromise;
}

async function decryptConfig(userId: string, provider: ArtworkProviderId, blob: string): Promise<string> {
  const [version, ivText, ciphertextText] = blob.split(".");
  if (version !== "v1" || !ivText || !ciphertextText) throw new Error("invalid_encrypted_config");
  const plaintext = await crypto.subtle.decrypt(
    {
      name: "AES-GCM",
      iv: bufferSource(b64Decode(ivText)),
      additionalData: bufferSource(encoder.encode(`${userId}:artwork.${provider}`)),
    },
    await encryptionKey(),
    bufferSource(b64Decode(ciphertextText)),
  );
  const config: unknown = JSON.parse(decoder.decode(plaintext));
  const apiKey = typeof config === "object" && config !== null && !Array.isArray(config)
    ? (config as Record<string, unknown>).api_key
    : null;
  if (typeof apiKey !== "string" || apiKey.trim().length < 1 || apiKey.length > 512) {
    throw new Error("invalid_decrypted_config");
  }
  return apiKey;
}

function validCatalogRow(value: unknown): value is CatalogProvider {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return false;
  const row = value as Record<string, unknown>;
  if (typeof row.id !== "string" || !ID_PATTERN.test(row.id)) return false;
  for (const key of ["display_name", "purpose", "help_text", "key_page_url"] as const) {
    if (typeof row[key] !== "string" || row[key].trim() === "" || row[key].length > 500) return false;
  }
  if (typeof row.sort_order !== "number" || !Number.isInteger(row.sort_order)) return false;
  if (typeof row.enabled !== "boolean") return false;
  try {
    const url = new URL(row.key_page_url as string);
    if (url.protocol !== "https:" || !url.hostname) return false;
  } catch {
    return false;
  }
  return true;
}

async function loadCatalog(): Promise<CatalogProvider[]> {
  const query = new URLSearchParams({
    select: "id,display_name,purpose,help_text,key_page_url,sort_order,enabled",
    order: "sort_order.asc,id.asc",
  });
  const response = await fetch(`${SB_URL}/rest/v1/artwork_providers?${query}`, {
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
  });
  if (!response.ok) throw new Error("catalog_lookup_failed");
  const rows: unknown = await response.json();
  if (!Array.isArray(rows)) throw new Error("catalog_invalid");
  const catalog = rows.filter(validCatalogRow);
  if (catalog.length !== rows.length || new Set(catalog.map((row) => row.id)).size !== catalog.length) {
    throw new Error("catalog_invalid");
  }
  return catalog;
}

async function loadConfigs(userId: string): Promise<ConfiguredProvider[]> {
  const query = new URLSearchParams({ select: "provider_id,encrypted_config", user_id: `eq.${userId}` });
  const response = await fetch(`${SB_URL}/rest/v1/provider_configs?${query}`, {
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
  });
  if (!response.ok) throw new Error("config_lookup_failed");
  const rows: unknown = await response.json();
  if (!Array.isArray(rows)) throw new Error("config_lookup_failed");
  return rows.flatMap((row) => {
    if (typeof row !== "object" || row === null || Array.isArray(row)) return [];
    const data = row as Record<string, unknown>;
    const providerId = typeof data.provider_id === "string" && data.provider_id.startsWith("artwork.")
      ? data.provider_id.slice("artwork.".length)
      : null;
    return providerId && ID_PATTERN.test(providerId) && typeof data.encrypted_config === "string"
      ? [{ id: providerId, encrypted_config: data.encrypted_config }]
      : [];
  });
}

function mediaType(value: unknown): ArtworkMediaType {
  return value === "movie" || value === "series" ? value : "unknown";
}

function requestFromBody(body: Record<string, unknown>): ArtworkRequest | null {
  const mediaKey = typeof body.media_key === "string" ? body.media_key.trim().slice(0, 300) : "";
  const name = typeof body.name === "string" ? body.name.trim().slice(0, 200) : "";
  const releaseYear = Number.isInteger(body.release_year) ? body.release_year as number : null;
  if (!mediaKey || !name) return null;
  return { mediaKey, name, releaseYear, mediaType: mediaType(body.media_type) };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);
  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const request = requestFromBody(body);
  if (!request) return json({ error: "missing_media_identity" }, 400);
  const versioned = body.contract_version === 2;

  let catalog: CatalogProvider[];
  let configs: ConfiguredProvider[];
  try {
    catalog = await loadCatalog();
    configs = await loadConfigs(user.id);
  } catch (error) {
    console.error("artwork catalog/config lookup failed", error instanceof Error ? error.name : "unknown");
    return json({ error: error instanceof Error && error.message === "catalog_invalid" ? "catalog_invalid" : "config_lookup_failed" }, 500);
  }

  const visibleCatalog = versioned ? catalog : catalog.filter((provider) => provider.id === "tmdb" || provider.id === "fanart");
  const configured = configs.filter((config) => visibleCatalog.some((provider) => provider.id === config.id && provider.enabled));
  if (configured.length === 0) return json({ error: "artwork_key_not_configured" }, 404);

  const validEntries: Array<{ id: ArtworkProviderId; apiKey: string; displayName: string; sortOrder: number }> = [];
  const results: ArtworkProviderResult[] = [];
  for (const config of configured) {
    const definition = visibleCatalog.find((provider) => provider.id === config.id)!;
    try {
      validEntries.push({
        id: config.id,
        apiKey: await decryptConfig(user.id, config.id, config.encrypted_config),
        displayName: definition.display_name,
        sortOrder: definition.sort_order,
      });
    } catch (error) {
      console.error("artwork provider config invalid", config.id, error instanceof Error ? error.name : "unknown");
      results.push({ ...failedProviderResolution(config.id), displayName: definition.display_name, sortOrder: definition.sort_order });
    }
  }

  if (validEntries.length) {
    const resolutions = await executeProviderAdapters(registry, request, validEntries, seedRequestIdentifiers(request));
    results.push(...resolutions);
  }
  const responseResults = versioned
    ? results
    : results.map(({ provider, status, posters, backdrops, logos }) => ({
      provider,
      status,
      posters,
      backdrops,
      logos,
    }));
  return json(combineProviderResults(responseResults, versioned ? visibleCatalog.map((provider) => ({
    id: provider.id,
    sortOrder: provider.sort_order,
    displayName: provider.display_name,
  })) : []));
});
