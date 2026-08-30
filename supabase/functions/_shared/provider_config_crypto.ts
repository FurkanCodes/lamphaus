const KEY_ID_PATTERN = /^[a-z0-9_]{1,32}$/;
const IV_LENGTH_BYTES = 12;
const AES_KEY_LENGTH_BYTES = 32;
const GCM_TAG_LENGTH_BYTES = 16;

export type ProviderConfigCryptoOptions = {
  activeKeyId: string;
  encodedKeys: string | Readonly<Record<string, string>>;
  legacyEncodedKey?: string | null;
};

export type DecryptedProviderConfig = {
  config: unknown;
  keyId: string;
  needsRotation: boolean;
};

type Keyring = Readonly<Record<string, string>>;

const encoder = new TextEncoder();
const decoder = new TextDecoder("utf-8", { fatal: true });
const bufferSource = (bytes: Uint8Array): BufferSource =>
  bytes as unknown as BufferSource;
function fail(category: string): never {
  throw new Error(category);
}

function decodeBase64(value: string, category: string): Uint8Array {
  if (
    !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(
      value,
    )
  ) {
    fail(category);
  }
  try {
    const decoded = Uint8Array.from(
      atob(value),
      (character) => character.charCodeAt(0),
    );
    if (encodeBase64(decoded) !== value) fail(category);
    return decoded;
  } catch {
    fail(category);
  }
}

function encodeBase64(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes));
}

function readJsonString(
  text: string,
  start: number,
): { value: string; end: number } {
  let escaped = false;
  for (let index = start + 1; index < text.length; index += 1) {
    const character = text[index];
    if (escaped) {
      escaped = false;
      continue;
    }
    if (character === "\\") {
      escaped = true;
      continue;
    }
    if (character === '"') {
      try {
        return {
          value: JSON.parse(text.slice(start, index + 1)) as string,
          end: index + 1,
        };
      } catch {
        fail("provider_config_invalid_keyring");
      }
    }
  }
  fail("provider_config_invalid_keyring");
}

function skipWhitespace(text: string, start: number): number {
  let index = start;
  while (/\s/.test(text[index] ?? "")) index += 1;
  return index;
}

function assertNoDuplicateKeyringKeys(text: string): void {
  let index = skipWhitespace(text, 0);
  if (text[index] !== "{") fail("provider_config_invalid_keyring");
  index = skipWhitespace(text, index + 1);
  const seen = new Set<string>();
  if (text[index] === "}") {
    if (skipWhitespace(text, index + 1) !== text.length) {
      fail("provider_config_invalid_keyring");
    }
    return;
  }

  while (index < text.length) {
    if (text[index] !== '"') fail("provider_config_invalid_keyring");
    const key = readJsonString(text, index);
    if (seen.has(key.value)) fail("provider_config_duplicate_key_id");
    seen.add(key.value);
    index = skipWhitespace(text, key.end);
    if (text[index] !== ":") fail("provider_config_invalid_keyring");
    index = skipWhitespace(text, index + 1);
    if (text[index] !== '"') fail("provider_config_invalid_keyring");
    const value = readJsonString(text, index);
    index = skipWhitespace(text, value.end);
    if (text[index] === "}") {
      if (skipWhitespace(text, index + 1) !== text.length) {
        fail("provider_config_invalid_keyring");
      }
      return;
    }
    if (text[index] !== ",") fail("provider_config_invalid_keyring");
    index = skipWhitespace(text, index + 1);
  }
  fail("provider_config_invalid_keyring");
}

function parseKeyring(encodedKeys: string | Keyring): Keyring {
  let parsed: unknown = encodedKeys;
  if (typeof encodedKeys === "string") {
    assertNoDuplicateKeyringKeys(encodedKeys);
    try {
      parsed = JSON.parse(encodedKeys) as unknown;
    } catch {
      fail("provider_config_invalid_keyring");
    }
  }
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    fail("provider_config_invalid_keyring");
  }
  const keyring: Record<string, string> = {};
  for (
    const [keyId, encodedKey] of Object.entries(
      parsed as Record<string, unknown>,
    )
  ) {
    if (!KEY_ID_PATTERN.test(keyId)) fail("provider_config_invalid_key_id");
    if (typeof encodedKey !== "string") fail("provider_config_invalid_keyring");
    if (keyring[keyId] !== undefined) fail("provider_config_duplicate_key_id");
    keyring[keyId] = encodedKey;
  }
  return keyring;
}

function decodeAesKey(encodedKey: string, category: string): Uint8Array {
  const key = decodeBase64(encodedKey, category);
  if (key.length !== AES_KEY_LENGTH_BYTES) {
    fail("provider_config_invalid_key_length");
  }
  return key;
}

