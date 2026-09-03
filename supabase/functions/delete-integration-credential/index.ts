// delete-integration-credential — removes one integration credential row.
//
// Authenticated (verify_jwt=true); deny-all RLS table touched via service role,
// scoped to the caller's user_id.

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

const INTEGRATION_PATTERN = /^[a-z][a-z0-9_-]{0,31}$/;

// ─────────────────────────────── handler ───────────────────────────────

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  let body: { integration?: unknown };
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_body" }, 400);
  }

  const integration = typeof body.integration === "string" ? body.integration : "";
  if (!INTEGRATION_PATTERN.test(integration)) {
    return json({ error: "unsupported_integration" }, 400);
  }

  const deleted = await fetch(
    `${SB_URL}/rest/v1/integration_credentials?user_id=eq.${user.id}&integration=eq.${integration}`,
    {
      method: "DELETE",
      headers: {
        apikey: SERVICE_ROLE,
        Authorization: `Bearer ${SERVICE_ROLE}`,
      },
    },
  );
  if (!deleted.ok) return json({ error: "delete_failed" }, 500);
  return json({ integration, connected: false });
});
