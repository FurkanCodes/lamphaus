const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ARTWORK_PROVIDER_IDS = {
  tmdb: "artwork.tmdb",
  fanart: "artwork.fanart",
} as const;

type ArtworkProvider = keyof typeof ARTWORK_PROVIDER_IDS;

function isArtworkProvider(value: unknown): value is ArtworkProvider {
  return value === "tmdb" || value === "fanart";
}

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

  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const providerValue = typeof body.provider === "string" ? body.provider.trim().toLowerCase() : "";
  if (!isArtworkProvider(providerValue)) return json({ error: "unsupported_provider" }, 400);
  const providerConfigId = ARTWORK_PROVIDER_IDS[providerValue];
  const response = await fetch(
    `${SB_URL}/rest/v1/provider_configs?user_id=eq.${user.id}&provider_id=eq.${encodeURIComponent(providerConfigId)}`,
    {
      method: "DELETE",
      headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
    },
  );
  if (!response.ok) return json({ error: "delete_failed" }, 500);

  return json({ ok: true, provider: providerValue });
});
