import { createProviderConfigCrypto } from "../_shared/provider_config_crypto.ts";
import {
  DISPLAY_NAME_BY_SOURCE,
  SCALE_BY_SOURCE,
  fetchMdbListRatings,
  type MdbListRating,
} from "../_shared/mdblist.ts";

// resolve-detail-enrichment — aggregates provider-neutral detail enrichment.
//
// Authenticated (verify_jwt=true). Sources:
//   • TMDB: credits, facts, similar titles, IMDb-id resolution, and the TMDB
//     rating. The credential is the caller's stored artwork.tmdb key
//     (provider_configs) — the same key the artwork system uses — falling
//     back to TMDB_API_KEY for deployments that ship a server-side key.
//   • MDBList (caller's stored integration credential): aggregate rating
//     sources.
//
// One integration therefore powers artwork, cast, and ratings together: any
// stored key enables the whole enrichment, and a caller with no keys simply
// gets an empty 200 — never an error (SHR-PROD-04).
//
// Sources degrade independently per the enrichment contract: one unavailable
// source never fails the whole response. A 502 is returned only when nothing
// usable was produced AND at least one failure may be momentary, so the
// client keeps retrying instead of caching a permanently empty result.

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const TMDB_ENV_API_KEY = Deno.env.get("TMDB_API_KEY") ?? "";
const TMDB_BASE = "https://api.themoviedb.org/3";
const TMDB_IMAGE = "https://image.tmdb.org/t/p";

const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

const SIMILAR_LIMIT = 8;
const CAST_LIMIT = 12;
const CREW_LIMIT = 6;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

async function requireUser(
  req: Request,
): Promise<{ id: string } | null> {
  const authorization = req.headers.get("Authorization");
  if (!authorization) return null;
  const res = await fetch(`${SB_URL}/auth/v1/user`, {
    headers: { apikey: ANON_KEY, Authorization: authorization },
  });
  if (!res.ok) return null;
  const user = await res.json();
  return typeof user?.id === "string" ? user : null;
}

const providerConfigCrypto = createProviderConfigCrypto({
  activeKeyId: Deno.env.get("PROVIDER_CONFIG_ACTIVE_KEY_ID") ?? "",
  encodedKeys: Deno.env.get("PROVIDER_CONFIG_KEYRING") ?? "",
  legacyEncodedKey: Deno.env.get("PROVIDER_CONFIG_KEY"),
});

type EnrichmentBody = {
  mediaKey?: unknown;
  type?: unknown;
  id?: unknown;
  name?: unknown;
  releaseYear?: unknown;
};

type TmdbPerson = {
  id?: number | null;
  name?: string | null;
  character?: string | null;
  job?: string | null;
  profile_path?: string | null;
};

type TmdbSummary = {
  id?: number | null;
  imdb_id?: string | null;
  title?: string | null;
  name?: string | null;
  poster_path?: string | null;
  backdrop_path?: string | null;
  release_date?: string | null;
  first_air_date?: string | null;
  status?: string | null;
  original_language?: string | null;
  budget?: number | null;
  revenue?: number | null;
  vote_average?: number | null;
  vote_count?: number | null;
  credits?: {
    cast?: TmdbPerson[] | null;
    crew?: TmdbPerson[] | null;
  } | null;
  recommendations?: {
    results?: TmdbSummary[] | null;
  } | null;
};

type TmdbResult<T> =
  | { ok: true; value: T }
  | { ok: false; transient: boolean };

async function tmdbJson<T>(
  apiKey: string,
  path: string,
  params: Record<string, string> = {},
): Promise<TmdbResult<T>> {
  if (apiKey === "") return { ok: false, transient: false };
  try {
    const url = new URL(`${TMDB_BASE}${path}`);
    url.searchParams.set("api_key", apiKey);
    for (const [key, value] of Object.entries(params)) {
      url.searchParams.set(key, value);
    }
    const response = await fetch(url.toString(), {
      headers: { accept: "application/json" },
    });
    if (!response.ok) return { ok: false, transient: response.status >= 500 };
    return { ok: true, value: await response.json() as T };
  } catch {
    return { ok: false, transient: true };
  }
}

function isWireType(type: string): type is "movie" | "series" {
  return type === "movie" || type === "series";
}

/** TMDB spells series "tv"; the client contract spells it "series". */
function toTmdbType(type: "movie" | "series"): "movie" | "tv" {
  return type === "series" ? "tv" : "movie";
}

type IdResolution = { tmdbId: number | null; transient: boolean };

