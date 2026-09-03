import { createProviderConfigCrypto } from "../_shared/provider_config_crypto.ts";
import { validateMdbListKey } from "../_shared/mdblist.ts";

// list-integrations — connection state for the caller's integrations.
//
// Authenticated (verify_jwt=true); the deny-all RLS table is read via service
// role scoped to the caller's user_id. The response NEVER contains credential
// material: only integration name, connection state, last validation outcome,
// and the enabled-source list (SHR-PROD-06). A live key check runs per
// connected integration; a check that could not run (network) reports
// valid: null instead of a false "disconnected".

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

type IntegrationRow = {
  integration: string;
  encrypted_credential: string;
  enabled_sources: unknown;
};

// Mirrors the migration default; used when the stored jsonb is malformed.
const DEFAULT_SOURCES = [
  "imdb",
  "tmdb",
  "trakt",
  "tomatoes",
  "popcorn",
  "metacritic",
  "letterboxd",
];

function readEnabledSources(raw: unknown): string[] {
  if (Array.isArray(raw) && raw.every((entry) => typeof entry === "string")) {
    return raw as string[];
  }
  return [...DEFAULT_SOURCES];
}

// ─────────────────────────────── handler ───────────────────────────────

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const rows = await fetch(
    `${SB_URL}/rest/v1/integration_credentials?user_id=eq.${user.id}&order=integration.asc`,
    { headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` } },
  )
    .then((r) => r.ok ? r.json() : Promise.reject(new Error(`rest_${r.status}`)))
    .catch(() => null);
  if (!Array.isArray(rows)) return json({ error: "list_failed" }, 500);

  const integrations: Array<Record<string, unknown>> = [];
  // REST rows are shaped by our own migration; the named type documents that
  // contract, and every field is still re-validated before use below.
  for (const row of rows as IntegrationRow[]) {
    if (typeof row.integration !== "string") continue;
    const connected = typeof row.encrypted_credential === "string" &&
      row.encrypted_credential.length > 0;

    // Live validation when possible; network trouble stays honest (null).
    let valid: boolean | null = null;
    if (connected) {
      try {
        const decrypted = await providerConfigCrypto.decrypt(
          user.id,
          `integration.${row.integration}`,
          row.encrypted_credential,
        );
        const config = decrypted.config;
        if (config && typeof config === "object" && "apiKey" in config) {
          const apiKey = config.apiKey;
          valid = typeof apiKey === "string" && await validateMdbListKey(apiKey);
        } else {
          valid = false;
        }
      } catch {
        // Undecryptable row: wrong keyring or tampering. Report invalid rather
        // than silently presenting a credential the resolver will reject.
        valid = false;
      }
    }

    integrations.push({
      integration: row.integration,
      connected,
      valid,
      enabledSources: readEnabledSources(row.enabled_sources),
    });
  }

  return json(integrations);
});
