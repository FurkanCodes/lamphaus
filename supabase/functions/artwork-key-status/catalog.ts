export type ArtworkCatalogRow = {
  id: string;
  display_name: string;
  purpose: string;
  help_text: string;
  key_page_url: string;
  sort_order: number;
  enabled: boolean;
};

export type ArtworkCatalogStatus = ArtworkCatalogRow & {
  configured: boolean;
};

const ID_PATTERN = /^[a-z][a-z0-9_-]{0,63}$/;

export function isValidArtworkCatalogRow(value: unknown): value is ArtworkCatalogRow {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return false;
  const row = value as Record<string, unknown>;
  if (typeof row.id !== "string" || !ID_PATTERN.test(row.id)) return false;
  for (const key of ["display_name", "purpose", "help_text", "key_page_url"] as const) {
    if (typeof row[key] !== "string" || row[key].trim() === "" || row[key].length > 500) return false;
  }
  if (typeof row.sort_order !== "number" || !Number.isInteger(row.sort_order)) return false;
  if (typeof row.enabled !== "boolean") return false;
  try {
    const url = new URL(row.key_page_url as string);
    if (url.protocol !== "https:" || !url.hostname) return false;
  } catch {
    return false;
  }
  return true;
}

export function projectArtworkKeyStatus(
  rows: readonly ArtworkCatalogRow[],
  configured: ReadonlySet<string>,
  contractVersion: 2,
): { contract_version: 2; providers: ArtworkCatalogStatus[] };
export function projectArtworkKeyStatus(
  rows: readonly ArtworkCatalogRow[],
  configured: ReadonlySet<string>,
  contractVersion?: number,
): { providers: Array<{ provider: "tmdb" | "fanart"; configured: boolean }> };
export function projectArtworkKeyStatus(
  rows: readonly ArtworkCatalogRow[],
  configured: ReadonlySet<string>,
  contractVersion?: number,
): { contract_version: 2; providers: ArtworkCatalogStatus[] } | { providers: Array<{ provider: "tmdb" | "fanart"; configured: boolean }> } {
  const ordered = [...rows].sort((left, right) => left.sort_order - right.sort_order || left.id.localeCompare(right.id));
  if (contractVersion !== 2) {
    return {
      providers: (["tmdb", "fanart"] as const).map((provider) => ({
        provider,
        configured: configured.has(`artwork.${provider}`),
      })),
    };
  }
  return {
    contract_version: 2,
    providers: ordered
      .filter((row) => row.enabled || configured.has(`artwork.${row.id}`))
      .map((row) => ({ ...row, configured: configured.has(`artwork.${row.id}`) })),
  };
}
