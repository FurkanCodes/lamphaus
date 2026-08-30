import { createProviderConfigCrypto } from "../functions/_shared/provider_config_crypto.ts";
import { runRotation } from "../scripts/rotate-provider-configs.ts";

const b64Encode = (bytes: Uint8Array): string =>
  btoa(String.fromCharCode(...bytes));
const bufferSource = (bytes: Uint8Array): BufferSource =>
  bytes as unknown as BufferSource;
const encoder = new TextEncoder();

type Row = {
  user_id: string;
  provider_id: string;
  encrypted_config: string;
};

type FetchState = {
  rows: Row[];
  patches: Array<{ url: string; body: Record<string, unknown> }>;
  failProviderId?: string;
  listOffsets: number[];
};

function keyBytes(seed: number): Uint8Array {
  return Uint8Array.from({ length: 32 }, (_, index) => (seed + index) & 0xff);
}

async function legacyBlob(
  keyBytes: Uint8Array,
  userId: string,
  providerId: string,
  config: unknown,
): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    bufferSource(keyBytes),
    "AES-GCM",
    false,
    ["encrypt"],
  );
  const iv = Uint8Array.from({ length: 12 }, (_, index) => index + 1);
  const ciphertext = await crypto.subtle.encrypt(
    {
      name: "AES-GCM",
      iv: bufferSource(iv),
      additionalData: bufferSource(encoder.encode(`${userId}:${providerId}`)),
    },
    key,
    bufferSource(encoder.encode(JSON.stringify(config))),
  );
  return `v1.${b64Encode(iv)}.${b64Encode(new Uint8Array(ciphertext))}`;
}

function env(
  active: Uint8Array,
  old: Uint8Array,
  legacy: Uint8Array,
): Record<string, string> {
  return {
    SUPABASE_URL: "https://fixture.supabase.co",
    SERVICE_ROLE_JWT: "service-role-fixture",
    PROVIDER_CONFIG_ACTIVE_KEY_ID: "k2",
    PROVIDER_CONFIG_KEYRING: JSON.stringify({
      k1: b64Encode(old),
      k2: b64Encode(active),
    }),
    PROVIDER_CONFIG_KEY: b64Encode(legacy),
  };
}

function fakeFetch(state: FetchState): typeof fetch {
  return async (input, init) => {
    const url = input instanceof Request ? input.url : String(input);
    const parsed = new URL(url);
    if (init?.method === "PATCH") {
      const userId = parsed.searchParams.get("user_id")?.slice("eq.".length);
      const providerId = parsed.searchParams.get("provider_id")?.slice(
        "eq.".length,
      );
      if (providerId === state.failProviderId) {
        return new Response(null, { status: 500 });
      }
      const row = state.rows.find((candidate) =>
        candidate.user_id === userId && candidate.provider_id === providerId
      );
      if (!row) return new Response(null, { status: 404 });
      const body = JSON.parse(String(init.body)) as Record<string, unknown>;
      state.patches.push({ url: parsed.toString(), body });
      if (
        Object.keys(body).length !== 1 ||
        typeof body.encrypted_config !== "string"
      ) {
        return new Response(null, { status: 400 });
      }
      row.encrypted_config = body.encrypted_config;
      return new Response(null, { status: 204 });
    }

    const offset = Number(parsed.searchParams.get("offset") ?? "0");
    const limit = Number(parsed.searchParams.get("limit") ?? "100");
    state.listOffsets.push(offset);
    const userFilter = parsed.searchParams.get("user_id")?.slice("eq.".length);
    const providerFilter = parsed.searchParams.get("provider_id")?.slice(
      "eq.".length,
    );
    const rows = state.rows.filter((row) =>
      (userFilter === undefined || row.user_id === userFilter) &&
      (providerFilter === undefined || row.provider_id === providerFilter)
    );
    return Response.json(rows.slice(offset, offset + limit));
  };
}

function summary(logs: string[]): Record<string, unknown> {
  return JSON.parse(logs.at(-1) ?? "{}") as Record<string, unknown>;
}

Deno.test("dry-run reads all pages and never writes", async () => {
  const active = keyBytes(32);
  const old = keyBytes(64);
  const legacy = keyBytes(96);
  const codec = createProviderConfigCrypto({
    activeKeyId: "k2",
    encodedKeys: env(active, old, legacy).PROVIDER_CONFIG_KEYRING,
    legacyEncodedKey: b64Encode(legacy),
  });
  const rows: Row[] = [];
  for (let index = 0; index < 101; index += 1) {
    rows.push({
      user_id: "user-a",
      provider_id: `provider_${index}`,
      encrypted_config: await codec.encrypt("user-a", `provider_${index}`, {
        api_key: "fixture-secret",
      }),
    });
  }
  rows[0].encrypted_config = await legacyBlob(legacy, "user-a", "provider_0", {
    api_key: "fixture-secret",
  });
  const state: FetchState = { rows, patches: [], listOffsets: [] };
  const logs: string[] = [];
  const errors: string[] = [];
  const exitCode = await runRotation({
    apply: false,
    env: env(active, old, legacy),
    fetchImpl: fakeFetch(state),
    log: (message) => logs.push(message),
    error: (message) => errors.push(message),
  });
  const result = summary(logs);
  if (exitCode !== 0 || state.patches.length !== 0) {
    throw new Error("dry-run wrote or failed");
  }
  if (
    JSON.stringify(result.versions) !==
      JSON.stringify({ "v1.legacy": 1, "v2.k2": 100 })
  ) {
    throw new Error(`unexpected version totals: ${JSON.stringify(result)}`);
  }
  if (
    result.requiring_rotation !== 1 || result.failed !== 0 ||
    errors.length !== 0
  ) throw new Error("unexpected dry-run counts");
  if (state.listOffsets.join(",") !== "0,100") {
    throw new Error(`pagination not exercised: ${state.listOffsets}`);
  }
  if (logs.some((message) => message.includes("fixture-secret"))) {
    throw new Error("plaintext leaked in dry-run output");
  }
});

