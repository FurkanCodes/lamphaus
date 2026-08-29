const SB_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_ROLE = Deno.env.get("SERVICE_ROLE_JWT") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ARTWORK_PROVIDER_ID = "artwork.tmdb";

const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

async function requireUser(req: Request): Promise<{ id: string } | null> {
  const authorization = req.headers.get("Authorization");
  if (!authorization) return null;
  const response = await fetch(`${SB_URL}/auth/v1/user`, {
    headers: { apikey: ANON_KEY, Authorization: authorization },
  });
  if (!response.ok) return null;
  const user = await response.json();
  return typeof user?.id === "string" ? user : null;
}

const encoder = new TextEncoder();
let encryptionKeyPromise: Promise<CryptoKey> | null = null;

const b64Encode = (bytes: Uint8Array): string => btoa(String.fromCharCode(...bytes));

function b64Decode(text: string): Uint8Array {
  return Uint8Array.from(atob(text), (character) => character.charCodeAt(0));
}

function encryptionKey(): Promise<CryptoKey> {
  const secret = Deno.env.get("PROVIDER_CONFIG_KEY");
  if (!secret) return Promise.reject(new Error("PROVIDER_CONFIG_KEY not set"));
  encryptionKeyPromise ??= crypto.subtle.importKey(
    "raw",
    b64Decode(secret),
    "AES-GCM",
    false,
    ["encrypt", "decrypt"],
  );
  return encryptionKeyPromise;
}

async function encryptConfig(userId: string, config: unknown): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    {
      name: "AES-GCM",
      iv,
      additionalData: encoder.encode(`${userId}:${ARTWORK_PROVIDER_ID}`),
    },
    await encryptionKey(),
    encoder.encode(JSON.stringify(config)),
  );
  return `v1.${b64Encode(iv)}.${b64Encode(new Uint8Array(ciphertext))}`;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const body = await req.json().catch(() => ({}) as Record<string, unknown>);
  const provider = typeof body.provider === "string" ? body.provider.trim().toLowerCase() : "tmdb";
  if (provider !== "tmdb") return json({ error: "unsupported_provider" }, 400);

  const apiKey = typeof body.api_key === "string" ? body.api_key.trim() : "";
  if (!apiKey || apiKey.length > 512) return json({ error: "invalid_api_key" }, 400);

  let encryptedConfig: string;
  try {
    encryptedConfig = await encryptConfig(user.id, { api_key: apiKey });
  } catch (error) {
    console.error("artwork config encryption failed", error instanceof Error ? error.name : "unknown");
    return json({ error: "encrypt_failed" }, 500);
  }

  const response = await fetch(`${SB_URL}/rest/v1/provider_configs`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
      Prefer: "resolution=merge-duplicates,return=minimal",
    },
    body: JSON.stringify({
      user_id: user.id,
      provider_id: ARTWORK_PROVIDER_ID,
      display_name: "TMDB",
      enabled: true,
      sort_order: 0,
      encrypted_config: encryptedConfig,
      updated_at_epoch_millis: Date.now(),
    }),
  });
  if (!response.ok) return json({ error: "save_failed" }, 500);

  return json({ ok: true, provider: "tmdb" });
});