async function findTmdbId(
  apiKey: string,
  imdbId: string,
  type: "movie" | "tv",
): Promise<IdResolution> {
  const found = await tmdbJson<{ movie_results?: TmdbSummary[]; tv_results?: TmdbSummary[] }>(
    apiKey,
    `/find/${encodeURIComponent(imdbId)}`,
    { external_source: "imdb_id" },
  );
  if (!found.ok) return { tmdbId: null, transient: found.transient };
  const bucket = type === "movie" ? found.value.movie_results : found.value.tv_results;
  const match = (bucket ?? []).find((entry) => typeof entry.id === "number");
  return { tmdbId: match?.id ?? null, transient: false };
}

async function searchTmdbId(
  apiKey: string,
  name: string,
  type: "movie" | "tv",
  releaseYear: number | null,
): Promise<IdResolution> {
  const params: Record<string, string> = { query: name };
  if (releaseYear !== null) {
    params[type === "movie" ? "year" : "first_air_date_year"] = String(releaseYear);
  }
  const searched = await tmdbJson<{ results?: TmdbSummary[] }>(apiKey, `/search/${type}`, params);
  if (!searched.ok) return { tmdbId: null, transient: searched.transient };
  const match = (searched.value.results ?? []).find((entry) => typeof entry.id === "number");
  return { tmdbId: match?.id ?? null, transient: false };
}

function profileUrl(path: string | null | undefined, width: "w185" | "w342" | "w780"): string | null {
  return typeof path === "string" && path.startsWith("/")
    ? `${TMDB_IMAGE}/${width}${path}`
    : null;
}

function crewRole(job: string | null | undefined): string | null {
  if (typeof job !== "string") return null;
  return job === "Director" || job === "Screenplay" || job === "Writer" ? job : null;
}

function creditsFrom(details: TmdbSummary): { cast: unknown[]; crew: unknown[] } {
  const cast = (details.credits?.cast ?? [])
    .filter((person) => typeof person.name === "string" && person.name.length > 0)
    .slice(0, CAST_LIMIT)
    .map((person) => ({
      personId: typeof person.id === "number" ? String(person.id) : null,
      name: person.name,
      role: typeof person.character === "string" && person.character.length > 0
        ? person.character
        : null,
      profileUrl: profileUrl(person.profile_path, "w185"),
    }));

  const crew: unknown[] = [];
  const seen = new Set<string>();
  for (const person of details.credits?.crew ?? []) {
    const role = crewRole(person.job);
    if (role === null) continue;
    const name = typeof person.name === "string" ? person.name : "";
    if (name.length === 0) continue;
    const identity = `${name}|${role}`;
    if (seen.has(identity)) continue;
    seen.add(identity);
    crew.push({
      personId: typeof person.id === "number" ? String(person.id) : null,
      name,
      role,
      profileUrl: profileUrl(person.profile_path, "w185"),
    });
    if (crew.length >= CREW_LIMIT) break;
  }

  return { cast, crew };
}

function factsFrom(details: TmdbSummary): Record<string, unknown> {
  const facts: Record<string, unknown> = {
    status: typeof details.status === "string" ? details.status : null,
    originalLanguage: typeof details.original_language === "string"
      ? details.original_language
      : null,
  };
  if (typeof details.budget === "number" && details.budget > 0) facts.budgetUsd = details.budget;
  if (typeof details.revenue === "number" && details.revenue > 0) facts.revenueUsd = details.revenue;
  return facts;
}

async function similarFrom(
  apiKey: string,
  details: TmdbSummary,
  tmdbType: "movie" | "tv",
): Promise<Array<Record<string, unknown>>> {
  // Similar previews travel to the client as MediaPreview, whose MediaType
  // contract spells series "series" — never TMDB's "tv".
  const wire = tmdbType === "tv" ? "series" : "movie";
  const candidates = (details.recommendations?.results ?? [])
    .filter((entry) => typeof entry.id === "number")
    .slice(0, SIMILAR_LIMIT);

  const resolved = await Promise.all(
    candidates.map(async (entry): Promise<Record<string, unknown> | null> => {
      // Recommendations omit IMDb ids; resolve each through its details payload.
      const detailed = await tmdbJson<TmdbSummary>(apiKey, `/${tmdbType}/${entry.id}`);
      if (!detailed.ok) return null;
      const summary = detailed.value;
      if (typeof summary.imdb_id !== "string" || !summary.imdb_id.startsWith("tt")) return null;
      const name = summary.title ?? summary.name ?? "";
      if (name.length === 0) return null;
      const year = Number.parseInt((summary.release_date ?? summary.first_air_date ?? "").slice(0, 4), 10);
      return {
        id: summary.imdb_id,
        type: wire,
        rawType: wire,
        name,
        posterUrl: profileUrl(summary.poster_path, "w342"),
        backgroundUrl: profileUrl(summary.backdrop_path, "w780"),
        releaseYear: Number.isFinite(year) ? year : null,
        providerIds: [],
      };
    }),
  );

  return resolved.filter((item) => item !== null);
}

