/**
 * Build-time configuration shared by server and client code.
 *
 * NEXT_PUBLIC_BASE_PATH mirrors PAGES_BASE_PATH (set in next.config.ts) so
 * client components can build OAuth redirect URLs under the Pages sub-path.
 */
export const BASE_PATH = process.env.NEXT_PUBLIC_BASE_PATH ?? "";

export const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
export const SUPABASE_PUBLISHABLE_KEY =
  process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ?? "";

export const cloudConfigured = Boolean(SUPABASE_URL && SUPABASE_PUBLISHABLE_KEY);

/** Prefixes an absolute in-app path with the Pages sub-path when deployed. */
export function withBasePath(path: string): string {
  return `${BASE_PATH}${path}`;
}