Deno.test("apply patches exact rows and reruns idempotently", async () => {
  const active = keyBytes(32);
  const old = keyBytes(64);
  const legacy = keyBytes(96);
  const state: FetchState = {
    rows: [
      {
        user_id: "user-a",
        provider_id: "provider_a",
        encrypted_config: await legacyBlob(legacy, "user-a", "provider_a", {
          api_key: "fixture-secret",
        }),
      },
      {
        user_id: "user-a",
        provider_id: "provider_b",
        encrypted_config: await legacyBlob(legacy, "user-a", "provider_b", {
          api_key: "fixture-secret",
        }),
      },
    ],
    patches: [],
    listOffsets: [],
  };
  const logs: string[] = [];
  const exitCode = await runRotation({
    apply: true,
    env: env(active, old, legacy),
    fetchImpl: fakeFetch(state),
    log: (message) => logs.push(message),
    error: () => undefined,
  });
  if (exitCode !== 0 || state.patches.length !== 2) {
    throw new Error("apply did not patch both rows");
  }
  for (const patch of state.patches) {
    if (Object.keys(patch.body).join(",") !== "encrypted_config") {
      throw new Error("patch changed more than encrypted_config");
    }
    if (
      !state.rows.some((row) =>
        patch.url.includes(`provider_id=eq.${row.provider_id}`)
      )
    ) throw new Error("patch row key mismatch");
    if (!String(patch.body.encrypted_config).startsWith("v2.k2.")) {
      throw new Error("patch did not use active key");
    }
  }

  state.patches.length = 0;
  logs.length = 0;
  const rerunCode = await runRotation({
    apply: true,
    env: env(active, old, legacy),
    fetchImpl: fakeFetch(state),
    log: (message) => logs.push(message),
    error: () => undefined,
  });
  const rerun = summary(logs);
  if (
    rerunCode !== 0 || state.patches.length !== 0 ||
    rerun.requiring_rotation !== 0
  ) throw new Error("rerun was not idempotent");
});

Deno.test("continues after row-local failure and returns nonzero", async () => {
  const active = keyBytes(32);
  const old = keyBytes(64);
  const legacy = keyBytes(96);
  const state: FetchState = {
    rows: [
      {
        user_id: "user-a",
        provider_id: "provider_ok",
        encrypted_config: await legacyBlob(legacy, "user-a", "provider_ok", {
          api_key: "fixture-secret",
        }),
      },
      {
        user_id: "user-a",
        provider_id: "provider_fail",
        encrypted_config: await legacyBlob(legacy, "user-a", "provider_fail", {
          api_key: "fixture-secret",
        }),
      },
    ],
    patches: [],
    failProviderId: "provider_fail",
    listOffsets: [],
  };
  const errors: string[] = [];
  const exitCode = await runRotation({
    apply: true,
    env: env(active, old, legacy),
    fetchImpl: fakeFetch(state),
    log: () => undefined,
    error: (message) => errors.push(message),
  });
  if (exitCode !== 1 || state.patches.length !== 1) {
    throw new Error("row-local failure did not continue");
  }
  if (
    errors.length !== 1 || !errors[0].includes("provider_fail") ||
    errors[0].includes("fixture-secret")
  ) {
    throw new Error(`unexpected failure output: ${errors}`);
  }
});

Deno.test("counts malformed rows as failures without aborting the page", async () => {
  const active = keyBytes(32);
  const old = keyBytes(64);
  const legacy = keyBytes(96);
  const state: FetchState = {
    rows: [{
      user_id: "user-a",
      provider_id: "provider_valid",
      encrypted_config: await createProviderConfigCrypto({
        activeKeyId: "k2",
        encodedKeys: env(active, old, legacy).PROVIDER_CONFIG_KEYRING,
        legacyEncodedKey: b64Encode(legacy),
      }).encrypt("user-a", "provider_valid", { api_key: "fixture-secret" }),
    }],
    patches: [],
    listOffsets: [],
  };
  const fetchImpl: typeof fetch = async (input, init) => {
    if (init?.method === "PATCH") return fakeFetch(state)(input, init);
    return Response.json([
      ...state.rows,
      { user_id: "user-a", provider_id: "provider_invalid" },
    ]);
  };
  const errors: string[] = [];
  const logs: string[] = [];
  const exitCode = await runRotation({
    apply: false,
    env: env(active, old, legacy),
    fetchImpl,
    log: (message) => logs.push(message),
    error: (message) => errors.push(message),
  });
  const result = summary(logs);
  if (exitCode !== 1 || result.failed !== 1 || errors.length !== 1) {
    throw new Error("malformed row was not counted as a failure");
  }
  if (
    !errors[0].includes("provider_invalid") ||
    !errors[0].includes("row_invalid")
  ) {
    throw new Error(`unexpected malformed row output: ${errors}`);
  }
});
