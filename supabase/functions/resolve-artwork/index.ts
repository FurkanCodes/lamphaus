import {
  combineProviderResults,
  normalizeFanartPayload,
  normalizeTmdbImagePayload,
  normalizeTmdbMatches,
  normalizeTmdbSearchArtwork,
  type ArtworkMediaType,
  type ArtworkProvider,
  type ArtworkProviderResult,
  type ArtworkLists,
  type TmdbMatch,
} from "./provider.ts";

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const TMDB_API_BASE = "https://api.themoviedb.org/3";
const FANART_API_BASE = "https://webservice.fanart.tv/v3.2";
const ARTWORK_PROVIDER_IDS: Record<ArtworkProvider, string> = {
  tmdb: "artwork.tmdb",
  fanart: "artwork.fanart",
};

const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
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
  if (!secret) return Promise.reject(new Error("PROVIDER_CONFIG_KEY not set"));
  encryptionKeyPromise ??= crypto.subtle.importKey(
    "raw",
    bufferSource(b64Decode(secret)),
    "AES-GCM",
    false,
    ["decrypt"],
  );
  return encryptionKeyPromise;
}

async function decryptConfig(
  userId: string,
  provider: ArtworkProvider,
  blob: string,
): Promise<Record<string, unknown>> {
  const [version, ivText, ciphertextText] = blob.split(".");
  if (version !== "v1" || !ivText || !ciphertextText) throw new Error("unsupported_blob_format");
  const plaintext = await crypto.subtle.decrypt(
    {
      name: "AES-GCM",
      iv: bufferSource(b64Decode(ivText)),
      additionalData: bufferSource(encoder.encode(`${userId}:${ARTWORK_PROVIDER_IDS[provider]}`)),
    },
    await encryptionKey(),
    bufferSource(b64Decode(ciphertextText)),
  );
  const config: unknown = JSON.parse(decoder.decode(plaintext));
  if (typeof config !== "object" || config === null || Array.isArray(config)) {
    throw new Error("invalid_config");
  }
  return config as Record<string, unknown>;
}

async function loadEncryptedConfigs(userId: string): Promise<Partial<Record<ArtworkProvider, string>>> {
  const query = new URLSearchParams({
    select: "provider_id,encrypted_config",
    user_id: `eq.${userId}`,
    provider_id: `in.(${Object.values(ARTWORK_PROVIDER_IDS).join(",")})`,
  });
  const response = await fetch(`${SB_URL}/rest/v1/provider_configs?${query}`, {
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
  });
  if (!response.ok) throw new Error("config_lookup_failed");
  const rows: unknown = await response.json();
  if (!Array.isArray(rows)) throw new Error("config_lookup_failed");
  const configs: Partial<Record<ArtworkProvider, string>> = {};
  for (const row of rows) {
    if (typeof row !== "object" || row === null || Array.isArray(row)) continue;
    const data = row as Record<string, unknown>;
    const provider = (Object.entries(ARTWORK_PROVIDER_IDS) as Array<[ArtworkProvider, string]>)
      .find(([, providerId]) => providerId === data.provider_id)?.[0];
    if (provider && typeof data.encrypted_config === "string") configs[provider] = data.encrypted_config;
  }
  return configs;
}

type JsonResponse = {
  response: Response | null;
  payload: unknown | null;
};

async function requestJson(url: string, init: RequestInit): Promise<JsonResponse> {
  try {
    const response = await fetch(url, init);
    const payload = await response.json().catch(() => null);
    return { response, payload };
  } catch {
    return { response: null, payload: null };
  }
}

function emptyLists(): ArtworkLists {
  return { posters: [], backdrops: [], logos: [] };
}

function appendLists(target: ArtworkLists, additions: ArtworkLists): void {
  target.posters.push(...additions.posters);
  target.backdrops.push(...additions.backdrops);
  target.logos.push(...additions.logos);
}

function mediaType(value: unknown): ArtworkMediaType {
  return value === "movie" || value === "series" ? value : "unknown";
}

function imdbSuffix(mediaKey: string): string | null {
  const suffix = mediaKey.slice(mediaKey.lastIndexOf(":") + 1);
  return /^tt\d+$/i.test(suffix) ? suffix : null;
}
function numericSuffix(mediaKey: string): string | null {
  const suffix = mediaKey.slice(mediaKey.lastIndexOf(":") + 1);
  return /^\d+$/.test(suffix) ? suffix : null;
}

