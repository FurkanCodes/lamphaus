import { createProviderConfigCrypto } from "../_shared/provider_config_crypto.ts";

// list-provider-configs — returns the caller's provider configs, decrypted.
//
// Authenticated (verify_jwt=true); RLS keeps the table deny-all, service
// role fetches rows scoped to the caller's user_id. The shared codec accepts
// legacy v1 and versioned v2 ciphertext without rotating rows on reads.

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

const providerConfigCrypto = createProviderConfigCrypto({
  activeKeyId: Deno.env.get("PROVIDER_CONFIG_ACTIVE_KEY_ID") ?? "",
  encodedKeys: Deno.env.get("PROVIDER_CONFIG_KEYRING") ?? "",
  legacyEncodedKey: Deno.env.get("PROVIDER_CONFIG_KEY"),
});

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
      try {
        const decrypted = await providerConfigCrypto.decrypt(
          user.id,
          row.provider_id,
          row.encrypted_config,
        );
        configs.push({
          provider_id: row.provider_id,
          display_name: row.display_name,
          enabled: row.enabled,
          sort_order: row.sort_order,
          updated_at_epoch_millis: row.updated_at_epoch_millis,
          config: decrypted.config,
        });
      } catch (error) {
        console.error("decrypt failed", row.provider_id, error instanceof Error ? error.message : "unknown");
        throw error;
      }
    }
    return json({ configs });
  } catch {
    return json({ error: "decrypt_failed" }, 500);
  }
});
