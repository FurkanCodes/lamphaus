// create-pairing-session — called by the TV on cold start (plan flow F2).
//
// Self-contained by choice: the platform's remote bundler only sees this
// function's own directory, so helpers live inline instead of in /_shared.
//
// Unauthenticated by design: a TV has no identity yet. Protections are
// IP-hash rate limiting (20 sessions / 15 min) plus 5-minute single-use
// sessions keyed by SHA-256 of a 6-character code. Deployed with
// verify_jwt=false for exactly this reason.

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const PAIRING_SITE_URL =
  Deno.env.get("PAIRING_SITE_URL") ?? "https://furkancodes.github.io/lamphaus";

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

// No I/O/0/1 — codes must survive being read off a TV across a room.
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

function randomShortCode(length = 6): string {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return [...bytes]
    .map((b) => CODE_ALPHABET[b % CODE_ALPHABET.length])
    .join("");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const ipHash = await sha256Hex(clientIp(req));
  const slotAllowed = await rest("rpc/consume_pairing_slot", {
    method: "POST",
    body: JSON.stringify({
      p_ip_hash: ipHash,
      p_limit: 20,
      p_window_minutes: 15,
    }),
  })
    .then((r) => r.json())
    .catch(() => null);

  if (slotAllowed !== true) return json({ error: "rate_limited" }, 429);

  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const deviceLabel =
    typeof body.device_label === "string" && body.device_label.trim()
      ? body.device_label.trim().slice(0, 40)
      : "Television";
  // Hardware-bound identity (ANDROID_ID): lets claim() reuse the same
  // devices row instead of cloning one per re-pair. Sanitized hard.
  const deviceKey =
    typeof body.device_key === "string" &&
      /^[A-Za-z0-9._-]{8,128}$/.test(body.device_key.trim())
      ? body.device_key.trim()
      : null;

  const sessionId = crypto.randomUUID();
  const shortCode = randomShortCode();
  const codeHash = await sha256Hex(shortCode);
  const expiresAt = new Date(Date.now() + 5 * 60_000).toISOString();

  const inserted = await rest("pairing_sessions", {
    method: "POST",
    body: JSON.stringify({
      id: sessionId,
      code_hash: codeHash,
      device_label: deviceLabel,
      device_key: deviceKey,
      expires_at: expiresAt,
    }),
  });
  if (!inserted.ok) return json({ error: "session_create_failed" }, 500);

  return json({
    session_id: sessionId,
    short_code: shortCode,
    // e=epochSeconds lets the /pair page render a live "time left" without
    // an extra round trip; the server remains authoritative via 410.
    qr_payload: `${PAIRING_SITE_URL}/pair?code=${shortCode}&e=${Math.floor(
      Date.parse(expiresAt) / 1000,
    )}`,
    expires_at: expiresAt,
  });
});
