// Shared MDBList REST helpers for integration endpoints.
//
// The API key is caller-supplied credential material: it is never logged and
// never echoed back in a response (SHR-PROD-06).

export const MDBLIST_BASE = "https://api.mdblist.com";

/** Native scale of MDBList's `value` per source (value vs. score distinction). */
export const SCALE_BY_SOURCE: Readonly<Record<string, number>> = {
  imdb: 10,
  tmdb: 10,
  metacriticuser: 10,
  myanimelist: 10,
  letterboxd: 5,
  rogerebert: 4,
  trakt: 100,
  tomatoes: 100,
  popcorn: 100,
  metacritic: 100,
};

export const DISPLAY_NAME_BY_SOURCE: Readonly<Record<string, string>> = {
  imdb: "IMDb",
  tmdb: "TMDB",
  trakt: "Trakt",
  tomatoes: "Rotten Tomatoes",
  popcorn: "Rotten Tomatoes (Audience)",
  metacritic: "Metacritic",
  metacriticuser: "Metacritic (Users)",
  letterboxd: "Letterboxd",
  myanimelist: "MyAnimeList",
  rogerebert: "Roger Ebert",
};

/** Spec-recommended sources; also the stored default for a new integration. */
export const RECOMMENDED_SOURCES: readonly string[] = [
  "imdb",
  "tmdb",
  "trakt",
  "tomatoes",
  "popcorn",
  "metacritic",
  "letterboxd",
];

export type MdbListRating = {
  source?: string | null;
  value?: number | null;
  score?: number | null;
  votes?: number | null;
};

export type MdbListTitle = {
  ratings?: MdbListRating[] | null;
};

/** True when the key authenticates against the MDBList account endpoint. */
export async function validateMdbListKey(apiKey: string): Promise<boolean> {
  try {
    const response = await fetch(
      `${MDBLIST_BASE}/user?apikey=${encodeURIComponent(apiKey)}`,
      { headers: { accept: "application/json" } },
    );
    if (!response.ok) return false;
    const body = await response.json();
    return typeof body === "object" && body !== null;
  } catch {
    return false;
  }
}

/**
 * Ratings for an IMDb title. `type` is MDBList's spelling: "movie" or "show".
 * Returns null on any failure — callers must degrade to the other sources.
 */
export async function fetchMdbListRatings(
  apiKey: string,
  imdbId: string,
  type: "movie" | "show",
): Promise<MdbListRating[] | null> {
  try {
    const response = await fetch(
      `${MDBLIST_BASE}/imdb/${type}/${encodeURIComponent(imdbId)}?apikey=${encodeURIComponent(apiKey)}`,
      { headers: { accept: "application/json" } },
    );
    if (!response.ok) return null;
    const body = await response.json() as MdbListTitle;
    return Array.isArray(body.ratings) ? body.ratings : null;
  } catch {
    return null;
  }
}
