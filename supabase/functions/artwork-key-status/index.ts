import {
  isValidArtworkCatalogRow,
  projectArtworkKeyStatus,
  type ArtworkCatalogRow,
} from "./catalog.ts";

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

async function requireUser(req: Request): Promise<{ id: string } | null> {
  const authorization = req.headers.get("Authorization");
  if (!authorization) return null;
  const response = await fetch(`${SB_URL}/auth/v1/user`, {
    headers: { apikey: ANON_KEY, Authorization: authorization },
  });
  if (!response.ok) return null;
  const user = await response.json();
  return typeof user?.id === "string" ? user : null;
}

async function loadCatalog(): Promise<ArtworkCatalogRow[]> {
  const query = new URLSearchParams({
    select: "id,display_name,purpose,help_text,key_page_url,sort_order,enabled",
    order: "sort_order.asc,id.asc",
  });
  const response = await fetch(`${SB_URL}/rest/v1/artwork_providers?${query}`, {
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
  });
  if (!response.ok) {
    console.error("artwork catalog lookup failed", response.status);
    throw new Error("catalog_lookup_failed");
  }
  const rows: unknown = await response.json();
  if (!Array.isArray(rows) || !rows.every(isValidArtworkCatalogRow)) throw new Error("catalog_invalid");
  if (new Set(rows.map((row) => row.id)).size !== rows.length) throw new Error("catalog_invalid");
  return rows;
}

async function loadConfiguredIds(userId: string): Promise<Set<string>> {
  const query = new URLSearchParams({ select: "provider_id", user_id: `eq.${userId}` });
  const response = await fetch(`${SB_URL}/rest/v1/provider_configs?${query}`, {
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
  });
  if (!response.ok) {
    console.error("artwork status lookup failed", response.status);
    throw new Error("status_lookup_failed");
  }
  const rows: unknown = await response.json();
  if (!Array.isArray(rows)) throw new Error("status_lookup_failed");
  return new Set(rows.flatMap((row) => {
    if (typeof row !== "object" || row === null || Array.isArray(row)) return [];
    const providerId = (row as Record<string, unknown>).provider_id;
    return typeof providerId === "string" && providerId.toLowerCase().startsWith("artwork.") ? [providerId] : [];
  }));
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);
  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const contractVersion = body.contract_version === 2 ? 2 : undefined;
  try {
    const [catalog, configured] = await Promise.all([loadCatalog(), loadConfiguredIds(user.id)]);
    return json(projectArtworkKeyStatus(catalog, configured, contractVersion));
  } catch (error) {
    console.error("artwork catalog/status failed", error instanceof Error ? error.name : "unknown");
    return json({ error: error instanceof Error && error.message === "catalog_invalid" ? "catalog_invalid" : "status_failed" }, 500);
  }
});
