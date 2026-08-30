import { createProviderConfigCrypto } from "../_shared/provider_config_crypto.ts";

// save-provider-config — upserts one provider config, encrypted.
//
// Authenticated (verify_jwt=true). RLS keeps provider_configs deny-all, so
// access travels through service role here, scoped to the caller's user_id.
//
// The shared codec writes v2.<key_id>.<base64(iv)>.<base64(ciphertext)>.
// AES-256-GCM uses a fresh 12-byte IV per save, and AAD binds ciphertext to
// "<user_id>:<provider_id>" so copying a blob to another row or user fails.

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

// Browsers preflight calls from web settings surfaces; pairing set the precedent.
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

const providerConfigCrypto = createProviderConfigCrypto({
  activeKeyId: Deno.env.get("PROVIDER_CONFIG_ACTIVE_KEY_ID") ?? "",
  encodedKeys: Deno.env.get("PROVIDER_CONFIG_KEYRING") ?? "",
  legacyEncodedKey: Deno.env.get("PROVIDER_CONFIG_KEY"),
});

// ─────────────────────────────── handler ───────────────────────────────

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const providerId = typeof body.provider_id === "string" && body.provider_id.trim()
    ? body.provider_id.trim().slice(0, 200)
    : "";
  if (providerId.toLowerCase().startsWith("artwork.")) return json({ error: "reserved_provider_id" }, 400);
  if (typeof body.config !== "object" || body.config === null || Array.isArray(body.config)) {
    return json({ error: "config_must_be_object" }, 400);
  }

  let encrypted: string;
  try {
    encrypted = await providerConfigCrypto.encrypt(user.id, providerId, body.config);
  } catch (error) {
    console.error("encrypt failed", providerId, error instanceof Error ? error.message : "unknown");
    return json({ error: "encrypt_failed" }, 500);
  }

  const upserted = await fetch(`${SB_URL}/rest/v1/provider_configs`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
      Prefer: "resolution=merge-duplicates,return=representation",
    },
    body: JSON.stringify({
      user_id: user.id,
      provider_id: providerId,
      display_name: typeof body.display_name === "string"
        ? body.display_name.slice(0, 120)
        : "",
      enabled: typeof body.enabled === "boolean" ? body.enabled : true,
      sort_order: Number.isFinite(body.sort_order) ? body.sort_order : 0,
      encrypted_config: encrypted,
      updated_at_epoch_millis: Date.now(),
    }),
  });
  if (!upserted.ok) return json({ error: "upsert_failed" }, 500);

  return json({ ok: true });
});
