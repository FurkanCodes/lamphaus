import { createProviderConfigCrypto } from "../_shared/provider_config_crypto.ts";

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

const providerConfigCrypto = createProviderConfigCrypto({
  activeKeyId: Deno.env.get("PROVIDER_CONFIG_ACTIVE_KEY_ID") ?? "",
  encodedKeys: Deno.env.get("PROVIDER_CONFIG_KEYRING") ?? "",
  legacyEncodedKey: Deno.env.get("PROVIDER_CONFIG_KEY"),
});

async function catalogProvider(provider: string): Promise<boolean> {
  const query = new URLSearchParams({ select: "id,enabled", id: `eq.${provider}`, limit: "1" });
  const response = await fetch(`${SB_URL}/rest/v1/artwork_providers?${query}`, {
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
  });
  if (!response.ok) throw new Error("catalog_lookup_failed");
  const rows: unknown = await response.json();
  if (!Array.isArray(rows)) throw new Error("catalog_invalid");
  const row = rows[0];
  return typeof row === "object" && row !== null && !Array.isArray(row) &&
    (row as Record<string, unknown>).id === provider && (row as Record<string, unknown>).enabled === true;
}


Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);
  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const provider = typeof body.provider === "string" ? body.provider.trim().toLowerCase() : "";
  if (!ID_PATTERN.test(provider)) return json({ error: "unsupported_provider" }, 400);
  let supported: boolean;
  try {
    supported = await catalogProvider(provider);
  } catch (error) {
    console.error("artwork provider catalog lookup failed", error instanceof Error ? error.name : "unknown");
    return json({ error: "save_failed" }, 500);
  }
  if (!supported) return json({ error: "unsupported_provider" }, 400);
  const apiKey = typeof body.api_key === "string" ? body.api_key.trim() : "";
  if (!apiKey || apiKey.length > 512) return json({ error: "invalid_api_key" }, 400);
  if (provider === "tmdb") {
    // Storage-gate for the TMDB credential: an invalid key must never land in
    // provider_configs, or artwork and detail enrichment silently degrade to
    // empty responses for the whole TTL. Mirrors the MDBList save contract —
    // validation runs before storage and answers 400 invalid_credential.
    // The key is never logged and never echoed back (SHR-PROD-06).
    const probe = await fetch(
      `https://api.themoviedb.org/3/configuration?api_key=${encodeURIComponent(apiKey)}`,
      { headers: { accept: "application/json" } },
    ).catch(() => null);
    if (probe === null || !probe.ok) return json({ error: "invalid_credential" }, 400);
  }

  let encryptedConfig: string;
  try {
    encryptedConfig = await providerConfigCrypto.encrypt(
      user.id,
      `artwork.${provider}`,
      { api_key: apiKey },
    );
  } catch (error) {
    console.error("artwork config encryption failed", provider, error instanceof Error ? error.message : "unknown");
    return json({ error: "encrypt_failed" }, 500);
  }
  const response = await fetch(`${SB_URL}/rest/v1/provider_configs`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
      Prefer: "resolution=merge-duplicates,return=minimal",
    },
    body: JSON.stringify({
      user_id: user.id,
      provider_id: `artwork.${provider}`,
      display_name: provider,
      enabled: true,
      sort_order: 0,
      encrypted_config: encryptedConfig,
      updated_at_epoch_millis: Date.now(),
    }),
  });
  if (!response.ok) return json({ error: "save_failed" }, 500);
  return json({ ok: true, provider });
});
