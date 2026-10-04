// Shared Seekr (seekr.tv) REST helpers: seek-preview sprite lookups.
//
// The API key is caller-supplied credential material: it is sent only to
// api.seekr.tv, never logged, and never echoed back (SHR-PROD-06). The signed
// WebVTT URL and the sprite sheets it names need no key, so devices fetch
// those directly and the key itself never leaves the server.

export const SEEKR_BASE = "https://api.seekr.tv";

/** True/false when Seekr answered; null when the check could not run. */
export async function validateSeekrKey(apiKey: string): Promise<boolean | null> {
  try {
    const response = await fetch(`${SEEKR_BASE}/v1/keys/validate`, {
      headers: { accept: "application/json", "X-API-Key": apiKey },
      redirect: "error",
    });
    if (response.status >= 500) return null;
    if (!response.ok) return false;
    const body = await response.json() as { valid?: unknown };
    return body?.valid === true;
  } catch {
    return null;
  }
}

/** What to look up: a film by id, or one episode of a series by its show id. */
export type SeekrTitle =
  | { kind: "movie"; imdbId: string | null; tmdbId: number | null }
  | { kind: "episode"; imdbId: string | null; tmdbId: number | null; season: number; episode: number };

/**
 * Which allowance a 429 used up. Seekr counts distinct movies (20) and
 * distinct episodes (70) per key per day separately, plus 5,000 lookups;
 * all reset at midnight UTC. "all" also covers the 2 req/s burst limit.
 */
export type SeekrLimitScope = "movie" | "episode" | "all";

export type SeekrLookup =
  | { status: "found"; vttUrl: string; sourceDurationMs: number; scale: number }
  | { status: "not_found" }
  | { status: "rejected" }
  | { status: "rate_limited"; scope: SeekrLimitScope; retryAfterSeconds: number }
  | { status: "unavailable" };

/** The allowance a title draws on. */
export function limitScopeFor(title: SeekrTitle): "movie" | "episode" {
  return title.kind === "movie" ? "movie" : "episode";
}

/** Seconds from [nowMs] to the next midnight UTC, when Seekr's daily counts reset. */
export function secondsUntilUtcMidnight(nowMs: number): number {
  const now = new Date(nowMs);
  const midnight = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1);
  return Math.max(1, Math.ceil((midnight - nowMs) / 1000));
}

/**
 * Reads a 429: Seekr's `error` names the cap ("distinct movie cap reached",
 * "distinct episode cap reached", "daily quota exceeded") and `Retry-After`
 * gives the seconds until it lifts. A daily cap without a usable header lasts
 * until midnight UTC; an unnamed 429 is the short burst limit.
 */
export function parseSeekrLimit(
  error: unknown,
  retryAfterHeader: string | null,
  nowMs: number,
): { scope: SeekrLimitScope; retryAfterSeconds: number } {
  const message = typeof error === "string" ? error.toLowerCase() : "";
  const scope: SeekrLimitScope | null = message.includes("movie")
    ? "movie"
    : message.includes("episode")
    ? "episode"
    : message.includes("quota")
    ? "all"
    : null;
  const header = retryAfterHeader === null ? NaN : Number.parseInt(retryAfterHeader, 10);
  const daily = secondsUntilUtcMidnight(nowMs);
  if (scope === null) {
    // Burst limit: back off briefly, or as long as Seekr says.
    return { scope: "all", retryAfterSeconds: Number.isFinite(header) && header > 0 ? Math.min(header, daily) : 60 };
  }
  return {
    scope,
    retryAfterSeconds: Number.isFinite(header) && header > 0 ? Math.min(header, daily) : daily,
  };
}

/** One `/sprites` lookup. Every outcome is a value; nothing throws. */
export async function lookupSeekrSprites(
  apiKey: string,
  title: SeekrTitle,
  durationMs: number,
): Promise<SeekrLookup> {
  const url = new URL(`${SEEKR_BASE}/sprites`);
  url.searchParams.set("duration_ms", String(durationMs));
  if (title.kind === "movie") {
    if (title.imdbId !== null) url.searchParams.set("imdb_id", title.imdbId);
    if (title.tmdbId !== null) url.searchParams.set("tmdb_id", String(title.tmdbId));
  } else {
    if (title.tmdbId !== null) url.searchParams.set("show_tmdb_id", String(title.tmdbId));
    if (title.imdbId !== null) url.searchParams.set("show_imdb_id", title.imdbId);
    url.searchParams.set("season", String(title.season));
    url.searchParams.set("episode", String(title.episode));
  }
  try {
    const response = await fetch(url.toString(), {
      headers: { accept: "application/json", "X-API-Key": apiKey },
      redirect: "error",
    });
    if (response.status === 404) return { status: "not_found" };
    if (response.status === 401 || response.status === 403) return { status: "rejected" };
    if (response.status === 429) {
      const body = await response.json().catch(() => null) as { error?: unknown } | null;
      return {
        status: "rate_limited",
        ...parseSeekrLimit(body?.error, response.headers.get("retry-after"), Date.now()),
      };
    }
    if (!response.ok) return response.status >= 500 ? { status: "unavailable" } : { status: "not_found" };
    const body = await response.json() as {
      vtt_url?: unknown;
      scale?: unknown;
      source_duration_ms?: unknown;
    };
    // Only a signed https manifest is passed on; anything else is "no previews".
    if (typeof body.vtt_url !== "string" || !body.vtt_url.startsWith("https://")) {
      return { status: "not_found" };
    }
    return {
      status: "found",
      vttUrl: body.vtt_url,
      scale: typeof body.scale === "number" && Number.isFinite(body.scale) ? body.scale : 1,
      sourceDurationMs: typeof body.source_duration_ms === "number" &&
          Number.isFinite(body.source_duration_ms)
        ? Math.round(body.source_duration_ms)
        : 0,
    };
  } catch {
    return { status: "unavailable" };
  }
}
