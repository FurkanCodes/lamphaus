// delete-provider-config — removes one of the caller's provider configs.
//
// Self-contained by choice: the platform's remote bundler only sees this
// function's own directory, so helpers live inline instead of /_shared.
//
// Authenticated (verify_jwt=true); delete is scoped to the caller's user_id
// and idempotent — deleting an unknown provider still succeeds.

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

async function requireUser(
  req: Request,
): Promise<{ id: string } | null> {
  const authorization = req.headers.get("Authorization");
  if (!authorization) return null;
  const res = await fetch(`${SB_URL}/auth/v1/user`, {
    headers: { apikey: ANON_KEY, Authorization: authorization },
  });
  if (!res.ok) return null;
  const user = await res.json();
  return typeof user?.id === "string" ? user : null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const providerId = typeof body.provider_id === "string" && body.provider_id.trim()
    ? body.provider_id.trim()
    : "";
  if (!providerId) return json({ error: "missing_provider_id" }, 400);

  const removed = await fetch(
    `${SB_URL}/rest/v1/provider_configs?user_id=eq.${user.id}&provider_id=eq.${encodeURIComponent(providerId)}`,
    { method: "DELETE", headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` } },
  );
  if (!removed.ok) return json({ error: "delete_failed" }, 500);

  return json({ ok: true });
});
