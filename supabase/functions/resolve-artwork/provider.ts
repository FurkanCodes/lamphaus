export type ArtworkProviderId = string;
export type ArtworkLookupStatus =
  | "success"
  | "no_match"
  | "missing_external_id"
  | "invalid_key"
  | "lookup_failed";
export type ArtworkMediaType = "movie" | "series" | "unknown";
export type IdentifierKind =
  | "imdb_id"
  | "numeric_id"
  | "tmdb_movie_id"
  | "tmdb_tv_id"
  | "tvdb_id";

export type ArtworkRequest = {
  mediaKey: string;
  name: string;
  releaseYear: number | null;
  mediaType: ArtworkMediaType;
};

export type ArtworkAsset = {
  provider: ArtworkProviderId;
  reference: string;
};

export type ArtworkLists = {
  posters: ArtworkAsset[];
  backdrops: ArtworkAsset[];
  logos: ArtworkAsset[];
};

export type ArtworkProviderResult = ArtworkLists & {
  provider: ArtworkProviderId;
  status: ArtworkLookupStatus;
  displayName?: string;
  sortOrder?: number;
};

export type ProviderResolveInput = {
  request: ArtworkRequest;
  apiKey: string;
  identifiers: Readonly<Partial<Record<IdentifierKind, string>>>;
  requestedIdentifiers: readonly IdentifierKind[];
};

export type ProviderResolution = ArtworkProviderResult & {
  identifiers: Readonly<Partial<Record<IdentifierKind, string>>>;
};

export type ArtworkProviderAdapter = {
  id: ArtworkProviderId;
  after: readonly ArtworkProviderId[];
  allowedImageHosts: readonly string[];
  requestedIdentifiers(request: ArtworkRequest): readonly IdentifierKind[];
  resolve(input: ProviderResolveInput): Promise<ProviderResolution>;
};

export type JsonResponse = {
  response: Response | null;
  payload: unknown | null;
};

export const emptyArtworkLists = (): ArtworkLists => ({ posters: [], backdrops: [], logos: [] });

export function objectRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

export async function requestJson(url: string, init: RequestInit): Promise<JsonResponse> {
  try {
    const response = await fetch(url, init);
    const payload = await response.json().catch(() => null);
    return { response, payload };
  } catch {
    return { response: null, payload: null };
  }
}

function validAbsoluteHttpsReference(reference: string, allowedImageHosts: readonly string[]): boolean {
  try {
    const url = new URL(reference);
    return url.protocol === "https:" && allowedImageHosts.includes(url.hostname);
  } catch {
    return false;
  }
}

export function sanitizeArtworkReference(
  provider: ArtworkProviderAdapter,
  reference: unknown,
): string | null {
  if (typeof reference !== "string") return null;
  const value = reference.trim();
  if (!value || value.length > 2048) return null;
  if (provider.id === "tmdb" && value.startsWith("/")) return value;
  return validAbsoluteHttpsReference(value, provider.allowedImageHosts) ? value : null;
}

export function sanitizeProviderResolution(
  provider: ArtworkProviderAdapter,
  resolution: ProviderResolution,
): ProviderResolution {
  const sanitize = (assets: ArtworkAsset[]) => assets.flatMap((asset) => {
    if (asset.provider !== provider.id) return [];
    const reference = sanitizeArtworkReference(provider, asset.reference);
    return reference ? [{ provider: provider.id, reference }] : [];
  });
  return {
    ...resolution,
    provider: provider.id,
    posters: dedupeArtworkAssets(sanitize(resolution.posters)),
    backdrops: dedupeArtworkAssets(sanitize(resolution.backdrops)),
    logos: dedupeArtworkAssets(sanitize(resolution.logos)),
  };
}

export function dedupeArtworkAssets(assets: ArtworkAsset[]): ArtworkAsset[] {
  const seen = new Set<string>();
  const result: ArtworkAsset[] = [];
  for (const asset of assets) {
    const identity = `${asset.provider}\u0000${asset.reference}`;
    if (seen.has(identity)) continue;
    seen.add(identity);
    result.push(asset);
  }
  return result;
}

export function combineProviderResults(
  results: ArtworkProviderResult[],
  catalog: ReadonlyArray<{ id: ArtworkProviderId; sortOrder: number; displayName?: string }> = [],
): {
  posters: ArtworkAsset[];
  backdrops: ArtworkAsset[];
  logos: ArtworkAsset[];
  provider_results: Array<{
    provider: ArtworkProviderId;
    display_name?: string;
    status: ArtworkLookupStatus;
  }>;
} {
  const order = new Map(catalog.map((entry) => [entry.id, entry]));
  const legacyOrder = new Map<ArtworkProviderId, number>([["tmdb", 0], ["fanart", 1]]);
  const ordered = [...results].sort((left, right) => {
    const leftCatalog = order.get(left.provider);
    const rightCatalog = order.get(right.provider);
    const leftSort = leftCatalog?.sortOrder ?? left.sortOrder ?? legacyOrder.get(left.provider) ?? Number.MAX_SAFE_INTEGER;
    const rightSort = rightCatalog?.sortOrder ?? right.sortOrder ?? legacyOrder.get(right.provider) ?? Number.MAX_SAFE_INTEGER;
    return leftSort - rightSort || left.provider.localeCompare(right.provider);
  });
  const providerResults = ordered.map(({ provider, displayName, status }) => ({
    provider,
    ...(displayName ? { display_name: displayName } : {}),
    status,
  }));
  return {
    posters: dedupeArtworkAssets(ordered.flatMap((result) => result.posters)),
    backdrops: dedupeArtworkAssets(ordered.flatMap((result) => result.backdrops)),
    logos: dedupeArtworkAssets(ordered.flatMap((result) => result.logos)),
    provider_results: providerResults,
  };
}

export function seedRequestIdentifiers(
  request: ArtworkRequest,
): Partial<Record<IdentifierKind, string>> {
  const suffix = request.mediaKey.slice(request.mediaKey.lastIndexOf(":") + 1);
  const identifiers: Partial<Record<IdentifierKind, string>> = {};
  if (/^tt\d+$/i.test(suffix)) identifiers.imdb_id = suffix;
  if (/^\d+$/.test(suffix)) identifiers.numeric_id = suffix;
  return identifiers;
}

export function failedProviderResolution(
  provider: ArtworkProviderId,
  status: ArtworkLookupStatus = "lookup_failed",
): ProviderResolution {
  return { provider, status, ...emptyArtworkLists(), identifiers: {} };
}

export { normalizeTmdbImagePayload, normalizeTmdbMatches, normalizeTmdbSearchArtwork } from "./providers/tmdb.ts";
export { normalizeFanartPayload } from "./providers/fanart.ts";
export { createProviderRegistry, executeProviderAdapters } from "./providers/registry.ts";
