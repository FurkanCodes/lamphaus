import {
  FCM_SCOPE,
  isFresh,
  mintAccessToken,
  parseServiceAccount,
  REFRESH_MARGIN_SECONDS,
  signedAssertion,
} from "../functions/refresh-push-access/fcm_access.ts";

function assertEquals<T>(actual: T, expected: T, message = ""): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message} expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

function decodeSegment(segment: string): Record<string, unknown> {
  const padded = segment.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(segment.length / 4) * 4, "=");
  return JSON.parse(atob(padded));
}

function base64UrlToBytes(segment: string): Uint8Array<ArrayBuffer> {
  const padded = segment.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(segment.length / 4) * 4, "=");
  const binary = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function testAccount() {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  let binary = "";
  for (const byte of pkcs8) binary += String.fromCharCode(byte);
  const body = btoa(binary).match(/.{1,64}/g)!.join("\n");
  // Service-account files carry the key with escaped newlines inside JSON.
  const privateKey = `-----BEGIN PRIVATE KEY-----\n${body}\n-----END PRIVATE KEY-----\n`;
  const raw = JSON.stringify({
    type: "service_account",
    project_id: "lamphaus-test",
    client_email: "sync@lamphaus-test.iam.gserviceaccount.com",
    private_key: privateKey,
    token_uri: "https://oauth2.googleapis.com/token",
  });
  return { raw, publicKey: pair.publicKey };
}

Deno.test("an unusable service account disables push without failing", () => {
  assertEquals(parseServiceAccount(undefined), null);
  assertEquals(parseServiceAccount("not json"), null);
  assertEquals(parseServiceAccount(JSON.stringify({ project_id: "p", client_email: "e" })), null);
});

Deno.test("a token is kept until it nears expiry", () => {
  const now = Date.UTC(2026, 9, 10, 12, 0, 0);
  assertEquals(isFresh(null, now), false);
  assertEquals(isFresh("garbage", now), false);
  assertEquals(isFresh(new Date(now + 3600_000).toISOString(), now), true);
  assertEquals(isFresh(new Date(now + (REFRESH_MARGIN_SECONDS - 60) * 1000).toISOString(), now), false);
});

Deno.test("the assertion is a verifiable RS256 JWT scoped to messaging only", async () => {
  const { raw, publicKey } = await testAccount();
  const account = parseServiceAccount(raw)!;
  const jwt = await signedAssertion(account, 1_800_000_000);
  const [header, claims, signature] = jwt.split(".");
  assertEquals(decodeSegment(header), { alg: "RS256", typ: "JWT" });
  assertEquals(decodeSegment(claims), {
    iss: "sync@lamphaus-test.iam.gserviceaccount.com",
    scope: FCM_SCOPE,
    aud: "https://oauth2.googleapis.com/token",
    iat: 1_800_000_000,
    exp: 1_800_003_600,
  });
  const valid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    publicKey,
    base64UrlToBytes(signature),
    new TextEncoder().encode(`${header}.${claims}`),
  );
  assertEquals(valid, true);
});

Deno.test("the exchange records when the token expires and fails closed", async () => {
  const { raw } = await testAccount();
  const account = parseServiceAccount(raw)!;
  const now = Date.UTC(2026, 9, 10, 12, 0, 0);
  let sentGrant = "";
  const ok = await mintAccessToken(account, now, async (_url, init) => {
    sentGrant = new URLSearchParams(String(init?.body)).get("grant_type") ?? "";
    return new Response(JSON.stringify({ access_token: "ya29.test", expires_in: 3599 }));
  });
  assertEquals(sentGrant, "urn:ietf:params:oauth:grant-type:jwt-bearer");
  assertEquals(ok, { accessToken: "ya29.test", expiresAt: new Date(now + 3599_000).toISOString() });

  let failed = "";
  await mintAccessToken(account, now, async () => new Response("{}", { status: 400 }))
    .catch((error: Error) => failed = error.message);
  assertEquals(failed, "token_exchange_failed_400");
});
