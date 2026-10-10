// refresh-push-access — keeps the sync signal's FCM access token fresh.
//
// Self-contained by choice: the platform's remote bundler only sees this
// function's own directory, so helpers live beside it in fcm_access.ts.
//
// Unauthenticated (verify_jwt=false) because pg_cron calls it every 30
// minutes without credentials. It is idempotent and reveals nothing: while
// the stored token stays valid it answers without contacting Google, so a
// stray caller can only make it refresh a token a little early. The token
// itself is written with the service role into sync_push_config, which no
// client can read, and never appears in the response.
//
// Without the FCM_SERVICE_ACCOUNT secret it does nothing: devices still sync
// when they open and through the Realtime fallback.

import {
  isFresh,
  mintAccessToken,
  parseServiceAccount,
} from "./fcm_access.ts";

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const restHeaders = {
  apikey: SERVICE_ROLE,
  Authorization: `Bearer ${SERVICE_ROLE}`,
  "Content-Type": "application/json",
};

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const account = parseServiceAccount(Deno.env.get("FCM_SERVICE_ACCOUNT"));
  if (!account) return json({ configured: false });

  const current = await fetch(
    `${SB_URL}/rest/v1/sync_push_config?id=eq.true&select=fcm_project_id,expires_at,functions_url`,
    { headers: restHeaders },
  );
  if (!current.ok) return json({ error: "config_unavailable" }, 500);
  const row = (await current.json())?.[0] ?? null;
  const now = Date.now();
  const functionsUrl = `${SB_URL}/functions/v1`;
  if (
    row &&
    row.fcm_project_id === account.projectId &&
    row.functions_url === functionsUrl &&
    isFresh(row.expires_at, now)
  ) {
    return json({ configured: true, refreshed: false });
  }

  let minted;
  try {
    minted = await mintAccessToken(account, now);
  } catch (error) {
    // The message names the failure kind only; no key material is logged.
    console.error("refresh-push-access", (error as Error).message);
    return json({ error: "token_exchange_failed" }, 502);
  }

  const saved = await fetch(`${SB_URL}/rest/v1/sync_push_config?on_conflict=id`, {
    method: "POST",
    headers: { ...restHeaders, Prefer: "resolution=merge-duplicates,return=minimal" },
    body: JSON.stringify({
      id: true,
      fcm_project_id: account.projectId,
      access_token: minted.accessToken,
      expires_at: minted.expiresAt,
      functions_url: functionsUrl,
      updated_at: new Date(now).toISOString(),
    }),
  });
  if (!saved.ok) return json({ error: "config_write_failed" }, 500);
  return json({ configured: true, refreshed: true });
});
