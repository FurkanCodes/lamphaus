export type ArtworkProvider = "tmdb" | "fanart";
export type ArtworkLookupStatus =
  | "success"
  | "no_match"
  | "missing_external_id"
  | "invalid_key"
  | "lookup_failed";
export type ArtworkMediaType = "movie" | "series" | "unknown";

export type ArtworkAsset = {
  provider: ArtworkProvider;
  reference: string;
};

export type ArtworkLists = {
  posters: ArtworkAsset[];
  backdrops: ArtworkAsset[];
  logos: ArtworkAsset[];
};

export type ArtworkProviderResult = ArtworkLists & {
  provider: ArtworkProvider;
  status: ArtworkLookupStatus;
};

export type TmdbMatch = {
  kind: "movie" | "tv";
  id: number;
  posterReference?: string;
  backdropReference?: string;
};

const emptyLists = (): ArtworkLists => ({ posters: [], backdrops: [], logos: [] });

function objectRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function tmdbPath(value: unknown): string | null {
  return typeof value === "string" && value.startsWith("/") ? value : null;
}

function fanartUrl(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const url = value.trim();
  return url !== "" && url.startsWith("https://") ? url : null;
}

function addUnique(target: ArtworkAsset[], asset: ArtworkAsset): void {
  if (!target.some((existing) => existing.provider === asset.provider && existing.reference === asset.reference)) {
    target.push(asset);
  }
}

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
  addPaths("posters", target.posters);
  addPaths("backdrops", target.backdrops);
  addPaths("logos", target.logos);
}

export function normalizeTmdbImagePayload(payload: unknown): ArtworkLists {
  const result = emptyLists();
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
  const result = emptyLists();
  for (const match of matches) {
    if (match.posterReference) addUnique(result.posters, { provider: "tmdb", reference: match.posterReference });
    if (match.backdropReference) addUnique(result.backdrops, { provider: "tmdb", reference: match.backdropReference });
  }
  return result;
}

export function normalizeFanartPayload(payload: unknown, mediaType: ArtworkMediaType): ArtworkLists {
  const result = emptyLists();
  const root = objectRecord(payload);
  if (!root) return result;
  const fields = mediaType === "movie"
    ? { poster: "movieposter", backdrop: "moviebackground", logo: "hdmovielogo" }
    : mediaType === "series"
    ? { poster: "tvposter", backdrop: "showbackground", logo: "hdtvlogo" }
    : null;
  if (!fields) return result;
  const addUrls = (field: string, list: ArtworkAsset[]) => {
    const values = root[field];
    if (!Array.isArray(values)) return;
    for (const entry of values) {
      const url = fanartUrl(objectRecord(entry)?.url);
      if (url) addUnique(list, { provider: "fanart", reference: url });
    }
  };
  addUrls(fields.poster, result.posters);
  addUrls(fields.backdrop, result.backdrops);
  addUrls(fields.logo, result.logos);
  return result;
}

export function dedupeArtworkAssets(assets: ArtworkAsset[]): ArtworkAsset[] {
  const result: ArtworkAsset[] = [];
  for (const asset of assets) addUnique(result, asset);
  return result;
}

export function combineProviderResults(results: ArtworkProviderResult[]): {
  posters: ArtworkAsset[];
  backdrops: ArtworkAsset[];
  logos: ArtworkAsset[];
  provider_results: Array<{ provider: ArtworkProvider; status: ArtworkLookupStatus }>;
} {
  const ordered = (["tmdb", "fanart"] as ArtworkProvider[]).flatMap((provider) =>
    results.filter((result) => result.provider === provider));
  return {
    posters: dedupeArtworkAssets(ordered.flatMap((result) => result.posters)),
    backdrops: dedupeArtworkAssets(ordered.flatMap((result) => result.backdrops)),
    logos: dedupeArtworkAssets(ordered.flatMap((result) => result.logos)),
    provider_results: ordered.map(({ provider, status }) => ({ provider, status })),
  };
}
