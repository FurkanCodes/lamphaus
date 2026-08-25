// exchange-device-grant — polled every ~3s by the TV that created the
// session (plan F2).
//
// Self-contained by choice: the platform's remote bundler only sees this
// function's own directory, so helpers live inline instead of in /_shared.
//
// Unauthenticated like create; the session id itself is the capability, and
// the grant is handed out EXACTLY once: the atomic update flips exchanged=
// true and nulls the OTP columns in the same statement, so a replayed poll
// finds nothing.
//
// Statuses:
//   pending  → not claimed yet, keep polling (200)
//   granted  → here is your login material, burn after reading (200)
//   consumed → someone already took it (409)
//   expired  → past the 5-minute TTL (410)
//   unknown  → no such session (404)

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
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

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const sessionId = typeof body.session_id === "string" ? body.session_id : "";
  if (!sessionId || sessionId.length > 64) {
    return json({ error: "missing_session_id" }, 400);
  }

  const found = await rest(
    `pairing_sessions?id=eq.${encodeURIComponent(sessionId)}`,
  ).then((r) => r.json());
  if (!Array.isArray(found) || found.length === 0) {
    return json({ status: "unknown" }, 404);
  }
  const session = found[0];

  if (new Date(session.expires_at) < new Date()) {
    return json({ status: "expired" }, 410);
  }
  if (!session.claimed_by) return json({ status: "pending" });
  if (session.exchanged || !session.grant_otp) {
    return json({ status: "consumed" }, 409);
  }

  // Single-use take: only wins if nobody else flipped exchanged first.
  const taken = await rest(
    `pairing_sessions?id=eq.${session.id}&exchanged=eq.false&grant_otp=not.is.null`,
    {
      method: "PATCH",
      headers: { Prefer: "return=representation" },
      body: JSON.stringify({
        exchanged: true,
        exchanged_at: new Date().toISOString(),
        grant_email: null,
        grant_otp: null,
      }),
    },
  )
    .then((r) => r.json())
    .catch(() => []);

  if (!Array.isArray(taken) || taken.length === 0) {
    return json({ status: "consumed" }, 409);
  }

  // Payload comes from the pre-update read: the conditional UPDATE above is
  // what guarantees WE are the one consumer, so those values are ours alone.
  return json({
    status: "granted",
    email: session.grant_email,
    otp: session.grant_otp,
    device_id: session.device_id,
  });
});