function tmdbSearchUrl(
  apiKey: string,
  mediaKey: string,
  name: string,
  releaseYear: number | null,
): string {
  const externalId = imdbSuffix(mediaKey);
  if (externalId) {
    const params = new URLSearchParams({ api_key: apiKey, external_source: "imdb_id" });
    return `${TMDB_API_BASE}/find/${encodeURIComponent(externalId)}?${params}`;
  }
  const params = new URLSearchParams({ api_key: apiKey, query: name, include_adult: "false" });
  if (releaseYear !== null) params.set("year", String(releaseYear));
  return `${TMDB_API_BASE}/search/multi?${params}`;
}

type TmdbLookup = {
  result: ArtworkProviderResult;
  matches: TmdbMatch[];
  bridgeAvailable: boolean;
  apiKey: string;
};

async function resolveTmdb(
  apiKey: string,
  mediaKey: string,
  name: string,
  releaseYear: number | null,
  requestedMediaType: ArtworkMediaType,
): Promise<TmdbLookup> {
  const search = await requestJson(
    tmdbSearchUrl(apiKey, mediaKey, name, releaseYear),
    { headers: { accept: "application/json" } },
  );
  if (!search.response) {
    return { result: { provider: "tmdb", status: "lookup_failed", ...emptyLists() }, matches: [], bridgeAvailable: false, apiKey };
  }
  if (search.response.status === 401) {
    return { result: { provider: "tmdb", status: "invalid_key", ...emptyLists() }, matches: [], bridgeAvailable: false, apiKey };
  }
  if (!search.response.ok || search.payload === null) {
    return { result: { provider: "tmdb", status: "lookup_failed", ...emptyLists() }, matches: [], bridgeAvailable: false, apiKey };
  }

  const matches = normalizeTmdbMatches(search.payload, requestedMediaType);
  if (matches.length === 0) {
    return { result: { provider: "tmdb", status: "no_match", ...emptyLists() }, matches, bridgeAvailable: true, apiKey };
  }

  const artwork = normalizeTmdbSearchArtwork(matches);
  let imageLookupFailed = false;
  let invalidKey = false;
  for (const match of matches.slice(0, 8)) {
    const params = new URLSearchParams({ api_key: apiKey, include_image_language: "en,null" });
    const images = await requestJson(
      `${TMDB_API_BASE}/${match.kind}/${match.id}/images?${params}`,
      { headers: { accept: "application/json" } },
    );
    if (!images.response) {
      imageLookupFailed = true;
    } else if (images.response.status === 401) {
      invalidKey = true;
    } else if (!images.response.ok || images.payload === null) {
      imageLookupFailed = true;
    } else {
      appendLists(artwork, normalizeTmdbImagePayload(images.payload));
    }
  }
  const status = invalidKey ? "invalid_key" : imageLookupFailed ? "lookup_failed" : "success";
  return { result: { provider: "tmdb", status, ...artwork }, matches, bridgeAvailable: true, apiKey };
}

