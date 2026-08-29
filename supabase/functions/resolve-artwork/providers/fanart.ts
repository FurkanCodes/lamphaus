import {
  emptyArtworkLists,
  objectRecord,
  requestJson,
  type ArtworkLists,
  type ArtworkMediaType,
  type ArtworkProviderAdapter,
  type ProviderResolveInput,
  type ProviderResolution,
} from "../provider.ts";

const FANART_API_BASE = "https://webservice.fanart.tv/v3.2";
const FANART_IMAGE_HOSTS = ["assets.fanart.tv"] as const;

type ArtworkAsset = { provider: "fanart"; reference: string };

function fanartUrl(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const url = value.trim();
  try {
    const parsed = new URL(url);
    return parsed.protocol === "https:" && parsed.hostname !== "" ? url : null;
  } catch {
    return null;
  }
}

function addUnique(target: ArtworkAsset[], asset: ArtworkAsset): void {
  if (!target.some((existing) => existing.provider === asset.provider && existing.reference === asset.reference)) {
    target.push(asset);
  }
}

export function normalizeFanartPayload(payload: unknown, mediaType: ArtworkMediaType): ArtworkLists {
  const result = emptyArtworkLists();
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
  addUrls(fields.poster, result.posters as ArtworkAsset[]);
  addUrls(fields.backdrop, result.backdrops as ArtworkAsset[]);
  addUrls(fields.logo, result.logos as ArtworkAsset[]);
  return result;
}

function numericSuffix(mediaKey: string): string | null {
  const suffix = mediaKey.slice(mediaKey.lastIndexOf(":") + 1);
  return /^\d+$/.test(suffix) ? suffix : null;
}

function imdbSuffix(mediaKey: string): string | null {
  const suffix = mediaKey.slice(mediaKey.lastIndexOf(":") + 1);
  return /^tt\d+$/i.test(suffix) ? suffix : null;
}

export const fanartAdapter: ArtworkProviderAdapter = {
  id: "fanart",
  after: ["tmdb"],
  allowedImageHosts: FANART_IMAGE_HOSTS,
  requestedIdentifiers: (request) => request.mediaType === "movie" ? ["tmdb_movie_id"] : request.mediaType === "series" ? ["tvdb_id"] : [],
  async resolve({ request, apiKey, identifiers }: ProviderResolveInput): Promise<ProviderResolution> {
    let externalId: string | null = null;
    if (request.mediaType === "movie") {
      externalId = identifiers.tmdb_movie_id ?? numericSuffix(request.mediaKey) ?? imdbSuffix(request.mediaKey);
    } else if (request.mediaType === "series") {
      externalId = identifiers.tvdb_id ?? null;
    }
    if (!externalId) return { provider: "fanart", status: "missing_external_id", ...emptyArtworkLists(), identifiers: {} };

    const endpoint = request.mediaType === "movie" ? "movies" : "tv";
    const fanart = await requestJson(`${FANART_API_BASE}/${endpoint}/${encodeURIComponent(externalId)}`, {
      headers: { "api-key": apiKey, accept: "application/json" },
    });
    if (!fanart.response) return { provider: "fanart", status: "lookup_failed", ...emptyArtworkLists(), identifiers: {} };
    if (fanart.response.status === 401 || fanart.response.status === 403) {
      return { provider: "fanart", status: "invalid_key", ...emptyArtworkLists(), identifiers: {} };
    }
    if (fanart.response.status === 404) return { provider: "fanart", status: "no_match", ...emptyArtworkLists(), identifiers: {} };
    if (!fanart.response.ok || fanart.payload === null) {
      return { provider: "fanart", status: "lookup_failed", ...emptyArtworkLists(), identifiers: {} };
    }
    const artwork = normalizeFanartPayload(fanart.payload, request.mediaType);
    return {
      provider: "fanart",
      status: artwork.posters.length || artwork.backdrops.length || artwork.logos.length ? "success" : "no_match",
      ...artwork,
      identifiers: {},
    };
  },
};
