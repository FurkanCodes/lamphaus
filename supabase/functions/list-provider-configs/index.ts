// list-provider-configs — returns the caller's provider configs, decrypted.
//
// Self-contained by choice: the platform's remote bundler only sees this
// function's own directory, so helpers live inline instead of /_shared.
//
// Authenticated (verify_jwt=true); RLS keeps the table deny-all, service
// role fetches rows scoped to the caller's user_id. AAD binding means a
// row only decrypts for its own user_id + provider_id.

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

const decoder = new TextDecoder();

const b64Decode = (text: string): Uint8Array =>
  Uint8Array.from(atob(text), (c) => c.charCodeAt(0));
const bufferSource = (bytes: Uint8Array): BufferSource => bytes as unknown as BufferSource;

let keyPromise: Promise<CryptoKey> | null = null;

function encryptionKey(): Promise<CryptoKey> {
  const secret = Deno.env.get("PROVIDER_CONFIG_KEY");
  if (!secret) return Promise.reject(new Error("PROVIDER_CONFIG_KEY not set"));
  keyPromise ??= crypto.subtle.importKey(
    "raw",
    bufferSource(b64Decode(secret)),
    "AES-GCM",
    false,
    ["encrypt", "decrypt"],
  );
  return keyPromise;
}

async function decryptConfig(
  userId: string,
  providerId: string,
  blob: string,
): Promise<unknown> {
  const [version, ivText, ciphertextText] = blob.split(".");
  if (version !== "v1" || !ivText || !ciphertextText) {
    throw new Error(`unsupported_blob_format:${version ?? "empty"}`);
  }
  const plaintext = await crypto.subtle.decrypt(
    {
      name: "AES-GCM",
      iv: bufferSource(b64Decode(ivText)),
      additionalData: bufferSource(new TextEncoder().encode(`${userId}:${providerId}`)),
    },
    await encryptionKey(),
    bufferSource(b64Decode(ciphertextText)),
  );
  return JSON.parse(decoder.decode(plaintext));
}

const isArtworkProviderId = (value: unknown): value is string =>
  typeof value === "string" && value.toLowerCase().startsWith("artwork.");
// ─────────────────────────────── handler ───────────────────────────────

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const rows = await fetch(
    `${SB_URL}/rest/v1/provider_configs?user_id=eq.${user.id}&order=sort_order.asc,provider_id.asc`,
    { headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` } },
  )
    .then((r) => r.ok ? r.json() : Promise.reject(new Error(`rest_${r.status}`)))
    .catch(() => null);
  if (!Array.isArray(rows)) return json({ error: "list_failed" }, 500);

  // Any undecryptable row is a real integrity problem (wrong key / tampering)
  // and must be loud, never silently skipped.
  try {
    const configs = [];
    for (const row of rows) {
      if (isArtworkProviderId(row.provider_id)) continue;
      configs.push({
        provider_id: row.provider_id,
        display_name: row.display_name,
        enabled: row.enabled,
        sort_order: row.sort_order,
        updated_at_epoch_millis: row.updated_at_epoch_millis,
        config: await decryptConfig(user.id, row.provider_id, row.encrypted_config),
      });
    }
    return json({ configs });
  } catch (error) {
    console.error("decrypt failed", error instanceof Error ? error.name : "unknown");
    return json({ error: "decrypt_failed" }, 500);
  }
});
