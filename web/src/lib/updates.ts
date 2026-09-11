/**
 * Signed release-feed consumption for the website (plan §3/§5).
 * Mirrors scripts/release/feed.py and UpdateFeed.kt — keep all three in sync.
 *
 * The site fetches and verifies the same signed envelope at runtime; APK
 * bytes stay GitHub Release assets so promotion needs no website rebuild.
 * Without JS or successful validation, pages link the GitHub Releases page
 * rather than an invented latest-APK link.
 */

export const FEED_URL =
  "https://raw.githubusercontent.com/furkancodes/lamphaus/release-metadata/updates/v1/index.json";

export const RELEASES_PAGE = "https://github.com/furkancodes/lamphaus/releases";

export const SCHEMA_VERSION = 1;
export const MAX_PAYLOAD_BYTES = 1024 * 1024;
const EXPECTED_REPO_MARKER = "github.com/furkancodes/lamphaus/releases/download/";
const EXPECTED_PACKAGE = "com.lamphaus.app";
const REQUIRED_ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64", "x86"];
const SHA256_RE = /^[0-9a-f]{64}$/i;
const FINGERPRINT_RE = /^([0-9A-Fa-f]{2}:)+[0-9A-Fa-f]{2}$/;
const SEMVER_RE = /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/;

/** Trusted metadata keys (SPKI DER base64): primary + offline recovery. */
export const TRUSTED_KEYS: Record<string, string> = {
  "lamphaus-metadata-1":
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5k9HxpKP3WxJr8fbfd+CibyiF9l+cbaqhXRMiPE6tzdhWS5XC4zIfeIxdXebZdIPTHcObUCqwzwKqxzmiZ9w2hDuIO2SsGk2xDMuD1rkDQkDiu88KqsH0xDTTqgs8duaOaEpEAv9nznFfdK73Inf31UEKXpHQzRlJQjIjyw1HdPnkroRZeUqlM4TD2klzOu4nCsPdOZvsyTtQ45J44IiFJbknO3g3CGPTZ61O6J4IsdM07+r+jFNCnVkCQRRbVftQBQRmNbuny5aEbURwTDzHyaWvyl/NEZB6eGM2slNXOWQPCuvuSeslQ7D6Mp12ZzKMiOjv8Ey5aVOfeOntyoZ2wIDAQAB",
  "lamphaus-metadata-recovery-1":
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5bDScNmr8IEldGY53Ovgx+FSUvYwxTMCcP+D/SvKvGUXMHNkz16GWSOsLj0Rxghwr2RSp4XSeNvma1B2uVuIyt57cXeYC3ZR2AGPhzYUWtuW9EnSJLRZJ44Wa8UhuT6vDxQNFJxugi13hr4DPIiQ09OYgnN5iKNUAa/+T9FqmFj6bY51ZHWezGYSQKiF1CDavYwtogsOm/UWY5rut4QEajslImvG/mmRg5dmFvCfbGaYniwBIoblNWaPpOk4QfztVcO/vg3Zep2rlNL8UUDQD5BT7flRTmTXhB2RCkCAdIkGcWeVSxkZ817/Y3IZ113aM7vcdhMl8+4o3+MejLvrrwIDAQAB",
};

export interface FeedApk {
  packageId: string;
  url: string;
  abis: string[];
  byteLength: number;
  sha256: string;
  signerFingerprint: string;
}

export interface FeedRelease {
  channel: "beta" | "stable";
  versionCode: number;
  versionName: string;
  tag: string;
  commit: string;
  publishedAt: string;
  minSdk: number;
  changelog: string;
  releasePageUrl: string;
  apk: FeedApk;
}

export interface FeedPayload {
  schemaVersion: number;
  revision: number;
  publishedAt: string;
  releases: FeedRelease[];
  withdrawnVersionCodes: number[];
}

export type FeedStatus =
  | { kind: "ok"; payload: FeedPayload; latest: FeedRelease }
  | { kind: "empty" }
  | { kind: "error"; reason: "offline" | "invalid" | "unavailable" };

function b64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function importKey(spkiB64: string): Promise<CryptoKey> {
  const der = b64ToBytes(spkiB64);
  return crypto.subtle.importKey(
    "spki",
    der.buffer as ArrayBuffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );
}

export function validatePayload(payload: FeedPayload): string | null {
  if (payload.schemaVersion !== SCHEMA_VERSION) return "unsupported schema";
  if (!Number.isInteger(payload.revision) || payload.revision < 1) return "bad revision";
  if (!payload.publishedAt) return "missing publishedAt";
  if (!Array.isArray(payload.releases) || payload.releases.length > 64) return "bad releases";
  const codes = new Set<number>();
  const tags = new Set<string>();
  for (const rel of payload.releases) {
    const err = validateRelease(rel);
    if (err) return err;
    if (codes.has(rel.versionCode)) return "duplicate version";
    if (tags.has(rel.tag)) return "duplicate tag";
    codes.add(rel.versionCode);
    tags.add(rel.tag);
    if (payload.withdrawnVersionCodes.includes(rel.versionCode)) return "release withdrawn";
  }
  const ordered = payload.releases.map((r) => r.versionCode);
  if (JSON.stringify(ordered) !== JSON.stringify([...ordered].sort((a, b) => a - b))) {
    return "unordered releases";
  }
  return null;
}

