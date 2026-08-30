import {
  createProviderConfigCrypto,
  type DecryptedProviderConfig,
} from "../functions/_shared/provider_config_crypto.ts";

const hexDecode = (hex: string): Uint8Array =>
  new Uint8Array((hex.match(/../g) ?? []).map((byte) => parseInt(byte, 16)));
const bufferSource = (bytes: Uint8Array): BufferSource =>
  bytes as unknown as BufferSource;
const b64Encode = (bytes: Uint8Array): string =>
  btoa(String.fromCharCode(...bytes));
const encoder = new TextEncoder();

async function aesGcm(
  keyBytes: Uint8Array,
  iv: Uint8Array,
  plaintext: Uint8Array,
  additionalData?: Uint8Array,
): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey(
    "raw",
    bufferSource(keyBytes),
    "AES-GCM",
    false,
    ["encrypt"],
  );
  const sealed = await crypto.subtle.encrypt(
    {
      name: "AES-GCM",
      iv: bufferSource(iv),
      additionalData: additionalData && bufferSource(additionalData),
    },
    key,
    bufferSource(plaintext),
  );
  return new Uint8Array(sealed);
}

function assertEqual(
  actual: Uint8Array,
  expected: Uint8Array,
  label: string,
): void {
  const a = [...actual].map((b) => b.toString(16).padStart(2, "0")).join("");
  const e = [...expected].map((b) => b.toString(16).padStart(2, "0")).join("");
  if (a !== e) throw new Error(`${label}: expected ${e}, actual ${a}`);
}

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

async function expectThrows(
  label: string,
  block: () => Promise<unknown>,
): Promise<void> {
  try {
    await block();
  } catch {
    console.log(`  ✓ ${label}`);
    return;
  }
  throw new Error(`${label}: expected rejection`);
}

function expectThrowsSync(label: string, block: () => unknown): void {
  try {
    block();
  } catch {
    console.log(`  ✓ ${label}`);
    return;
  }
  throw new Error(`${label}: expected rejection`);
}

function keyBytes(seed: number): Uint8Array {
  return Uint8Array.from({ length: 32 }, (_, index) => (seed + index) & 0xff);
}

function keyring(active: Uint8Array, old: Uint8Array): string {
  return JSON.stringify({ k1: b64Encode(old), k2: b64Encode(active) });
}

async function legacyBlob(
  key: Uint8Array,
  userId: string,
  providerId: string,
  plaintext: string,
): Promise<string> {
  const iv = Uint8Array.from({ length: 12 }, (_, index) => index + 1);
  const ciphertext = await aesGcm(
    key,
    iv,
    encoder.encode(plaintext),
    encoder.encode(`${userId}:${providerId}`),
  );
  return `v1.${b64Encode(iv)}.${b64Encode(ciphertext)}`;
}

Deno.test("retains AES-256-GCM known-answer vectors", async () => {
  const sealed14 = await aesGcm(
    hexDecode(
      "0000000000000000000000000000000000000000000000000000000000000000",
    ),
    hexDecode("000000000000000000000000"),
    hexDecode("00000000000000000000000000000000"),
  );
  assertEqual(
    sealed14.slice(0, -16),
    hexDecode("cea7403d4d606b6e074ec5d3baf39d18"),
    "TC14 ciphertext",
  );
  assertEqual(
    sealed14.slice(-16),
    hexDecode("d0d1c8a799996bf0265b98b5d48ab919"),
    "TC14 tag",
  );

  const sealed16 = await aesGcm(
    hexDecode(
      "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308",
    ),
    hexDecode("cafebabefacedbaddecaf888"),
    hexDecode(
      "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
        "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39",
    ),
    hexDecode("feedfacedeadbeeffeedfacedeadbeefabaddad2"),
  );
  assertEqual(
    sealed16.slice(-16),
    hexDecode("76fc6ece0f4e1768cddf8853bb2d551b"),
    "TC16 tag",
  );
});

