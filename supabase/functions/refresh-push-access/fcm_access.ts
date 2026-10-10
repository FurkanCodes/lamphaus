// Firebase Cloud Messaging access for the sync signal (push-to-pull sync).
//
// Lives beside index.ts because the platform's remote bundler only sees the
// function's own directory. Pure helpers, so supabase/tests can pin them.

export const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
export const GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";

/** A token this close to expiry is replaced; the cron runs every 30 minutes. */
export const REFRESH_MARGIN_SECONDS = 45 * 60;

export interface ServiceAccount {
  projectId: string;
  clientEmail: string;
  privateKey: string;
  tokenUri: string;
}

/** The fields we need from a Firebase service-account JSON key, or null when it is unusable. */
export function parseServiceAccount(raw: string | undefined | null): ServiceAccount | null {
  if (!raw) return null;
  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }
  const projectId = parsed.project_id;
  const clientEmail = parsed.client_email;
  const privateKey = parsed.private_key;
  const tokenUri = parsed.token_uri;
  if (typeof projectId !== "string" || !projectId) return null;
  if (typeof clientEmail !== "string" || !clientEmail) return null;
  if (typeof privateKey !== "string" || !privateKey.includes("PRIVATE KEY")) return null;
  return {
    projectId,
    clientEmail,
    privateKey,
    tokenUri: typeof tokenUri === "string" && tokenUri ? tokenUri : GOOGLE_TOKEN_URI,
  };
}

/** True when the stored token stays valid past the refresh margin. */
export function isFresh(expiresAt: string | null | undefined, nowMillis: number): boolean {
  if (!expiresAt) return false;
  const expires = Date.parse(expiresAt);
  if (Number.isNaN(expires)) return false;
  return expires - nowMillis > REFRESH_MARGIN_SECONDS * 1000;
}

function base64Url(bytes: Uint8Array<ArrayBuffer>): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlJson(value: unknown): string {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}

function pemToDer(pem: string): Uint8Array<ArrayBuffer> {
  const body = pem
    .replace(/-----BEGIN [A-Z ]+-----/g, "")
    .replace(/-----END [A-Z ]+-----/g, "")
    .replace(/\\n/g, "")
    .replace(/\s+/g, "");
  const binary = atob(body);
  const der = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i++) der[i] = binary.charCodeAt(i);
  return der;
}

/** The signed JWT Google exchanges for an hour-long access token (RFC 7523). */
export async function signedAssertion(account: ServiceAccount, nowSeconds: number): Promise<string> {
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: account.clientEmail,
    scope: FCM_SCOPE,
    aud: account.tokenUri,
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  };
  const unsigned = `${base64UrlJson(header)}.${base64UrlJson(claims)}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(account.privateKey),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  return `${unsigned}.${base64Url(new Uint8Array(signature))}`;
}

export interface AccessToken {
  accessToken: string;
  expiresAt: string;
}

/** Exchanges the service account for a messaging access token. */
export async function mintAccessToken(
  account: ServiceAccount,
  nowMillis: number,
  fetcher: typeof fetch = fetch,
): Promise<AccessToken> {
  const assertion = await signedAssertion(account, Math.floor(nowMillis / 1000));
  const res = await fetcher(account.tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!res.ok) throw new Error(`token_exchange_failed_${res.status}`);
  const body = await res.json();
  const accessToken = body?.access_token;
  const expiresIn = Number(body?.expires_in ?? 3600);
  if (typeof accessToken !== "string" || !accessToken) throw new Error("token_exchange_empty");
  return {
    accessToken,
    expiresAt: new Date(nowMillis + expiresIn * 1000).toISOString(),
  };
}
