import {
  createProviderConfigCrypto,
  type DecryptedProviderConfig,
} from "../functions/_shared/provider_config_crypto.ts";

const PAGE_SIZE = 100;
const KEY_ID_PATTERN = /^[a-z0-9_]{1,32}$/;

type ProviderConfigRow = {
  user_id: string;
  provider_id: string;
  encrypted_config: string;
};

type RotationSummary = {
  dry_run: boolean;
  versions: Record<string, number>;
  requiring_rotation: number;
  patched: number;
  failed: number;
};

type RotationOptions = {
  apply: boolean;
  fetchImpl?: typeof fetch;
  env?: Record<string, string | undefined>;
  log?: (message: string) => void;
  error?: (message: string) => void;
};

function category(error: unknown, fallback: string): string {
  if (!(error instanceof Error)) return fallback;
  return /^[a-z0-9_]+$/.test(error.message) ? error.message : fallback;
}

function requiredEnv(
  env: Record<string, string | undefined>,
  name: string,
): string {
  const value = env[name];
  if (!value) throw new Error(`missing_${name.toLowerCase()}`);
  return value;
}

function parseArgs(args: string[]): { apply: boolean } {
  if (args.includes("--help")) {
    console.log(
      "Usage: deno run --allow-env --allow-net supabase/scripts/rotate-provider-configs.ts [--apply]",
    );
    Deno.exit(0);
  }
  if (args.some((arg) => arg !== "--apply")) {
    throw new Error("invalid_argument");
  }
  return { apply: args.includes("--apply") };
}

function rowFromUnknown(value: unknown): ProviderConfigRow {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error("row_invalid");
  }
  const row = value as Record<string, unknown>;
  if (
    typeof row.user_id !== "string" || typeof row.provider_id !== "string" ||
    typeof row.encrypted_config !== "string"
  ) {
    throw new Error("row_invalid");
  }
  return {
    user_id: row.user_id,
    provider_id: row.provider_id,
    encrypted_config: row.encrypted_config,
  };
}

function blobGroup(blob: string): string {
  const parts = blob.split(".");
  if (parts[0] === "v1" && parts.length === 3) return "v1.legacy";
  if (
    parts[0] === "v2" && parts.length === 4 && KEY_ID_PATTERN.test(parts[1])
  ) return `v2.${parts[1]}`;
  return "invalid.unknown";
}

function rowUrl(
  baseUrl: string,
  userId?: string,
  providerId?: string,
  offset?: number,
): string {
  const url = new URL("/rest/v1/provider_configs", `${baseUrl}/`);
  url.searchParams.set("select", "user_id,provider_id,encrypted_config");
  url.searchParams.set("order", "user_id.asc,provider_id.asc");
  if (userId !== undefined) url.searchParams.set("user_id", `eq.${userId}`);
  if (providerId !== undefined) {
    url.searchParams.set("provider_id", `eq.${providerId}`);
  }
  if (offset !== undefined) {
    url.searchParams.set("limit", String(PAGE_SIZE));
    url.searchParams.set("offset", String(offset));
  }
  return url.toString();
}

function authHeaders(serviceRole: string): HeadersInit {
  return { apikey: serviceRole, Authorization: `Bearer ${serviceRole}` };
}

async function loadPage(
  fetchImpl: typeof fetch,
  baseUrl: string,
  serviceRole: string,
  offset: number,
): Promise<unknown[]> {
  const response = await fetchImpl(
    rowUrl(baseUrl, undefined, undefined, offset),
    {
      headers: authHeaders(serviceRole),
    },
  );
  if (!response.ok) throw new Error("list_failed");
  const values: unknown = await response.json();
  if (!Array.isArray(values)) throw new Error("list_invalid");
  return values;
}

function providerIdForError(value: unknown): string {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return "unknown";
  }
  const providerId = (value as Record<string, unknown>).provider_id;
  return typeof providerId === "string" ? providerId : "unknown";
}

async function patchRow(
  fetchImpl: typeof fetch,
  baseUrl: string,
  serviceRole: string,
  row: ProviderConfigRow,
  encryptedConfig: string,
): Promise<void> {
  const response = await fetchImpl(
    rowUrl(baseUrl, row.user_id, row.provider_id),
    {
      method: "PATCH",
      headers: {
        ...authHeaders(serviceRole),
        "Content-Type": "application/json",
        Prefer: "return=minimal",
      },
      body: JSON.stringify({ encrypted_config: encryptedConfig }),
    },
  );
  if (!response.ok) throw new Error("patch_failed");
}