Deno.test("encrypts v2 and decrypts with active or retired key metadata", async () => {
  const active = keyBytes(32);
  const old = keyBytes(64);
  const legacy = keyBytes(96);
  const codec = createProviderConfigCrypto({
    activeKeyId: "k2",
    encodedKeys: keyring(active, old),
    legacyEncodedKey: b64Encode(legacy),
  });
  const config = { api_key: "fixture-secret" };
  const blob = await codec.encrypt("user-a", "artwork.fixture_art", config);
  assert(blob.startsWith("v2.k2."), "new ciphertext must use active v2 key");
  const recovered = await codec.decrypt("user-a", "artwork.fixture_art", blob);
  assert(
    JSON.stringify(recovered.config) === JSON.stringify(config),
    "v2 roundtrip mismatch",
  );
  assert(
    recovered.keyId === "k2" && !recovered.needsRotation,
    "active v2 metadata mismatch",
  );

  const oldCodec = createProviderConfigCrypto({
    activeKeyId: "k1",
    encodedKeys: keyring(active, old),
    legacyEncodedKey: b64Encode(legacy),
  });
  const oldBlob = await oldCodec.encrypt(
    "user-a",
    "artwork.fixture_art",
    config,
  );
  const oldResult = await codec.decrypt(
    "user-a",
    "artwork.fixture_art",
    oldBlob,
  );
  assert(
    oldResult.keyId === "k1" && oldResult.needsRotation,
    "retired v2 metadata mismatch",
  );
});

Deno.test("decrypts legacy v1 and marks it for rotation", async () => {
  const active = keyBytes(32);
  const legacy = keyBytes(96);
  const codec = createProviderConfigCrypto({
    activeKeyId: "k2",
    encodedKeys: { k2: b64Encode(active) },
    legacyEncodedKey: b64Encode(legacy),
  });
  const blob = await legacyBlob(
    legacy,
    "user-a",
    "artwork.fixture_art",
    JSON.stringify({ api_key: "fixture-secret" }),
  );
  const result: DecryptedProviderConfig = await codec.decrypt(
    "user-a",
    "artwork.fixture_art",
    blob,
  );
  assert(
    JSON.stringify(result.config) ===
      JSON.stringify({ api_key: "fixture-secret" }),
    "legacy JSON mismatch",
  );
  assert(
    result.keyId === "legacy" && result.needsRotation,
    "legacy metadata mismatch",
  );
});

Deno.test("rejects malformed configuration and authenticated tampering", async () => {
  const active = keyBytes(32);
  const codec = createProviderConfigCrypto({
    activeKeyId: "k2",
    encodedKeys: { k2: b64Encode(active) },
  });
  const blob = await codec.encrypt("user-a", "artwork.fixture_art", {
    api_key: "fixture-secret",
  });

  await expectThrows(
    "AAD binding: different user rejected",
    () => codec.decrypt("user-b", "artwork.fixture_art", blob),
  );
  await expectThrows(
    "AAD binding: different provider rejected",
    () => codec.decrypt("user-a", "artwork.tmdb", blob),
  );
  const parts = blob.split(".");
  const ciphertext = Uint8Array.from(
    atob(parts[3]),
    (character) => character.charCodeAt(0),
  );
  ciphertext[0] ^= 1;
  parts[3] = b64Encode(ciphertext);
  await expectThrows(
    "tampered ciphertext rejected",
    () => codec.decrypt("user-a", "artwork.fixture_art", parts.join(".")),
  );
  await expectThrows(
    "unknown v2 key ID rejected",
    () =>
      codec.decrypt(
        "user-a",
        "artwork.fixture_art",
        blob.replace("v2.k2.", "v2.k3."),
      ),
  );
  await expectThrows("malformed JSON rejected", async () => {
    const malformed = await legacyBlob(
      active,
      "user-a",
      "artwork.fixture_art",
      "not-json",
    );
    return codec.decrypt("user-a", "artwork.fixture_art", malformed);
  });

  expectThrowsSync(
    "missing active key rejected",
    () => createProviderConfigCrypto({ activeKeyId: "k2", encodedKeys: {} }),
  );
  expectThrowsSync(
    "malformed active key ID rejected",
    () => createProviderConfigCrypto({ activeKeyId: "K2", encodedKeys: {} }),
  );
  expectThrowsSync(
    "malformed keyring JSON rejected",
    () => createProviderConfigCrypto({ activeKeyId: "k2", encodedKeys: "[]" }),
  );
  expectThrowsSync(
    "duplicate key ID rejected",
    () =>
      createProviderConfigCrypto({
        activeKeyId: "k2",
        encodedKeys: `{"k2":"${b64Encode(active)}","k2":"${
          b64Encode(active)
        }"}`,
      }),
  );
  expectThrowsSync(
    "malformed key base64 rejected",
    () =>
      createProviderConfigCrypto({
        activeKeyId: "k2",
        encodedKeys: { k2: "%%%" },
      }),
  );
  expectThrowsSync(
    "wrong key length rejected",
    () =>
      createProviderConfigCrypto({
        activeKeyId: "k2",
        encodedKeys: { k2: b64Encode(new Uint8Array(16)) },
      }),
  );
});
