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

export type SeekrLookup =
  | { status: "found"; vttUrl: string; sourceDurationMs: number; scale: number }
  | { status: "not_found" }
  | { status: "rejected" }
  | { status: "rate_limited" }
  | { status: "unavailable" };

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
    if (response.status === 429) return { status: "rate_limited" };
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
