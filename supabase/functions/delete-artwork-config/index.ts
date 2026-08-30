type FetchImpl = (input: string | URL | Request, init?: RequestInit) => Promise<Response>;

const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
const ID_PATTERN = /^[a-z][a-z0-9_-]{0,63}$/;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

export function createDeleteArtworkConfigHandler({
  supabaseUrl,
  anonKey,
  serviceRole,
  fetchImpl = fetch,
}: {
  supabaseUrl: string;
  anonKey: string;
  serviceRole: string;
  fetchImpl?: FetchImpl;
}): (req: Request) => Promise<Response> {
  async function requireUser(req: Request): Promise<{ id: string } | null> {
    const authorization = req.headers.get("Authorization");
    if (!authorization) return null;
    const response = await fetchImpl(`${supabaseUrl}/auth/v1/user`, {
      headers: { apikey: anonKey, Authorization: authorization },
    });
    if (!response.ok) return null;
    const user = await response.json();
    return typeof user?.id === "string" ? user : null;
  }

  async function catalogContains(provider: string): Promise<boolean> {
    const query = new URLSearchParams({ select: "id", id: `eq.${provider}`, limit: "1" });
    const response = await fetchImpl(`${supabaseUrl}/rest/v1/artwork_providers?${query}`, {
      headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}` },
    });
    if (!response.ok) throw new Error("catalog_lookup_failed");
    const rows: unknown = await response.json();
    if (!Array.isArray(rows)) throw new Error("catalog_invalid");
    return rows.some((row) => typeof row === "object" && row !== null && !Array.isArray(row) &&
      (row as Record<string, unknown>).id === provider);
  }

  return async (req: Request): Promise<Response> => {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    const user = await requireUser(req);
    if (!user) return json({ error: "unauthorized" }, 401);
    const parsed: unknown = await req.json().catch(() => ({}));
    const body = typeof parsed === "object" && parsed !== null
      ? parsed as Record<string, unknown>
      : {};
    const hasProvider = Object.prototype.hasOwnProperty.call(body, "provider");
    const all = body.all === true;
    if ((hasProvider && all) || (!hasProvider && !all)) {
      return json({ error: "unsupported_provider" }, 400);
    }

    let provider: string | null = null;
    if (hasProvider) {
      provider = typeof body.provider === "string" ? body.provider.trim().toLowerCase() : null;
      if (provider === null || !ID_PATTERN.test(provider)) {
        return json({ error: "unsupported_provider" }, 400);
      }
      try {
        if (!await catalogContains(provider)) return json({ error: "unsupported_provider" }, 400);
      } catch (error) {
        console.error("artwork provider catalog lookup failed", error instanceof Error ? error.name : "unknown");
        return json({ error: "delete_failed" }, 500);
      }
    }

    const providerFilter = all ? "like.artwork.*" : `eq.${encodeURIComponent(`artwork.${provider}`)}`;
    const response = await fetchImpl(
      `${supabaseUrl}/rest/v1/provider_configs?user_id=eq.${user.id}&provider_id=${providerFilter}`,
      {
        method: "DELETE",
        headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}` },
      },
    );
    if (!response.ok) return json({ error: "delete_failed" }, 500);
    return json(all ? { ok: true, all: true } : { ok: true, provider });
  };
}

if (import.meta.main) {
  Deno.serve(createDeleteArtworkConfigHandler({
    supabaseUrl: Deno.env.get("SUPABASE_URL")!,
    anonKey: Deno.env.get("SUPABASE_ANON_KEY")!,
    serviceRole: Deno.env.get("SERVICE_ROLE_JWT") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  }));
}