type Credential = { apiKey: string; enabledSources: string[] } | null;

async function loadMdbListCredential(userId: string): Promise<Credential> {
  try {
    const response = await fetch(
      `${SB_URL}/rest/v1/integration_credentials?user_id=eq.${userId}&integration=eq.mdblist`,
      { headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` } },
    );
    if (!response.ok) return null;
    const rows = await response.json() as Array<{
      encrypted_credential?: unknown;
      enabled_sources?: unknown;
    }>;
    const row = rows[0];
    if (!row || typeof row.encrypted_credential !== "string" || row.encrypted_credential === "") {
      return null;
    }
    const decrypted = await providerConfigCrypto.decrypt(
      userId,
      "integration.mdblist",
      row.encrypted_credential,
    );
    const config = decrypted.config;
    if (!config || typeof config !== "object" || !("apiKey" in config)) return null;
    const apiKey = config.apiKey;
    if (typeof apiKey !== "string" || apiKey.length === 0) return null;
    const enabledSources = Array.isArray(row.enabled_sources) &&
        row.enabled_sources.every((entry) => typeof entry === "string")
      ? row.enabled_sources as string[]
      : Object.keys(SCALE_BY_SOURCE);
    return { apiKey, enabledSources };
  } catch {
    // Missing, undecryptable, or unreachable integration: ratings degrade to none.
    return null;
  }
}

/**
 * The caller's own artwork.tmdb key — the same credential the artwork system
 * stores — so one TMDB key powers artwork, cast, facts, and ratings. Falls
 * back to the deployment's env key when the caller has none stored.
 */
async function loadTmdbKey(userId: string): Promise<string> {
  try {
    const response = await fetch(
      `${SB_URL}/rest/v1/provider_configs?user_id=eq.${userId}&provider_id=eq.artwork.tmdb`,
      { headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` } },
    );
    if (!response.ok) return TMDB_ENV_API_KEY;
    const rows = await response.json() as Array<{ encrypted_config?: unknown }>;
    const blob = rows[0]?.encrypted_config;
    if (typeof blob !== "string" || blob.length === 0) return TMDB_ENV_API_KEY;
    const decrypted = await providerConfigCrypto.decrypt(userId, "artwork.tmdb", blob);
    const config = decrypted.config;
    if (config && typeof config === "object" && "api_key" in config) {
      const apiKey = config.api_key;
      if (typeof apiKey === "string" && apiKey.length > 0) return apiKey;
    }
    return TMDB_ENV_API_KEY;
  } catch {
    return TMDB_ENV_API_KEY;
  }
}

function ratingsFrom(
  ratings: MdbListRating[] | null,
  enabledSources: string[],
): Array<Record<string, unknown>> {
  if (ratings === null) return [];
  const enabled = new Set(enabledSources);
  const result: Array<Record<string, unknown>> = [];
  const seen = new Set<string>();
  for (const rating of ratings) {
    const source = typeof rating.source === "string" ? rating.source : "";
    const scale = SCALE_BY_SOURCE[source];
    if (scale === undefined || !enabled.has(source) || seen.has(source)) continue;
    const value = typeof rating.value === "number" && Number.isFinite(rating.value)
      ? rating.value
      : null;
    if (value === null) continue;

    // Some percentage sources arrive above their native scale; recover from
    // MDBList's 0-100 `score` before dropping the entry.
    const normalized = value > scale
      ? (typeof rating.score === "number" && Number.isFinite(rating.score) && rating.score >= 0 &&
            rating.score <= 100
        ? (rating.score / 100) * scale
        : null)
      : value;
    if (normalized === null) continue;

    seen.add(source);
    result.push({
      sourceId: source,
      displayName: DISPLAY_NAME_BY_SOURCE[source] ?? source,
      value: normalized,
      scale,
      voteCount: typeof rating.votes === "number" && Number.isFinite(rating.votes)
        ? Math.round(rating.votes)
        : null,
    });
  }
  return result;
}

