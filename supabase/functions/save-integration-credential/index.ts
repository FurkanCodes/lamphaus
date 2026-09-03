import { createProviderConfigCrypto } from "../_shared/provider_config_crypto.ts";
import { validateMdbListKey } from "../_shared/mdblist.ts";

// save-integration-credential — upserts one integration credential, encrypted.
//
// Authenticated (verify_jwt=true). integration_credentials is deny-all RLS, so
// access travels through service role here, scoped to the caller's user_id.
//
// The shared codec writes AES-256-GCM blobs bound to
// "<user_id>:integration.<integration>", reusing the provider_config keyring.
// Validation runs BEFORE storage: an invalid MDBList key never lands in the
// table (answer: 400 invalid_credential).
//
// This function accepts two partial updates:
//   { integration, credential }        → validate + store key
//   { integration, enabledSources }    → update the enabled-source list only
// A sources-only update never touches the stored credential.

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

const INTEGRATION_PATTERN = /^[a-z][a-z0-9_-]{0,31}$/;


type SaveBody = {
  integration?: unknown;
  credential?: unknown;
  enabledSources?: unknown;
};

function validSources(value: unknown): string[] | null {
  if (!Array.isArray(value)) return null;
  const sources: string[] = [];
  for (const entry of value) {
    if (typeof entry !== "string" || !/^[a-z0-9_]{1,32}$/.test(entry)) return null;
    sources.push(entry);
  }
  return sources;
}

// ─────────────────────────────── handler ───────────────────────────────
// Which integrations accept credentials today. Trakt joins in its own phase.
const SUPPORTED_INTEGRATIONS: Record<string, true> = { mdblist: true };


Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await requireUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  let body: SaveBody;
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_body" }, 400);
  }

  const integration = typeof body.integration === "string" ? body.integration : "";
  if (!INTEGRATION_PATTERN.test(integration) || !(integration in SUPPORTED_INTEGRATIONS)) {
    return json({ error: "unsupported_integration" }, 400);
  }

  const now = Date.now();
  const hasCredential = typeof body.credential === "string" && body.credential.trim().length > 0;
  const sources = body.enabledSources === undefined ? null : validSources(body.enabledSources);
  if (body.enabledSources !== undefined && sources === null) {
    return json({ error: "invalid_sources" }, 400);
  }
  if (!hasCredential && sources === null) {
    return json({ error: "nothing_to_save" }, 400);
  }

  if (hasCredential) {
    const credential = (body.credential as string).trim();
    const valid = await validateMdbListKey(credential);
    if (!valid) return json({ error: "invalid_credential" }, 400);
    const encrypted = await providerConfigCrypto.encrypt(
      user.id,
      `integration.${integration}`,
      { apiKey: credential },
    );
    const upsert = await fetch(
      `${SB_URL}/rest/v1/integration_credentials`,
      {
        method: "POST",
        headers: {
          apikey: SERVICE_ROLE,
          Authorization: `Bearer ${SERVICE_ROLE}`,
          "Content-Type": "application/json",
          Prefer: "resolution=merge-duplicates,return=minimal",
        },
        body: JSON.stringify({
          user_id: user.id,
          integration,
          encrypted_credential: encrypted,
          updated_at_epoch_millis: now,
        }),
      },
    );
    if (!upsert.ok) return json({ error: "save_failed" }, 500);
  }

  if (sources !== null) {
    const patch = await fetch(
      `${SB_URL}/rest/v1/integration_credentials?user_id=eq.${user.id}&integration=eq.${integration}`,
      {
        method: "PATCH",
        headers: {
          apikey: SERVICE_ROLE,
          Authorization: `Bearer ${SERVICE_ROLE}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          enabled_sources: sources,
          updated_at_epoch_millis: now,
        }),
      },
    );
    if (!patch.ok) return json({ error: "save_failed" }, 500);
    const patched = await patch.json();
    if (!Array.isArray(patched) || patched.length === 0) {
      return json({ error: "not_connected" }, 409);
    }
  }

  return json({ integration, connected: true, valid: true });
});
