import { createProviderConfigCrypto } from "../_shared/provider_config_crypto.ts";

// resolve-person-credits — one cast or crew member's best-known titles.
//
// Authenticated (verify_jwt=true). TMDB answers with the caller's stored
// artwork.tmdb key (provider_configs), falling back to TMDB_API_KEY, exactly
// as resolve-detail-enrichment does; the key never reaches the client
// (SHR-PROD-06). Titles come back as MediaPreview JSON keyed by IMDb id, so
// each opens through the viewer's own add-ons like a similar title
// (SHR-PROD-05). Titles without an IMDb id are left out rather than guessed.

const TMDB_BASE = "https://api.themoviedb.org/3";
const TMDB_IMAGE = "https://image.tmdb.org/t/p";
const CREDITS_LIMIT = 30;
/** Talk, news, and award shows crowd out a person's actual work. */
const EXCLUDED_TV_GENRES = new Set([10763, 10764, 10767]);

const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

type FetchImpl = (input: string | URL | Request, init?: RequestInit) => Promise<Response>;

type Decrypt = (
  userId: string,
  providerId: string,
  blob: string,
) => Promise<{ config: unknown }>;

type TmdbCredit = {
  id?: number | null;
  media_type?: string | null;
  title?: string | null;
  name?: string | null;
  character?: string | null;
  job?: string | null;
  poster_path?: string | null;
  backdrop_path?: string | null;
  release_date?: string | null;
  first_air_date?: string | null;
  popularity?: number | null;
  vote_count?: number | null;
  genre_ids?: number[] | null;
  episode_count?: number | null;
};

type TmdbPersonDetails = {
  id?: number | null;
  name?: string | null;
  profile_path?: string | null;
  known_for_department?: string | null;
  combined_credits?: {
    cast?: TmdbCredit[] | null;
    crew?: TmdbCredit[] | null;
  } | null;
};

type TmdbResult<T> =
  | { ok: true; value: T }
  | { ok: false; transient: boolean };

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

function imageUrl(path: string | null | undefined, width: "w185" | "w342" | "w780"): string | null {
  return typeof path === "string" && path.startsWith("/") ? `${TMDB_IMAGE}/${width}${path}` : null;
}

/**
 * The person's distinct movie and series credits, most popular first. Cast
 * and crew credits merge per title; talk/news/reality appearances and
 * titles with almost no audience are dropped.
 */
export function rankCredits(details: TmdbPersonDetails, limit = CREDITS_LIMIT): TmdbCredit[] {
  const all = [
    ...(details.combined_credits?.cast ?? []),
    ...(details.combined_credits?.crew ?? []),
  ];
  const byKey = new Map<string, TmdbCredit>();
  for (const credit of all) {
    if (typeof credit.id !== "number") continue;
    if (credit.media_type !== "movie" && credit.media_type !== "tv") continue;
    if (credit.media_type === "tv" && (credit.genre_ids ?? []).some((genre) => EXCLUDED_TV_GENRES.has(genre))) {
      continue;
    }
    if ((credit.vote_count ?? 0) < 5) continue;
    const key = `${credit.media_type}:${credit.id}`;
    if (!byKey.has(key)) byKey.set(key, credit);
  }
  return [...byKey.values()]
    .sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0))
    .slice(0, limit);
}

