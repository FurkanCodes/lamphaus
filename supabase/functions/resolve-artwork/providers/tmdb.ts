import {
  emptyArtworkLists,
  objectRecord,
  requestJson,
  type ArtworkLists,
  type ArtworkMediaType,
  type ArtworkProviderAdapter,
  type ArtworkRequest,
  type ProviderResolveInput,
  type ProviderResolution,
} from "../provider.ts";

const TMDB_API_BASE = "https://api.themoviedb.org/3";

export type TmdbMatch = {
  kind: "movie" | "tv";
  id: number;
  posterReference?: string;
  backdropReference?: string;
};

function tmdbPath(value: unknown): string | null {
  return typeof value === "string" && value.startsWith("/") ? value : null;
}

function addUnique(target: ArtworkAsset[], asset: ArtworkAsset): void {
  if (!target.some((existing) => existing.provider === asset.provider && existing.reference === asset.reference)) {
    target.push(asset);
  }
}

type ArtworkAsset = { provider: "tmdb"; reference: string };

function addTmdbPaths(target: ArtworkLists, payload: unknown): void {
  const root = objectRecord(payload);
  if (!root) return;
  const addPaths = (field: string, list: ArtworkAsset[]) => {
    const values = root[field];
    if (!Array.isArray(values)) return;
    for (const image of values) {
      const path = tmdbPath(objectRecord(image)?.file_path);
      if (path) addUnique(list, { provider: "tmdb", reference: path });
    }
  };
  addPaths("posters", target.posters as ArtworkAsset[]);
  addPaths("backdrops", target.backdrops as ArtworkAsset[]);
  addPaths("logos", target.logos as ArtworkAsset[]);
}

export function normalizeTmdbImagePayload(payload: unknown): ArtworkLists {
  const result = emptyArtworkLists();
  addTmdbPaths(result, payload);
  return result;
}

export function normalizeTmdbMatches(payload: unknown, mediaType: ArtworkMediaType): TmdbMatch[] {
  const root = objectRecord(payload);
  if (!root) return [];
  const matches: TmdbMatch[] = [];
  const seen = new Set<string>();
  const addResults = (value: unknown, kind: "movie" | "tv" | null) => {
    if (!Array.isArray(value)) return;
    for (const entry of value) {
      const item = objectRecord(entry);
      if (!item) continue;
      const resultKind = kind ?? (item.media_type === "movie" || item.media_type === "tv" ? item.media_type : null);
      if (resultKind === null) continue;
      if (mediaType === "movie" && resultKind !== "movie") continue;
      if (mediaType === "series" && resultKind !== "tv") continue;
      const id = typeof item.id === "number" && Number.isInteger(item.id) ? item.id : null;
      if (id === null) continue;
      const identity = `${resultKind}:${id}`;
      if (seen.has(identity)) continue;
      seen.add(identity);
      matches.push({
        kind: resultKind,
        id,
        posterReference: tmdbPath(item.poster_path) ?? undefined,
        backdropReference: tmdbPath(item.backdrop_path) ?? undefined,
      });
    }
  };
  addResults(root.movie_results, "movie");
  addResults(root.tv_results, "tv");
  addResults(root.results, null);
  return matches;
}

export function normalizeTmdbSearchArtwork(matches: TmdbMatch[]): ArtworkLists {
  const result = emptyArtworkLists();
  for (const match of matches) {
    if (match.posterReference) addUnique(result.posters as ArtworkAsset[], { provider: "tmdb", reference: match.posterReference });
    if (match.backdropReference) addUnique(result.backdrops as ArtworkAsset[], { provider: "tmdb", reference: match.backdropReference });
  }
  return result;
}

function imdbSuffix(mediaKey: string): string | null {
  const suffix = mediaKey.slice(mediaKey.lastIndexOf(":") + 1);
  return /^tt\d+$/i.test(suffix) ? suffix : null;
}

function tmdbSearchUrl(request: ArtworkRequest, apiKey: string): string {
  const externalId = imdbSuffix(request.mediaKey);
  if (externalId) {
    const params = new URLSearchParams({ api_key: apiKey, external_source: "imdb_id" });
    return `${TMDB_API_BASE}/find/${encodeURIComponent(externalId)}?${params}`;
  }
  const params = new URLSearchParams({ api_key: apiKey, query: request.name, include_adult: "false" });
  if (request.releaseYear !== null) params.set("year", String(request.releaseYear));
  return `${TMDB_API_BASE}/search/multi?${params}`;
}

function externalId(value: unknown): string | null {
  if (typeof value === "number" && Number.isInteger(value)) return String(value);
  return typeof value === "string" && /^\d+$/.test(value) ? value : null;
}

async function tvdbIdentifier(match: TmdbMatch, apiKey: string): Promise<string | null> {
  const externalIds = await requestJson(
    `${TMDB_API_BASE}/tv/${match.id}/external_ids?${new URLSearchParams({ api_key: apiKey })}`,
    { headers: { accept: "application/json" } },
  );
  if (!externalIds.response?.ok) return null;
  return externalId(objectRecord(externalIds.payload)?.tvdb_id);
}

export const tmdbAdapter: ArtworkProviderAdapter = {
  id: "tmdb",
  after: [],
  allowedImageHosts: [],
  requestedIdentifiers: () => [],
  async resolve({ request, apiKey, requestedIdentifiers }: ProviderResolveInput): Promise<ProviderResolution> {
    const search = await requestJson(tmdbSearchUrl(request, apiKey), { headers: { accept: "application/json" } });
    if (!search.response) return { provider: "tmdb", status: "lookup_failed", ...emptyArtworkLists(), identifiers: {} };
    if (search.response.status === 401) return { provider: "tmdb", status: "invalid_key", ...emptyArtworkLists(), identifiers: {} };
    if (!search.response.ok || search.payload === null) return { provider: "tmdb", status: "lookup_failed", ...emptyArtworkLists(), identifiers: {} };

    const matches = normalizeTmdbMatches(search.payload, request.mediaType);
    if (matches.length === 0) return { provider: "tmdb", status: "no_match", ...emptyArtworkLists(), identifiers: {} };
    const artwork = normalizeTmdbSearchArtwork(matches);
    const identifiers: Record<string, string> = {};
    const movie = matches.find((match) => match.kind === "movie");
    const series = matches.find((match) => match.kind === "tv");
    if (movie) identifiers.tmdb_movie_id = String(movie.id);
    if (series) identifiers.tmdb_tv_id = String(series.id);

    let imageLookupFailed = false;
    let invalidKey = false;
    for (const match of matches.slice(0, 8)) {
      const params = new URLSearchParams({ api_key: apiKey, include_image_language: "en,null" });
      const images = await requestJson(`${TMDB_API_BASE}/${match.kind}/${match.id}/images?${params}`, {
        headers: { accept: "application/json" },
      });
      if (!images.response) imageLookupFailed = true;
      else if (images.response.status === 401) invalidKey = true;
      else if (!images.response.ok || images.payload === null) imageLookupFailed = true;
      else {
        const normalized = normalizeTmdbImagePayload(images.payload);
        artwork.posters.push(...normalized.posters);
        artwork.backdrops.push(...normalized.backdrops);
        artwork.logos.push(...normalized.logos);
      }
    }

    if (requestedIdentifiers.includes("tvdb_id") && series) {
      const tvdbId = await tvdbIdentifier(series, apiKey);
      if (tvdbId) identifiers.tvdb_id = tvdbId;
    }
    const status = invalidKey ? "invalid_key" : imageLookupFailed ? "lookup_failed" : "success";
    return { provider: "tmdb", status, ...artwork, identifiers };
  },
};
