import {
  failedProviderResolution,
  sanitizeProviderResolution,
  type ArtworkProviderAdapter,
  type ArtworkProviderId,
  type ArtworkRequest,
  type IdentifierKind,
  type ProviderResolution,
} from "../provider.ts";

export type ConfiguredArtworkProvider = {
  id: ArtworkProviderId;
  apiKey: string;
  displayName?: string;
  sortOrder?: number;
};

export type ArtworkProviderRegistry = {
  adapters: readonly ArtworkProviderAdapter[];
  byId: ReadonlyMap<ArtworkProviderId, ArtworkProviderAdapter>;
};

const ID_PATTERN = /^[a-z][a-z0-9_-]{0,63}$/;

export function createProviderRegistry(adapters: readonly ArtworkProviderAdapter[]): ArtworkProviderRegistry {
  const byId = new Map<ArtworkProviderId, ArtworkProviderAdapter>();
  for (const adapter of adapters) {
    if (!ID_PATTERN.test(adapter.id)) throw new Error(`invalid_provider_id:${adapter.id}`);
    if (byId.has(adapter.id)) throw new Error(`duplicate_provider_id:${adapter.id}`);
    byId.set(adapter.id, adapter);
  }

  const visiting = new Set<ArtworkProviderId>();
  const visited = new Set<ArtworkProviderId>();
  const visit = (id: ArtworkProviderId): void => {
    if (visited.has(id)) return;
    if (visiting.has(id)) throw new Error(`provider_dependency_cycle:${id}`);
    visiting.add(id);
    const adapter = byId.get(id);
    for (const dependency of adapter?.after ?? []) {
      if (byId.has(dependency)) visit(dependency);
    }
    visiting.delete(id);
    visited.add(id);
  };
  for (const adapter of adapters) visit(adapter.id);
  return { adapters: [...adapters], byId };
}

function configuredEntries(
  configured: ReadonlyArray<ConfiguredArtworkProvider> | ReadonlyMap<ArtworkProviderId, string>,
): ConfiguredArtworkProvider[] {
  return configured instanceof Map
    ? [...configured].map(([id, apiKey]) => ({ id, apiKey }))
    : [...configured] as ConfiguredArtworkProvider[];
}

function dependencyLevels(
  registry: ArtworkProviderRegistry,
  configuredIds: ReadonlySet<ArtworkProviderId>,
): ArtworkProviderId[][] {
  const pending = new Set(configuredIds);
  const levels: ArtworkProviderId[][] = [];
  while (pending.size) {
    const level = [...pending].filter((id) => {
      const adapter = registry.byId.get(id);
      return !adapter || adapter.after.every((dependency) => !pending.has(dependency));
    });
    if (!level.length) throw new Error("provider_dependency_cycle");
    levels.push(level);
    for (const id of level) pending.delete(id);
  }
  return levels;
}

function requestedIdentifiers(
  entries: readonly ConfiguredArtworkProvider[],
  registry: ArtworkProviderRegistry,
  request: ArtworkRequest,
): IdentifierKind[] {
  const identifiers = new Set<IdentifierKind>();
  for (const entry of entries) {
    const adapter = registry.byId.get(entry.id);
    if (!adapter) continue;
    for (const identifier of adapter.requestedIdentifiers(request)) identifiers.add(identifier);
  }
  return [...identifiers];
}

export async function executeProviderAdapters(
  registry: ArtworkProviderRegistry,
  request: ArtworkRequest,
  configured: ReadonlyArray<ConfiguredArtworkProvider> | ReadonlyMap<ArtworkProviderId, string>,
  initialIdentifiers: Readonly<Partial<Record<IdentifierKind, string>>> = {},
): Promise<ProviderResolution[]> {
  const entries = configuredEntries(configured);
  const byId = new Map(entries.map((entry) => [entry.id, entry]));
  const identifiers: Partial<Record<IdentifierKind, string>> = { ...initialIdentifiers };
  const results = new Map<ArtworkProviderId, ProviderResolution>();
  const requested = requestedIdentifiers(entries, registry, request);

  for (const level of dependencyLevels(registry, new Set(byId.keys()))) {
    const settled = await Promise.allSettled(level.map(async (id): Promise<ProviderResolution> => {
      const entry = byId.get(id)!;
      const adapter = registry.byId.get(id);
      if (!adapter) return failedProviderResolution(id);
      const resolution = await adapter.resolve({
        request,
        apiKey: entry.apiKey,
        identifiers: { ...identifiers },
        requestedIdentifiers: requested,
      });
      const sanitized = sanitizeProviderResolution(adapter, resolution);
      return {
        ...sanitized,
        displayName: entry.displayName ?? sanitized.displayName,
        sortOrder: entry.sortOrder ?? sanitized.sortOrder,
      };
    }));
    level.forEach((id, index) => {
      const outcome = settled[index];
      const result = outcome.status === "fulfilled" ? outcome.value : failedProviderResolution(id);
      results.set(id, result);
      if (outcome.status === "fulfilled") {
        Object.assign(identifiers, outcome.value.identifiers);
      }
    });
  }
  return entries.map((entry) => results.get(entry.id) ?? failedProviderResolution(entry.id));
}
