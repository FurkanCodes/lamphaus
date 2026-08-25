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
// x-supabase-api-version is sent by newer supabase-js releases — a missing
// entry makes the BROWSER cancel the request right after a 204 preflight.
const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type, x-supabase-api-version",
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

function clientIp(req: Request): string {
  return (
    req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ??
    req.headers.get("x-real-ip") ??
    "unknown"
  );
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

  // Claims get their OWN rate bucket (ip hash salted with "|claim") so
  // they never share counters with session creation. Without this an
  // authenticated script could brute-force live codes indefinitely.
  const ipHash = await sha256Hex(`${clientIp(req)}|claim`);
  const slotAllowed = await rest("rpc/consume_pairing_slot", {
    method: "POST",
    body: JSON.stringify({
      p_ip_hash: ipHash,
      p_limit: 10,
      p_window_minutes: 1,
    }),
  })
    .then((r) => r.json())
    .catch(() => null);
  if (slotAllowed !== true) return json({ error: "rate_limited" }, 429);

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

  // Find-or-create ONE devices entry per physical TV: a re-pairing TV
  // (revoked, deleted-account recovery, expiry) must reactivate its old
  // row instead of cloning it. Without device_key (legacy sessions) we
  // always insert, exactly as before.
  const deviceKey =
    typeof session.device_key === "string" && session.device_key
      ? session.device_key
      : null;
  let deviceId: string;
  let insertedNew = false;
  if (deviceKey) {
    const existing = await rest(
      `devices?user_id=eq.${user.id}&device_key=eq.${encodeURIComponent(deviceKey)}&select=id`,
    )
      .then((r) => r.json())
      .catch(() => []);
    if (Array.isArray(existing) && existing.length > 0) {
      // Re-activate: the old bound session is long dead (that's why the
      // TV shows a QR); register_device_session binds the fresh one.
      const reused = await rest(`devices?id=eq.${existing[0].id}`, {
        method: "PATCH",
        body: JSON.stringify({
          revoked: false,
          auth_session_id: null,
          label:
            typeof session.device_label === "string" && session.device_label
              ? session.device_label
              : "Television",
        }),
      });
      if (!reused.ok) return json({ error: "device_create_failed" }, 500);
      deviceId = existing[0].id;
    } else {
      deviceId = crypto.randomUUID();
      insertedNew = true;
    }
  } else {
    deviceId = crypto.randomUUID();
    insertedNew = true;
  }
  if (insertedNew) {
    const deviceRow = await rest("devices", {
      method: "POST",
      body: JSON.stringify({
        id: deviceId,
        user_id: user.id,
        label:
          typeof session.device_label === "string" && session.device_label
            ? session.device_label
            : "Television",
        device_key: deviceKey,
      }),
    });
    if (!deviceRow.ok) return json({ error: "device_create_failed" }, 500);
  }

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
    // Lost the race or dead code — remove the ORPHANED row best-effort,
    // but never a reused pre-existing device entry.
    if (insertedNew) {
      await rest(`devices?id=eq.${deviceId}`, { method: "DELETE" });
    }
    return json({ error: "invalid_or_expired_code" }, 410);
  }

  return json({ ok: true });
});
