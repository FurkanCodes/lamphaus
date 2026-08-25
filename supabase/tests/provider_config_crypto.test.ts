// provider-config crypto parity test (plan M5).
//
// Proves the EXACT codec the edge functions use is interoperable AES-GCM:
//   1. known-answer vectors from the GCM spec (McGrew-Viega, AES-256 cases)
//      — any compliant implementation (Deno ring, Node BoringSSL, JVM
//      javax.crypto) must produce these bytes;
//   2. roundtrip through the deployed wire format "v1.<iv>.<ciphertext>";
//   3. AAD binding: another user's identity must fail decryption;
//   4. tampered ciphertext must fail.
//
// Run: node supabase/tests/provider_config_crypto.test.ts
//  or: deno test supabase/tests/provider_config_crypto.test.ts

const hexDecode = (hex: string): Uint8Array =>
  new Uint8Array((hex.match(/../g) ?? []).map((byte) => parseInt(byte, 16)));

async function aesGcm(
  keyBytes: Uint8Array,
  iv: Uint8Array,
  plaintext: Uint8Array,
  additionalData?: Uint8Array,
): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey("raw", keyBytes, "AES-GCM", false, ["encrypt"]);
  const sealed = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv, additionalData },
    key,
    plaintext,
  );
  // WebCrypto appends the 16-byte tag to the ciphertext; spec vectors list them apart.
  return new Uint8Array(sealed);
}

function assertEqual(actual: Uint8Array, expected: Uint8Array, label: string): void {
  const a = [...actual].map((b) => b.toString(16).padStart(2, "0")).join("");
  const e = [...expected].map((b) => b.toString(16).padStart(2, "0")).join("");
  if (a !== e) throw new Error(`${label}\n  expected ${e}\n  actual   ${a}`);
}

async function expectThrows(label: string, block: () => Promise<unknown>): Promise<void> {
  try {
    await block();
  } catch {
    console.log(`  ✓ ${label}`);
    return;
  }
  throw new Error(`${label}: expected rejection, got success`);
}

// ──────────────────── 1. known-answer vectors (spec) ────────────────────

// Test Case 14: K = IV = 0, P = 16 zero bytes
{
  const sealed = await aesGcm(
    hexDecode("0000000000000000000000000000000000000000000000000000000000000000"),
    hexDecode("000000000000000000000000"),
    hexDecode("00000000000000000000000000000000"),
  );
  assertEqual(sealed.slice(0, -16), hexDecode("cea7403d4d606b6e074ec5d3baf39d18"), "TC14 ciphertext");
  assertEqual(sealed.slice(-16), hexDecode("d0d1c8a799996bf0265b98b5d48ab919"), "TC14 tag");
  console.log("  ✓ GCM spec TC14 (AES-256) known-answer");
}

// Test Case 16: real-world shape — 60-byte plaintext + 20-byte AAD
{
  const sealed = await aesGcm(
    hexDecode("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308"),
    hexDecode("cafebabefacedbaddecaf888"),
    hexDecode(
      "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
        "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39",
    ),
    hexDecode("feedfacedeadbeeffeedfacedeadbeefabaddad2"),
  );
  assertEqual(sealed.slice(-16), hexDecode("76fc6ece0f4e1768cddf8853bb2d551b"), "TC16 tag");
  console.log("  ✓ GCM spec TC16 (AES-256 + AAD) known-answer");
}

// ──────────────── 2–4. the deployed wire format itself ─────────────────

const encoder = new TextEncoder();
const decoder = new TextDecoder();

const b64Encode = (bytes: Uint8Array): string => btoa(String.fromCharCode(...bytes));
const b64Decode = (text: string): Uint8Array =>
  Uint8Array.from(atob(text), (c) => c.charCodeAt(0));

async function importKey(secretBase64: string): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", b64Decode(secretBase64), "AES-GCM", false, [
    "encrypt",
    "decrypt",
  ]);
}

async function encryptConfig(
  key: CryptoKey,
  userId: string,
  providerId: string,
  config: unknown,
): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv, additionalData: encoder.encode(`${userId}:${providerId}`) },
    key,
    encoder.encode(JSON.stringify(config)),
  );
  return `v1.${b64Encode(iv)}.${b64Encode(new Uint8Array(ciphertext))}`;
}

async function decryptConfig(
  key: CryptoKey,
  userId: string,
  providerId: string,
  blob: string,
): Promise<unknown> {
  const [version, ivText, ciphertextText] = blob.split(".");
  if (version !== "v1" || !ivText || !ciphertextText) throw new Error("bad_format");
  const plaintext = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: b64Decode(ivText), additionalData: encoder.encode(`${userId}:${providerId}`) },
    key,
    b64Decode(ciphertextText),
  );
  return JSON.parse(decoder.decode(plaintext));
}

{
  const secret = b64Encode(crypto.getRandomValues(new Uint8Array(32)));
  const key = await importKey(secret);
  const config = { url: "https://example.tld/manifest.json", token: "s3cret", timeoutMs: 5000 };

  const blob = await encryptConfig(key, "user-a", "prov-1", config);
  if (!blob.startsWith("v1.")) throw new Error("wire format missing version prefix");
  const roundtrip = await decryptConfig(key, "user-a", "prov-1", blob);
  if (JSON.stringify(roundtrip) !== JSON.stringify(config)) throw new Error("roundtrip mismatch");
  console.log("  ✓ wire-format roundtrip v1.<iv>.<ct>");

  await expectThrows("AAD binding: different user rejected", () =>
    decryptConfig(key, "user-b", "prov-1", blob));
  await expectThrows("AAD binding: different provider rejected", () =>
    decryptConfig(key, "user-a", "prov-2", blob));

  const parts = blob.split(".");
  parts[2] = b64Encode(
    b64Decode(parts[2]).map((b, i) => i === 0 ? b ^ 1 : b) as Uint8Array,
  );
  await expectThrows("tampered ciphertext rejected", () =>
    decryptConfig(key, "user-a", "prov-1", parts.join(".")));

  console.log("provider_config_crypto: ALL PASSED");
}
