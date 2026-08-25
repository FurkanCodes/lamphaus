// revoke-device — unpairs one of the caller's TVs (plan D3/F6).
//
// Self-contained by choice: the platform's remote bundler only sees this
// function's own directory, so helpers live inline instead of /_shared.
//
// Authenticated (verify_jwt=true). All semantics live in the
// revoke_device RPC, called WITH THE CALLER'S OWN JWT so the RPC's
// auth.uid() ownership guard applies; security definer lets it reach the
// paired GoTrue session (refresh tokens + session row) that plain RLS
// would never expose. Result: the TV's next refresh fails → QR screen.

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;

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

  const authorization = req.headers.get("Authorization")!;
  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const deviceId = typeof body.device_id === "string" && body.device_id.trim()
    ? body.device_id.trim()
    : "";
  if (!deviceId) return json({ error: "missing_device_id" }, 400);

  const revoked = await fetch(`${SB_URL}/rest/v1/rpc/revoke_device`, {
    method: "POST",
    headers: {
      apikey: ANON_KEY,
      Authorization: authorization,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ p_device_id: deviceId }),
  });

  if (!revoked.ok) {
    return json(
      { error: await revoked.text().then((t) => t.includes("DEVICE_NOT_FOUND") ? "device_not_found" : "revoke_failed").catch(() => "revoke_failed") },
      revoked.status === 404 || revoked.status === 400 ? 404 : 500,
    );
  }

  return json({ ok: true });
});