/** TMDB's own score fills the "tmdb" slot when MDBList did not supply one. */
function tmdbRatingFrom(details: TmdbSummary): Record<string, unknown> | null {
  const value = typeof details.vote_average === "number" && Number.isFinite(details.vote_average) &&
      details.vote_average > 0
    ? details.vote_average
    : null;
  if (value === null) return null;
  return {
    sourceId: "tmdb",
    displayName: DISPLAY_NAME_BY_SOURCE["tmdb"] ?? "tmdb",
    value,
    scale: 10,
    voteCount: typeof details.vote_count === "number" && Number.isFinite(details.vote_count)
      ? Math.round(details.vote_count)
      : null,
  };
}

// ─────────────────────────────── handler ───────────────────────────────

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  let body: EnrichmentBody;
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_body" }, 400);
  }

  const mediaKey = typeof body.mediaKey === "string" ? body.mediaKey : "";
  const type = typeof body.type === "string" ? body.type : "";
  const id = typeof body.id === "string" ? body.id : "";
  if (mediaKey.length === 0 || !isWireType(type) || id.length === 0) {
    return json({ error: "invalid_request" }, 400);
  }
  const name = typeof body.name === "string" ? body.name : "";
  const releaseYear = typeof body.releaseYear === "number" ? Math.trunc(body.releaseYear) : null;
  const tmdbType = toTmdbType(type);

  const tmdbKey = await loadTmdbKey(user.id);

  // Identifier resolution: tmdb: ids carry their own id; tt ids go through
  // /find; otherwise fall back to a name search.
  let imdbId: string | null = null;
  let resolution: IdResolution = { tmdbId: null, transient: false };
  if (id.startsWith("tt")) {
    imdbId = id;
    if (tmdbKey !== "") resolution = await findTmdbId(tmdbKey, imdbId, tmdbType);
  } else if (id.startsWith("tmdb:")) {
    const numeric = Number.parseInt(id.split(":").pop() ?? "", 10);
    if (Number.isFinite(numeric)) resolution = { tmdbId: numeric, transient: false };
  } else if (name.length > 0 && tmdbKey !== "") {
    resolution = await searchTmdbId(tmdbKey, name, tmdbType, releaseYear);
  }

  // Details and ratings resolve concurrently; either may come up empty.
  const detailsTask = resolution.tmdbId !== null
    ? tmdbJson<TmdbSummary>(tmdbKey, `/${tmdbType}/${resolution.tmdbId}`, {
      append_to_response: "credits,recommendations",
    })
    : null;

  const credential = await loadMdbListCredential(user.id);
  const ratingsTask = credential !== null && imdbId !== null
    ? fetchMdbListRatings(credential.apiKey, imdbId, tmdbType === "movie" ? "movie" : "show")
    : Promise.resolve(null);

  const [details, ratings] = await Promise.all([detailsTask, ratingsTask]);

  const mdblistRatings = ratingsFrom(ratings, credential?.enabledSources ?? []);
  // The source toggles under MDBList govern every rating slot, including the
  // TMDB score resolved from the caller's own artwork.tmdb key.
  const enabledSources = credential?.enabledSources ?? Object.keys(SCALE_BY_SOURCE);
  const ratingsOut = mdblistRatings.some((rating) => rating.sourceId === "tmdb")
    ? mdblistRatings
    : details?.ok && enabledSources.includes("tmdb")
    ? [tmdbRatingFrom(details.value), ...mdblistRatings].filter((entry) => entry !== null)
    : mdblistRatings;

  const enrichment: Record<string, unknown> = {
    mediaKey,
    cast: [],
    crew: [],
    similar: [],
    ratings: ratingsOut,
    facts: null,
    fetchedAtEpochMillis: Date.now(),
  };

  if (details?.ok) {
    const credits = creditsFrom(details.value);
    enrichment.cast = credits.cast;
    enrichment.crew = credits.crew;
    enrichment.facts = factsFrom(details.value);
    enrichment.similar = await similarFrom(tmdbKey, details.value, tmdbType);
  }

  const hasContent = (enrichment.cast as unknown[]).length > 0 ||
    (enrichment.similar as unknown[]).length > 0 ||
    enrichment.facts !== null ||
    (enrichment.ratings as unknown[]).length > 0;

  const detailsFailedTransiently = details !== null && !details.ok && details.transient;
  const ratingsFailedTransiently = credential !== null && ratings === null && imdbId !== null;
  const nothingReliable = !hasContent && (detailsFailedTransiently || ratingsFailedTransiently ||
    resolution.transient);

  if (nothingReliable) {
    // A momentary failure must not be cached as "no data"; the client will
    // retry on the next detail open instead. A caller with no keys at all
    // lands here with every transient flag false and gets an empty 200.
    return json({ error: "enrichment_unavailable" }, 502);
  }
  return json(enrichment);
});