function validateRelease(rel: FeedRelease): string | null {
  if (rel.channel !== "beta" && rel.channel !== "stable") return "bad channel";
  if (!Number.isInteger(rel.versionCode) || rel.versionCode < 1) return "bad versionCode";
  if (!SEMVER_RE.test(rel.versionName)) return "bad versionName";
  if (!rel.tag || !rel.commit || !rel.publishedAt) return "missing identity";
  if (rel.minSdk < 26) return "bad minSdk";
  if (rel.changelog.length > 8000) return "changelog too long";
  const lower = rel.changelog.toLowerCase();
  if (lower.includes("<script") || lower.includes("<img")) return "unsafe changelog";
  if (!rel.releasePageUrl) return "missing release page";
  const apk = rel.apk;
  if (apk.packageId !== EXPECTED_PACKAGE) return "wrong package";
  if (!apk.url.startsWith("https://")) return "bad apk url";
  if (!apk.url.includes(EXPECTED_REPO_MARKER)) return "apk outside repository";
  if (!SHA256_RE.test(apk.sha256)) return "bad sha256";
  if (!FINGERPRINT_RE.test(apk.signerFingerprint)) return "bad fingerprint";
  if (!Number.isInteger(apk.byteLength) || apk.byteLength < 1) return "bad byte length";
  if ([...apk.abis].sort().join(",") !== [...REQUIRED_ABIS].sort().join(",")) return "bad abis";
  return null;
}

/** Render changelog as restricted text: paragraphs + dash lists, no HTML. */
export function renderChangelog(source: string): { paragraphs: string[]; items: string[] } {
  const items: string[] = [];
  const paragraphs: string[] = [];
  for (const line of source.split("\n")) {
    const trimmed = line.trim();
    if (/^[-*]\s+/.test(trimmed)) items.push(trimmed.replace(/^[-*]\s+/, ""));
    else if (trimmed) paragraphs.push(trimmed);
  }
  return { paragraphs, items };
}

/**
 * Fetch, verify (signature over decoded bytes BEFORE parsing), and select the
 * newest non-withdrawn beta-or-stable release. Revalidates on each call —
 * cached recommendations are never followed without revalidation.
 */
export async function loadVerifiedFeed(): Promise<FeedStatus> {
  let res: Response;
  try {
    res = await fetch(FEED_URL, { headers: { Accept: "application/json" } });
  } catch {
    return { kind: "error", reason: "offline" };
  }
  if (!res.ok) {
    return res.status === 404
      ? { kind: "empty" }
      : { kind: "error", reason: "unavailable" };
  }
  let envelope: { keyId?: string; payloadBase64?: string; signatureBase64?: string };
  try {
    envelope = await res.json();
  } catch {
    return { kind: "error", reason: "invalid" };
  }
  if (!envelope.keyId || !envelope.payloadBase64 || !envelope.signatureBase64) {
    return { kind: "error", reason: "invalid" };
  }
  const spki = TRUSTED_KEYS[envelope.keyId];
  if (!spki) return { kind: "error", reason: "invalid" };
  let raw: Uint8Array;
  let sig: Uint8Array;
  try {
    raw = b64ToBytes(envelope.payloadBase64);
    sig = b64ToBytes(envelope.signatureBase64);
  } catch {
    return { kind: "error", reason: "invalid" };
  }
  if (raw.length === 0 || raw.length > MAX_PAYLOAD_BYTES) {
    return { kind: "error", reason: "invalid" };
  }
  let key: CryptoKey;
  try {
    key = await importKey(spki);
  } catch {
    return { kind: "error", reason: "invalid" };
  }
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    sig.buffer as ArrayBuffer,
    raw.buffer as ArrayBuffer,
  );
  if (!ok) return { kind: "error", reason: "invalid" };
  let payload: FeedPayload;
  try {
    payload = JSON.parse(new TextDecoder().decode(raw));
  } catch {
    return { kind: "error", reason: "invalid" };
  }
  if (validatePayload(payload)) return { kind: "error", reason: "invalid" };
  const visible = payload.releases.filter(
    (r) => !payload.withdrawnVersionCodes.includes(r.versionCode),
  );
  if (visible.length === 0) return { kind: "empty" };
  const latest = visible.reduce((a, b) => (b.versionCode > a.versionCode ? b : a));
  return { kind: "ok", payload, latest };
}

export function formatBytes(bytes: number): string {
  if (bytes <= 0) return "0 B";
  const mb = bytes / (1024 * 1024);
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`;
}
