const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ARTWORK_PROVIDER_IDS = {
  tmdb: "artwork.tmdb",
  fanart: "artwork.fanart",
} as const;
const ARTWORK_PROVIDERS = ["tmdb", "fanart"] as const;

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

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const query = new URLSearchParams({
    select: "provider_id",
    user_id: `eq.${user.id}`,
    provider_id: `in.(${Object.values(ARTWORK_PROVIDER_IDS).join(",")})`,
  });
  const response = await fetch(`${SB_URL}/rest/v1/provider_configs?${query}`, {
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
  });
  if (!response.ok) return json({ error: "status_failed" }, 500);
  const rows: unknown = await response.json();
  const configuredIds = Array.isArray(rows)
    ? new Set(rows.flatMap((row) => {
      if (typeof row !== "object" || row === null || Array.isArray(row)) return [];
      const providerId = (row as Record<string, unknown>).provider_id;
      return typeof providerId === "string" ? [providerId] : [];
    }))
    : new Set<string>();

  return json({
    providers: ARTWORK_PROVIDERS.map((provider) => ({
      provider,
      configured: configuredIds.has(ARTWORK_PROVIDER_IDS[provider]),
    })),
  });
});
