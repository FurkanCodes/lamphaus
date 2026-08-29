const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ARTWORK_PROVIDER_ID = "artwork.tmdb";
const TMDB_API_BASE = "https://api.themoviedb.org/3";

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

function encryptionKey(): Promise<CryptoKey> {
  const secret = Deno.env.get("PROVIDER_CONFIG_KEY");
  if (!secret) return Promise.reject(new Error("PROVIDER_CONFIG_KEY not set"));
  encryptionKeyPromise ??= crypto.subtle.importKey(
    "raw",
    b64Decode(secret),
    "AES-GCM",
    false,
    ["decrypt"],
  );
  return encryptionKeyPromise;
}

async function decryptConfig(userId: string, blob: string): Promise<Record<string, unknown>> {
  const [version, ivText, ciphertextText] = blob.split(".");
  if (version !== "v1" || !ivText || !ciphertextText) throw new Error("unsupported_blob_format");
  const plaintext = await crypto.subtle.decrypt(
    {
      name: "AES-GCM",
      iv: b64Decode(ivText),
      additionalData: encoder.encode(`${userId}:${ARTWORK_PROVIDER_ID}`),
    },
    await encryptionKey(),
    b64Decode(ciphertextText),
  );
  const config: unknown = JSON.parse(decoder.decode(plaintext));
  if (typeof config !== "object" || config === null || Array.isArray(config)) {
    throw new Error("invalid_config");
  }
  return config as Record<string, unknown>;
}

async function loadEncryptedConfig(userId: string): Promise<string | null> {
  const query = new URLSearchParams({
    select: "encrypted_config",
    user_id: `eq.${userId}`,
    provider_id: `eq.${ARTWORK_PROVIDER_ID}`,
  });
  const response = await fetch(`${SB_URL}/rest/v1/provider_configs?${query}`, {
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
  });
  if (!response.ok) throw new Error("config_lookup_failed");
  const rows: unknown = await response.json();
  if (!Array.isArray(rows)) throw new Error("config_lookup_failed");
  const first = rows[0];
  if (typeof first !== "object" || first === null || Array.isArray(first)) return null;
  const encryptedConfig = (first as Record<string, unknown>).encrypted_config;
  return typeof encryptedConfig === "string" ? encryptedConfig : null;
}

type TmdbTitle = {
  kind: "movie" | "tv";
  id: number;
  posterPath?: string;
  backdropPath?: string;
};

function collectTitles(payload: unknown): TmdbTitle[] {
  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) return [];
  const root = payload as Record<string, unknown>;
  const titles: TmdbTitle[] = [];
  const seen = new Set<string>();
  const addResults = (resultSet: unknown, kind: "movie" | "tv" | null) => {
    if (!Array.isArray(resultSet)) return;
    for (const result of resultSet) {
      if (typeof result !== "object" || result === null || Array.isArray(result)) continue;
      const item = result as Record<string, unknown>;
      const resultKind = kind ?? (item.media_type === "movie" || item.media_type === "tv" ? item.media_type : null);
      const id = typeof item.id === "number" && Number.isInteger(item.id) ? item.id : null;
      if (resultKind === null || id === null) continue;
      const key = `${resultKind}:${id}`;
      if (seen.has(key)) continue;
      seen.add(key);
      titles.push({
        kind: resultKind,
        id,
        posterPath: typeof item.poster_path === "string" ? item.poster_path : undefined,
        backdropPath: typeof item.backdrop_path === "string" ? item.backdrop_path : undefined,
      });
    }
  };
  addResults(root.movie_results, "movie");
  addResults(root.tv_results, "tv");
  addResults(root.results, null);
  return titles;
}

function addImagePaths(
  payload: unknown,
  posters: Set<string>,
  backdrops: Set<string>,
  logos: Set<string>,
) {
  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) return;
  const root = payload as Record<string, unknown>;
  const addPaths = (value: unknown, target: Set<string>) => {
    if (!Array.isArray(value)) return;
    for (const image of value) {
      if (typeof image !== "object" || image === null || Array.isArray(image)) continue;
      const filePath = (image as Record<string, unknown>).file_path;
      if (typeof filePath === "string" && filePath.startsWith("/")) target.add(filePath);
    }
  };
  addPaths(root.posters, posters);
  addPaths(root.backdrops, backdrops);
  addPaths(root.logos, logos);
}

