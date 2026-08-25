// delete-account — wipes the caller's account entirely (plan F6).
//
// Self-contained by choice: the platform's remote bundler only sees this
// function's own directory, so helpers live inline instead of /_shared.
//
// Authenticated (verify_jwt=true). Deleting the auth user cascades to every
// owned row (profiles, library_entries, watch_progress, user_settings,
// provider_configs, devices) and removes all GoTrue sessions/refresh tokens.
// The client clears its locally stored session afterwards.

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
): Promise<{ id: string } | { error: string }> {
  const authorization = req.headers.get("Authorization");
  if (!authorization) return { error: "unauthorized" };
  const res = await fetch(`${SB_URL}/auth/v1/user`, {
    headers: { apikey: ANON_KEY, Authorization: authorization },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    // A JWT whose session was revoked elsewhere (global logout, revoke-device,
    // pairing purge) is still cryptographically valid — GoTrue answers 403
    // session_not_found. Surface that distinctly so clients prompt a
    // re-login instead of a generic failure.
    return {
      error:
        body?.error_code === "session_not_found"
          ? "session_expired"
          : "unauthorized",
    };
  }
  const user = await res.json();
  return typeof user?.id === "string" ? user : { error: "unauthorized" };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if ("error" in user) return json({ error: user.error }, 401);

  const deleted = await fetch(
    `${SB_URL}/auth/v1/admin/users/${user.id}`,
    {
      method: "DELETE",
      headers: { apikey: ANON_KEY, Authorization: `Bearer ${SERVICE_ROLE}` },
    },
  );
  // 404 means already gone (double-tap, retry after success): deletion is
  // idempotent — report success so clients never show a false failure.
  if (!deleted.ok && deleted.status !== 404) {
    return json({ error: "delete_failed" }, 500);
  }

  return json({ ok: true });
});