function serializeConfig(config: unknown): Uint8Array {
  try {
    const serialized = JSON.stringify(config);
    if (serialized === undefined) fail("provider_config_invalid_json");
    return encoder.encode(serialized);
  } catch (error) {
    if (
      error instanceof Error && error.message.startsWith("provider_config_")
    ) throw error;
    fail("provider_config_invalid_json");
  }
}

function parseConfig(plaintext: ArrayBuffer): unknown {
  try {
    return JSON.parse(decoder.decode(new Uint8Array(plaintext)));
  } catch {
    fail("provider_config_invalid_json");
  }
}

function aad(userId: string, providerId: string): BufferSource {
  return bufferSource(encoder.encode(`${userId}:${providerId}`));
}

function importAesKey(keyBytes: Uint8Array): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    "raw",
    bufferSource(keyBytes),
    "AES-GCM",
    false,
    ["encrypt", "decrypt"],
  );
}

function safeDecryptError(): Error {
  return new Error("provider_config_authentication_failed");
}

export function createProviderConfigCrypto({
  activeKeyId,
  encodedKeys,
  legacyEncodedKey,
}: ProviderConfigCryptoOptions): {
  encrypt(userId: string, providerId: string, config: unknown): Promise<string>;
  decrypt(
    userId: string,
    providerId: string,
    blob: string,
  ): Promise<DecryptedProviderConfig>;
} {
  if (!KEY_ID_PATTERN.test(activeKeyId)) {
    fail("provider_config_invalid_active_key_id");
  }
  const keyring = parseKeyring(encodedKeys);
  if (!Object.hasOwn(keyring, activeKeyId)) {
    fail("provider_config_active_key_missing");
  }

  const keyPromises = new Map<string, Promise<CryptoKey>>();
  for (const [keyId, encodedKey] of Object.entries(keyring)) {
    keyPromises.set(
      keyId,
      importAesKey(decodeAesKey(encodedKey, "provider_config_invalid_key")),
    );
  }
  const legacyKeyPromise = legacyEncodedKey == null ? null : importAesKey(
    decodeAesKey(legacyEncodedKey, "provider_config_invalid_legacy_key"),
  );

  const encrypt = async (
    userId: string,
    providerId: string,
    config: unknown,
  ): Promise<string> => {
    const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH_BYTES));
    let ciphertext: ArrayBuffer;
    try {
      ciphertext = await crypto.subtle.encrypt(
        {
          name: "AES-GCM",
          iv: bufferSource(iv),
          additionalData: aad(userId, providerId),
        },
        await keyPromises.get(activeKeyId)!,
        bufferSource(serializeConfig(config)),
      );
    } catch {
      fail("provider_config_encryption_failed");
    }
    return `v2.${activeKeyId}.${encodeBase64(iv)}.${
      encodeBase64(new Uint8Array(ciphertext))
    }`;
  };

  const decrypt = async (
    userId: string,
    providerId: string,
    blob: string,
  ): Promise<DecryptedProviderConfig> => {
    const parts = blob.split(".");
    const version = parts[0];
    let keyId: string;
    let ivText: string;
    let ciphertextText: string;
    let keyPromise: Promise<CryptoKey> | undefined;
    let needsRotation: boolean;

    if (version === "v1" && parts.length === 3) {
      keyId = "legacy";
      ivText = parts[1];
      ciphertextText = parts[2];
      keyPromise = legacyKeyPromise ?? undefined;
      needsRotation = true;
      if (!keyPromise) fail("provider_config_legacy_key_unavailable");
    } else if (version === "v2" && parts.length === 4) {
      keyId = parts[1];
      ivText = parts[2];
      ciphertextText = parts[3];
      if (!KEY_ID_PATTERN.test(keyId)) fail("provider_config_unknown_key_id");
      keyPromise = keyPromises.get(keyId);
      needsRotation = keyId !== activeKeyId;
      if (!keyPromise) fail("provider_config_unknown_key_id");
    } else {
      fail("provider_config_invalid_blob_format");
    }

    const iv = decodeBase64(ivText, "provider_config_invalid_base64");
    const ciphertext = decodeBase64(
      ciphertextText,
      "provider_config_invalid_base64",
    );
    if (
      iv.length !== IV_LENGTH_BYTES || ciphertext.length < GCM_TAG_LENGTH_BYTES
    ) {
      fail("provider_config_invalid_blob_length");
    }

    let plaintext: ArrayBuffer;
    try {
      plaintext = await crypto.subtle.decrypt(
        {
          name: "AES-GCM",
          iv: bufferSource(iv),
          additionalData: aad(userId, providerId),
        },
        await keyPromise,
        bufferSource(ciphertext),
      );
    } catch {
      throw safeDecryptError();
    }

    return { config: parseConfig(plaintext), keyId, needsRotation };
  };

  return { encrypt, decrypt };
}