export function createResolvePersonCreditsHandler({
  supabaseUrl,
  anonKey,
  serviceRole,
  envTmdbKey,
  decrypt,
  fetchImpl = fetch,
}: {
  supabaseUrl: string;
  anonKey: string;
  serviceRole: string;
  envTmdbKey: string;
  decrypt: Decrypt;
  fetchImpl?: FetchImpl;
}): (req: Request) => Promise<Response> {
  async function requireUser(req: Request): Promise<{ id: string } | null> {
    const authorization = req.headers.get("Authorization");
    if (!authorization) return null;
    const response = await fetchImpl(`${supabaseUrl}/auth/v1/user`, {
      headers: { apikey: anonKey, Authorization: authorization },
    });
    if (!response.ok) return null;
    const user = await response.json();
    return typeof user?.id === "string" ? user : null;
  }

  /** The caller's own artwork.tmdb key, else the deployment's key (same as resolve-detail-enrichment). */
  async function loadTmdbKey(userId: string): Promise<string> {
    try {
      const response = await fetchImpl(
        `${supabaseUrl}/rest/v1/provider_configs?user_id=eq.${userId}&provider_id=eq.artwork.tmdb`,
        { headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}` } },
      );
      if (!response.ok) return envTmdbKey;
      const rows = await response.json() as Array<{ encrypted_config?: unknown }>;
      const blob = rows[0]?.encrypted_config;
      if (typeof blob !== "string" || blob.length === 0) return envTmdbKey;
      const { config } = await decrypt(userId, "artwork.tmdb", blob);
      if (config && typeof config === "object" && "api_key" in config) {
        const apiKey = (config as { api_key?: unknown }).api_key;
        if (typeof apiKey === "string" && apiKey.length > 0) return apiKey;
      }
      return envTmdbKey;
    } catch {
      return envTmdbKey;
    }
  }

  async function tmdbJson<T>(
    apiKey: string,
    path: string,
    params: Record<string, string> = {},
  ): Promise<TmdbResult<T>> {
    try {
      const url = new URL(`${TMDB_BASE}${path}`);
      url.searchParams.set("api_key", apiKey);
      for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
      const response = await fetchImpl(url.toString(), { headers: { accept: "application/json" } });
      if (!response.ok) return { ok: false, transient: response.status >= 500 };
      return { ok: true, value: await response.json() as T };
    } catch {
      return { ok: false, transient: true };
    }
  }

  async function previewFrom(apiKey: string, credit: TmdbCredit): Promise<Record<string, unknown> | null> {
    const tmdbType = credit.media_type === "tv" ? "tv" : "movie";
    const ids = await tmdbJson<{ imdb_id?: string | null }>(apiKey, `/${tmdbType}/${credit.id}/external_ids`);
    if (!ids.ok) return null;
    const imdbId = ids.value.imdb_id;
    if (typeof imdbId !== "string" || !imdbId.startsWith("tt")) return null;
    const name = credit.title ?? credit.name ?? "";
    if (name.length === 0) return null;
    const year = Number.parseInt((credit.release_date ?? credit.first_air_date ?? "").slice(0, 4), 10);
    // MediaPreview spells series "series", never TMDB's "tv".
    const wire = tmdbType === "tv" ? "series" : "movie";
    return {
      id: imdbId,
      type: wire,
      rawType: wire,
      name,
      posterUrl: imageUrl(credit.poster_path, "w342"),
      backgroundUrl: imageUrl(credit.backdrop_path, "w780"),
      releaseYear: Number.isFinite(year) ? year : null,
      providerIds: [],
    };
  }

  return async (req: Request): Promise<Response> => {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

    const user = await requireUser(req);
    if (!user) return json({ error: "unauthorized" }, 401);

    let body: { personId?: unknown };
    try {
      body = await req.json();
    } catch {
      return json({ error: "invalid_body" }, 400);
    }
    const personId = typeof body.personId === "string" ? body.personId : "";
    if (!/^[0-9]{1,12}$/.test(personId)) return json({ error: "invalid_request" }, 400);

    const tmdbKey = await loadTmdbKey(user.id);
    if (tmdbKey === "") return json({ error: "tmdb_key_missing" }, 404);

    const details = await tmdbJson<TmdbPersonDetails>(tmdbKey, `/person/${personId}`, {
      append_to_response: "combined_credits",
    });
    if (!details.ok) {
      return details.transient
        ? json({ error: "person_unavailable" }, 502)
        : json({ error: "person_not_found" }, 404);
    }

    const ranked = rankCredits(details.value);
    const credits = (await Promise.all(ranked.map((credit) => previewFrom(tmdbKey, credit))))
      .filter((preview) => preview !== null);

    return json({
      personId,
      name: details.value.name ?? "",
      profileUrl: imageUrl(details.value.profile_path, "w342"),
      knownFor: details.value.known_for_department ?? null,
      credits,
    });
  };
}

if (import.meta.main) {
  const crypto = createProviderConfigCrypto({
    activeKeyId: Deno.env.get("PROVIDER_CONFIG_ACTIVE_KEY_ID") ?? "",
    encodedKeys: Deno.env.get("PROVIDER_CONFIG_KEYRING") ?? "",
    legacyEncodedKey: Deno.env.get("PROVIDER_CONFIG_KEY"),
  });
  Deno.serve(createResolvePersonCreditsHandler({
    supabaseUrl: Deno.env.get("SUPABASE_URL")!,
    anonKey: Deno.env.get("SUPABASE_ANON_KEY")!,
    serviceRole: Deno.env.get("SERVICE_ROLE_JWT") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    envTmdbKey: Deno.env.get("TMDB_API_KEY") ?? "",
    decrypt: (userId, providerId, blob) => crypto.decrypt(userId, providerId, blob),
  }));
}
