// claim-pairing-session — called by a signed-in phone or web app (plan F2).
//
// Self-contained by choice: the platform's remote bundler only sees this
// function's own directory, so helpers live inline instead of in /_shared.
//
// verify_jwt stays ON: the caller's JWT IS the identity that becomes the
// TV's owner. The claim itself is atomic — the UPDATE only matches rows that
// are still unclaimed, live and unexchanged, so two phones racing one QR
// code produce exactly one winner.

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
// GoTrue's admin API parses the Bearer as a JWT; on new-API-key projects the
// platform-injected SUPABASE_SERVICE_ROLE_KEY is an opaque sb_secret that
// PostgREST happily takes but GoTrue rejects ("invalid number of segments").
// Prefer an explicitly-set legacy JWT when present.
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

// Browsers preflight every call from the /pair page; without these headers
// the request dies before POST ever runs and pairing looks "not live".
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

async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(input),
  );
  return [...new Uint8Array(digest)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function rest(path: string, init?: RequestInit): Promise<Response> {
  return fetch(`${SB_URL}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });
}

/** Resolves the caller's user via GoTrue, or null when the JWT is bad. */
async function requireUser(
  req: Request,
): Promise<{ id: string; email?: string } | null> {
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
  if (!user?.email) return json({ error: "unauthorized" }, 401);

  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const code = typeof body.code === "string"
    ? body.code.trim().toUpperCase()
    : "";
  if (!code) return json({ error: "missing_code" }, 400);
  const codeHash = await sha256Hex(code);

  // Read the live session first: dead codes exit here without touching GoTrue,
  // and we need device_label for the devices row later.
  const found = await rest(
    `pairing_sessions?code_hash=eq.${codeHash}&claimed_by=is.null&exchanged=eq.false&expires_at=gte.now()`,
  )
    .then((r) => r.json())
    .catch(() => []);
  if (!Array.isArray(found) || found.length === 0) {
    return json({ error: "invalid_or_expired_code" }, 410);
  }
  const session = found[0];

  // Mint everything BEFORE claiming: if GoTrue or the devices insert fails,
  // the code stays unclaimed and the user can simply retry — a half-written
  // session would leave the TV stuck on "consumed" forever.
  const linkRes = await fetch(`${SB_URL}/auth/v1/admin/generate_link`, {
    method: "POST",
    headers: {
      apikey: ANON_KEY,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ type: "magiclink", email: user.email }),
  });
  if (!linkRes.ok) {
    // Surface the upstream reason (e.g. GoTrue key-format rejections) so
    // failures are diagnosable from the client instead of a bare 502.
    const detail = await linkRes.text().catch(() => "");
    return json(
      {
        error: "grant_mint_failed",
        upstream_status: linkRes.status,
        upstream_detail: detail.slice(0, 200),
      },
      502,
    );
  }
  const link = await linkRes.json();

  const deviceId = crypto.randomUUID();
  const deviceRow = await rest("devices", {
    method: "POST",
    body: JSON.stringify({
      id: deviceId,
      user_id: user.id,
      label: typeof session.device_label === "string" && session.device_label
        ? session.device_label
        : "Television",
    }),
  });
  if (!deviceRow.ok) return json({ error: "device_create_failed" }, 500);

  // Atomic claim+grant in ONE statement: only an unclaimed, unexchanged,
  // live session matches, so racing phones produce exactly one winner.
  const claimed = await rest(
    `pairing_sessions?code_hash=eq.${codeHash}&claimed_by=is.null&exchanged=eq.false&expires_at=gte.now()`,
    {
      method: "PATCH",
      headers: { Prefer: "return=representation" },
      body: JSON.stringify({
        claimed_by: user.id,
        claimed_at: new Date().toISOString(),
        grant_email: user.email,
        grant_otp: link.email_otp,
        granted_at: new Date().toISOString(),
        device_id: deviceId,
      }),
    },
  )
    .then((r) => r.json())
    .catch(() => []);

  if (!Array.isArray(claimed) || claimed.length === 0) {
    // Lost the race or dead code — remove the orphan device row best-effort.
    await rest(`devices?id=eq.${deviceId}`, { method: "DELETE" });
    return json({ error: "invalid_or_expired_code" }, 410);
  }

  return json({ ok: true });
});