async function resolveFanart(
  apiKey: string,
  mediaKey: string,
  requestedMediaType: ArtworkMediaType,
  tmdbLookup: TmdbLookup | null,
): Promise<ArtworkProviderResult> {
  let externalId: string | null = null;
  if (requestedMediaType === "movie") {
    const tmdbMovie = tmdbLookup?.matches.find((match) => match.kind === "movie");
    externalId = tmdbMovie ? String(tmdbMovie.id) : numericSuffix(mediaKey) ?? imdbSuffix(mediaKey);
  } else if (requestedMediaType === "series") {
    const tmdbSeries = tmdbLookup?.matches.find((match) => match.kind === "tv");
    if (!tmdbLookup?.bridgeAvailable || !tmdbSeries || !tmdbLookup.apiKey) {
      return { provider: "fanart", status: "missing_external_id", ...emptyLists() };
    }
    const params = new URLSearchParams({ api_key: tmdbLookup.apiKey });
    const externalIds = await requestJson(
      `${TMDB_API_BASE}/tv/${tmdbSeries.id}/external_ids?${params}`,
      { headers: { accept: "application/json" } },
    );
    const tvdbId = externalIds.payload && typeof externalIds.payload === "object" && !Array.isArray(externalIds.payload)
      ? (externalIds.payload as Record<string, unknown>).tvdb_id
      : null;
    externalId = typeof tvdbId === "number" && Number.isInteger(tvdbId)
      ? String(tvdbId)
      : typeof tvdbId === "string" && /^\d+$/.test(tvdbId)
      ? tvdbId
      : null;
  }
  if (!externalId) return { provider: "fanart", status: "missing_external_id", ...emptyLists() };

  const endpoint = requestedMediaType === "movie" ? "movies" : "tv";
  const fanart = await requestJson(`${FANART_API_BASE}/${endpoint}/${encodeURIComponent(externalId)}`, {
    headers: { "api-key": apiKey, accept: "application/json" },
  });
  if (!fanart.response) return { provider: "fanart", status: "lookup_failed", ...emptyLists() };
  if (fanart.response.status === 401 || fanart.response.status === 403) {
    return { provider: "fanart", status: "invalid_key", ...emptyLists() };
  }
  if (fanart.response.status === 404) return { provider: "fanart", status: "no_match", ...emptyLists() };
  if (!fanart.response.ok || fanart.payload === null) {
    return { provider: "fanart", status: "lookup_failed", ...emptyLists() };
  }
  const artwork = normalizeFanartPayload(fanart.payload, requestedMediaType);
  return {
    provider: "fanart",
    status: artwork.posters.length || artwork.backdrops.length || artwork.logos.length ? "success" : "no_match",
    ...artwork,
  };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const mediaKey = typeof body.media_key === "string" ? body.media_key.trim().slice(0, 300) : "";
  const name = typeof body.name === "string" ? body.name.trim().slice(0, 200) : "";
  const releaseYear = Number.isInteger(body.release_year) ? body.release_year as number : null;
  const requestedMediaType = mediaType(body.media_type);
  if (!mediaKey || !name) return json({ error: "missing_media_identity" }, 400);

  let configs: Partial<Record<ArtworkProvider, string>>;
  try {
    configs = await loadEncryptedConfigs(user.id);
  } catch (error) {
    console.error("artwork config lookup failed", error instanceof Error ? error.name : "unknown");
    return json({ error: "config_lookup_failed" }, 500);
  }
  const configuredProviders = (Object.keys(configs) as ArtworkProvider[]).filter((provider) => Boolean(configs[provider]));
  if (configuredProviders.length === 0) return json({ error: "artwork_key_not_configured" }, 404);

  let tmdbLookup: TmdbLookup | null = null;
  const results: ArtworkProviderResult[] = [];
  if (configs.tmdb) {
    try {
      const config = await decryptConfig(user.id, "tmdb", configs.tmdb);
      const apiKey = typeof config.api_key === "string" ? config.api_key : "";
      tmdbLookup = apiKey
        ? await resolveTmdb(apiKey, mediaKey, name, releaseYear, requestedMediaType)
        : { result: { provider: "tmdb", status: "lookup_failed", ...emptyLists() }, matches: [], bridgeAvailable: false, apiKey: "" };
    } catch (error) {
      console.error("TMDB artwork config decryption failed", error instanceof Error ? error.name : "unknown");
      tmdbLookup = { result: { provider: "tmdb", status: "lookup_failed", ...emptyLists() }, matches: [], bridgeAvailable: false, apiKey: "" };
    }
    results.push(tmdbLookup.result);
  }
  if (configs.fanart) {
    try {
      const config = await decryptConfig(user.id, "fanart", configs.fanart);
      const apiKey = typeof config.api_key === "string" ? config.api_key : "";
      results.push(apiKey
        ? await resolveFanart(apiKey, mediaKey, requestedMediaType, tmdbLookup)
        : { provider: "fanart", status: "lookup_failed", ...emptyLists() });
    } catch (error) {
      console.error("Fanart artwork config decryption failed", error instanceof Error ? error.name : "unknown");
      results.push({ provider: "fanart", status: "lookup_failed", ...emptyLists() });
    }
  }

  return json(combineProviderResults(results));
});
