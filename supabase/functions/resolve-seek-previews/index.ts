import { createProviderConfigCrypto } from "../_shared/provider_config_crypto.ts";
import { lookupSeekrSprites, type SeekrTitle } from "../_shared/seekr.ts";

// resolve-seek-previews — the seek-preview manifest for one title (PLY-SEEK-01).
//
// Authenticated (verify_jwt=true). The caller's Seekr key is read from the
// deny-all integration_credentials table, used for one api.seekr.tv /sprites
// lookup, and never returned: the answer carries only the signed WebVTT URL,
// which (like the sprite sheets it names) needs no key (SHR-PROD-06). One key
// saved on any device therefore serves every device on the account.
//
// Request:  { type: "movie"|"series", id: "tt…"|"tmdb:…", season?, episode?, durationMs }
// Answers (all 200 unless noted; "no previews" is a normal outcome):
//   { available: true, vttUrl, sourceDurationMs, scale }
//   { available: false, reason: "not_connected"|"not_found"|"unsupported_title"|
//                               "key_rejected"|"rate_limited" }
//   502 { error: "upstream_unavailable" } — momentary; the client may retry later.

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

/** Longest runtime accepted: anything above 24 h is not a real title. */
const MAX_DURATION_MS = 24 * 60 * 60 * 1000;

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

const providerConfigCrypto = createProviderConfigCrypto({
  activeKeyId: Deno.env.get("PROVIDER_CONFIG_ACTIVE_KEY_ID") ?? "",
  encodedKeys: Deno.env.get("PROVIDER_CONFIG_KEYRING") ?? "",
  legacyEncodedKey: Deno.env.get("PROVIDER_CONFIG_KEY"),
});

async function loadSeekrKey(userId: string): Promise<string | null> {
  try {
    const response = await fetch(
      `${SB_URL}/rest/v1/integration_credentials?user_id=eq.${userId}&integration=eq.seekr`,
      { headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` } },
    );
    if (!response.ok) return null;
    const rows = await response.json() as Array<{ encrypted_credential?: unknown }>;
    const blob = rows[0]?.encrypted_credential;
    if (typeof blob !== "string" || blob === "") return null;
    const decrypted = await providerConfigCrypto.decrypt(userId, "integration.seekr", blob);
    const config = decrypted.config;
    if (!config || typeof config !== "object" || !("apiKey" in config)) return null;
    const apiKey = config.apiKey;
    return typeof apiKey === "string" && apiKey.length > 0 ? apiKey : null;
  } catch {
    return null;
  }
}

function positiveInt(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : null;
}

/** Only public IMDb/TMDB identities reach Seekr; provider-scoped ids never do. */
function titleFrom(body: Record<string, unknown>): SeekrTitle | null {
  const type = body.type;
  const id = typeof body.id === "string" ? body.id : "";
  const imdbId = /^tt\d+$/.test(id) ? id : null;
  const tmdbMatch = /^tmdb:(?:[a-z]+:)?(\d+)$/.exec(id);
  const tmdbId = tmdbMatch ? Number.parseInt(tmdbMatch[1], 10) : null;
  if (imdbId === null && tmdbId === null) return null;
  if (type === "movie") return { kind: "movie", imdbId, tmdbId };
  if (type !== "series") return null;
  const season = positiveInt(body.season);
  const episode = positiveInt(body.episode);
  if (season === null || episode === null) return null;
  return { kind: "episode", imdbId, tmdbId, season, episode };
}

// ─────────────────────────────── handler ───────────────────────────────

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  let body: Record<string, unknown>;
  try {
    const parsed = await req.json();
    if (!parsed || typeof parsed !== "object") throw new Error("not an object");
    body = parsed as Record<string, unknown>;
  } catch {
    return json({ error: "invalid_body" }, 400);
  }

  const durationMs = positiveInt(body.durationMs);
  if (durationMs === null || durationMs === 0 || durationMs > MAX_DURATION_MS) {
    return json({ error: "invalid_request" }, 400);
  }
  const title = titleFrom(body);
  if (title === null) return json({ available: false, reason: "unsupported_title" });

  const apiKey = await loadSeekrKey(user.id);
  if (apiKey === null) return json({ available: false, reason: "not_connected" });

  const lookup = await lookupSeekrSprites(apiKey, title, durationMs);
  switch (lookup.status) {
    case "found":
      return json({
        available: true,
        vttUrl: lookup.vttUrl,
        sourceDurationMs: lookup.sourceDurationMs,
        scale: lookup.scale,
      });
    case "not_found":
      return json({ available: false, reason: "not_found" });
    case "rejected":
      return json({ available: false, reason: "key_rejected" });
    case "rate_limited":
      return json({ available: false, reason: "rate_limited" });
    case "unavailable":
      return json({ error: "upstream_unavailable" }, 502);
  }
});