async function fetchTitleImages(apiKey: string, title: TmdbTitle): Promise<unknown | null> {
  const params = new URLSearchParams({
    api_key: apiKey,
    include_image_language: "en,null",
  });
  const response = await fetch(`${TMDB_API_BASE}/${title.kind}/${title.id}/images?${params}`, {
    headers: { accept: "application/json" },
  });
  if (!response.ok) {
    console.error("TMDB image lookup failed", response.status);
    return null;
  }
  return response.json();
}

async function collectImagePaths(
  apiKey: string,
  payload: unknown,
): Promise<{ posters: string[]; backdrops: string[]; logos: string[] }> {
  const titles = collectTitles(payload);
  const posters = new Set<string>();
  const backdrops = new Set<string>();
  const logos = new Set<string>();
  for (const title of titles) {
    if (title.posterPath?.startsWith("/")) posters.add(title.posterPath);
    if (title.backdropPath?.startsWith("/")) backdrops.add(title.backdropPath);
  }
  await Promise.all(
    titles.slice(0, 8).map(async (title) => {
      const images = await fetchTitleImages(apiKey, title);
      addImagePaths(images, posters, backdrops, logos);
    }),
  );
  return { posters: [...posters], backdrops: [...backdrops], logos: [...logos] };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const provider = typeof body.provider === "string" ? body.provider.trim().toLowerCase() : "tmdb";
  if (provider !== "tmdb") return json({ error: "unsupported_provider" }, 400);

  const mediaKey = typeof body.media_key === "string" ? body.media_key.trim().slice(0, 300) : "";
  const name = typeof body.name === "string" ? body.name.trim().slice(0, 200) : "";
  const releaseYear = Number.isInteger(body.release_year) ? body.release_year as number : null;
  if (!mediaKey || !name) return json({ error: "missing_media_identity" }, 400);

  let encryptedConfig: string | null;
  try {
    encryptedConfig = await loadEncryptedConfig(user.id);
  } catch (error) {
    console.error("artwork config lookup failed", error instanceof Error ? error.name : "unknown");
    return json({ error: "config_lookup_failed" }, 500);
  }
  if (!encryptedConfig) return json({ error: "artwork_key_not_configured" }, 404);

  let apiKey: string;
  try {
    const config = await decryptConfig(user.id, encryptedConfig);
    apiKey = typeof config.api_key === "string" ? config.api_key : "";
  } catch (error) {
    console.error("artwork config decryption failed", error instanceof Error ? error.name : "unknown");
    return json({ error: "config_decrypt_failed" }, 500);
  }
  if (!apiKey) return json({ error: "invalid_artwork_config" }, 500);

  const externalId = mediaKey.slice(mediaKey.lastIndexOf(":") + 1);
  const isImdbId = /^tt\d+$/i.test(externalId);
  const params = new URLSearchParams({ api_key: apiKey, include_adult: "false" });
  let endpoint: string;
  if (isImdbId) {
    endpoint = `${TMDB_API_BASE}/find/${encodeURIComponent(externalId)}?${new URLSearchParams({
      api_key: apiKey,
      external_source: "imdb_id",
    })}`;
  } else {
    params.set("query", name);
    if (releaseYear !== null) params.set("year", String(releaseYear));
    endpoint = `${TMDB_API_BASE}/search/multi?${params}`;
  }

  const tmdbResponse = await fetch(endpoint, { headers: { accept: "application/json" } });
  if (!tmdbResponse.ok) {
    console.error("TMDB artwork lookup failed", tmdbResponse.status);
    return json({ error: tmdbResponse.status === 401 ? "invalid_artwork_key" : "lookup_failed" }, 502);
  }

  const candidates = await collectImagePaths(apiKey, await tmdbResponse.json());
  return json({ provider: "tmdb", ...candidates });
});
