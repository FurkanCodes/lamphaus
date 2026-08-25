// save-provider-config — upserts one provider config, encrypted (plan D4/F5).
//
// Self-contained by choice: the platform's remote bundler only sees this
// function's own directory, so helpers live inline instead of /_shared.
//
// Authenticated (verify_jwt=true). RLS keeps provider_configs deny-all, so
// access travels through service role here, scoped to the caller's user_id.
//
// Wire format of encrypted_config:  "v1.<base64(iv)>.<base64(ciphertext)>"
//   - AES-256-GCM, 12-byte random IV per save
//   - AAD binds ciphertext to "<user_id>:<provider_id>", so copying a blob
//     to another row or user fails decryption instead of silently working.

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

// ────────────────────────── AES-256-GCM codec ──────────────────────────

const encoder = new TextEncoder();

const b64Encode = (bytes: Uint8Array): string => btoa(String.fromCharCode(...bytes));
const b64Decode = (text: string): Uint8Array =>
  Uint8Array.from(atob(text), (c) => c.charCodeAt(0));

let keyPromise: Promise<CryptoKey> | null = null;

function encryptionKey(): Promise<CryptoKey> {
  const secret = Deno.env.get("PROVIDER_CONFIG_KEY");
  if (!secret) return Promise.reject(new Error("PROVIDER_CONFIG_KEY not set"));
  keyPromise ??= crypto.subtle.importKey(
    "raw",
    b64Decode(secret),
    "AES-GCM",
    false,
    ["encrypt", "decrypt"],
  );
  return keyPromise;
}

async function aad(userId: string, providerId: string): Promise<Uint8Array> {
  return encoder.encode(`${userId}:${providerId}`);
}

export async function encryptConfig(
  userId: string,
  providerId: string,
  config: unknown,
): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv, additionalData: await aad(userId, providerId) },
    await encryptionKey(),
    encoder.encode(JSON.stringify(config)),
  );
  return `v1.${b64Encode(iv)}.${b64Encode(new Uint8Array(ciphertext))}`;
}

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
  if (!providerId) return json({ error: "missing_provider_id" }, 400);
  if (typeof body.config !== "object" || body.config === null || Array.isArray(body.config)) {
    return json({ error: "config_must_be_object" }, 400);
  }

  let encrypted: string;
  try {
    encrypted = await encryptConfig(user.id, providerId, body.config);
  } catch (error) {
    console.error("encrypt failed:", error.message);
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