async function verifyActiveRows(
  fetchImpl: typeof fetch,
  baseUrl: string,
  serviceRole: string,
  activeKeyId: string,
): Promise<void> {
  let offset = 0;
  while (true) {
    const rows = await loadPage(fetchImpl, baseUrl, serviceRole, offset);
    for (const value of rows) {
      let row: ProviderConfigRow;
      try {
        row = rowFromUnknown(value);
      } catch {
        throw new Error("active_key_verification_failed");
      }
      const parts = row.encrypted_config.split(".");
      if (
        parts.length !== 4 || parts[0] !== "v2" || parts[1] !== activeKeyId
      ) {
        throw new Error("active_key_verification_failed");
      }
    }
    if (rows.length < PAGE_SIZE) return;
    offset += rows.length;
  }
}

export async function runRotation({
  apply,
  fetchImpl = fetch,
  env = Deno.env.toObject(),
  log = console.log,
  error = console.error,
}: RotationOptions): Promise<number> {
  const baseUrl = requiredEnv(env, "SUPABASE_URL").replace(/\/+$/, "");
  const serviceRole = env.SERVICE_ROLE_JWT ?? env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceRole) throw new Error("missing_service_role");
  const activeKeyId = requiredEnv(env, "PROVIDER_CONFIG_ACTIVE_KEY_ID");
  const keyring = requiredEnv(env, "PROVIDER_CONFIG_KEYRING");
  const codec = createProviderConfigCrypto({
    activeKeyId,
    encodedKeys: keyring,
    legacyEncodedKey: env.PROVIDER_CONFIG_KEY,
  });

  const summary: RotationSummary = {
    dry_run: !apply,
    versions: {},
    requiring_rotation: 0,
    patched: 0,
    failed: 0,
  };
  const countVersion = (group: string): void => {
    summary.versions[group] = (summary.versions[group] ?? 0) + 1;
  };

  let offset = 0;
  while (true) {
    let values: unknown[];
    try {
      values = await loadPage(fetchImpl, baseUrl, serviceRole, offset);
    } catch (loadError) {
      throw new Error(category(loadError, "list_failed"));
    }
    for (const value of values) {
      const encryptedConfig = typeof value === "object" && value !== null &&
          !Array.isArray(value) &&
          typeof (value as Record<string, unknown>).encrypted_config ===
            "string"
        ? (value as Record<string, string>).encrypted_config
        : "";
      countVersion(blobGroup(encryptedConfig));
      let row: ProviderConfigRow;
      try {
        row = rowFromUnknown(value);
      } catch (rowError) {
        summary.failed += 1;
        error(
          JSON.stringify({
            provider_id: providerIdForError(value),
            category: category(rowError, "row_invalid"),
          }),
        );
        continue;
      }
      let decrypted: DecryptedProviderConfig;
      try {
        decrypted = await codec.decrypt(
          row.user_id,
          row.provider_id,
          row.encrypted_config,
        );
      } catch (decryptError) {
        summary.failed += 1;
        error(
          JSON.stringify({
            provider_id: row.provider_id,
            category: category(decryptError, "decrypt_failed"),
          }),
        );
        continue;
      }
      if (!decrypted.needsRotation) continue;
      summary.requiring_rotation += 1;
      if (!apply) continue;

      try {
        const encrypted = await codec.encrypt(
          row.user_id,
          row.provider_id,
          decrypted.config,
        );
        await patchRow(fetchImpl, baseUrl, serviceRole, row, encrypted);
        summary.patched += 1;
      } catch (rotationError) {
        summary.failed += 1;
        error(
          JSON.stringify({
            provider_id: row.provider_id,
            category: category(rotationError, "rotation_failed"),
          }),
        );
      }
    }
    if (values.length < PAGE_SIZE) break;
    offset += values.length;
  }

  if (apply && summary.failed === 0) {
    try {
      await verifyActiveRows(fetchImpl, baseUrl, serviceRole, activeKeyId);
    } catch (verificationError) {
      summary.failed += 1;
      error(
        JSON.stringify({
          category: category(
            verificationError,
            "active_key_verification_failed",
          ),
        }),
      );
    }
  }

  log(JSON.stringify(summary));
  return summary.failed === 0 ? 0 : 1;
}

if (import.meta.main) {
  try {
    const { apply } = parseArgs(Deno.args);
    const exitCode = await runRotation({ apply });
    Deno.exit(exitCode);
  } catch (error) {
    console.error(
      JSON.stringify({ category: category(error, "rotation_failed") }),
    );
    Deno.exit(1);
  }
}
