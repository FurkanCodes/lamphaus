import {createHash, randomBytes} from "node:crypto";
import {KeyManagementServiceClient} from "@google-cloud/kms";
import {initializeApp} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";
import {FieldValue, Timestamp, getFirestore} from "firebase-admin/firestore";
import {HttpsError, onCall} from "firebase-functions/v2/https";

initializeApp();

const db = getFirestore();
const kms = new KeyManagementServiceClient();
const pairingLifetimeMs = 5 * 60 * 1000;
const enforceAppCheck = process.env.ENFORCE_APP_CHECK === "true";

function requireAuth(uid: string | undefined): string {
  if (!uid) throw new HttpsError("unauthenticated", "Sign in is required.");
  return uid;
}

function cleanString(value: unknown, max: number): string {
  if (typeof value !== "string") throw new HttpsError("invalid-argument", "A required value is missing.");
  const result = value.trim().slice(0, max);
  if (!result) throw new HttpsError("invalid-argument", "A required value is empty.");
  return result;
}

function codeHash(code: string): string {
  return createHash("sha256").update(code.toUpperCase()).digest("hex");
}

function requestKey(ip: string | undefined): string {
  return createHash("sha256").update(ip ?? "unknown").digest("hex").slice(0, 32);
}

async function rateLimit(ip: string | undefined): Promise<void> {
  const ref = db.collection("pairingRateLimits").doc(requestKey(ip));
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const now = Date.now();
    const data = snapshot.data();
    const resetAt = data?.resetAt?.toMillis?.() ?? 0;
    const count = resetAt > now ? Number(data?.count ?? 0) : 0;
    if (count >= 10) throw new HttpsError("resource-exhausted", "Try again in a minute.");
    transaction.set(ref, {
      count: count + 1,
      resetAt: Timestamp.fromMillis(resetAt > now ? resetAt : now + 60_000),
    });
  });
}

export const createPairingSession = onCall({enforceAppCheck}, async (request) => {
  await rateLimit(request.rawRequest.ip);
  const sessionId = randomBytes(24).toString("base64url");
  const shortCode = randomBytes(4).toString("hex").slice(0, 6).toUpperCase();
  const expiresAtEpochMillis = Date.now() + pairingLifetimeMs;
  await db.collection("pairingSessions").doc(sessionId).create({
    codeHash: codeHash(shortCode),
    deviceLabel: cleanString(request.data?.deviceLabel ?? "Television", 80),
    createdAt: FieldValue.serverTimestamp(),
    expiresAt: Timestamp.fromMillis(expiresAtEpochMillis),
    claimedBy: null,
    exchanged: false,
  });
  return {
    sessionId,
    shortCode,
    qrPayload: `lamphaus://pair?code=${encodeURIComponent(shortCode)}`,
    expiresAtEpochMillis,
  };
});

export const claimPairingSession = onCall({enforceAppCheck}, async (request) => {
  const uid = requireAuth(request.auth?.uid);
  await rateLimit(request.rawRequest.ip);
  const shortCode = cleanString(request.data?.shortCode, 8).toUpperCase();
  const matches = await db.collection("pairingSessions").where("codeHash", "==", codeHash(shortCode)).limit(1).get();
  if (matches.empty) throw new HttpsError("not-found", "Pairing code not found.");
  const ref = matches.docs[0].ref;
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const data = snapshot.data();
    if (!data || data.expiresAt.toMillis() <= Date.now()) throw new HttpsError("deadline-exceeded", "Pairing code expired.");
    if (data.claimedBy) throw new HttpsError("already-exists", "Pairing code was already used.");
    transaction.update(ref, {claimedBy: uid, claimedAt: FieldValue.serverTimestamp()});
  });
  return {ok: true};
});

export const exchangeDeviceGrant = onCall({enforceAppCheck}, async (request) => {
  await rateLimit(request.rawRequest.ip);
  const sessionId = cleanString(request.data?.sessionId, 80);
  const ref = db.collection("pairingSessions").doc(sessionId);
  const deviceId = randomBytes(18).toString("base64url");
  const uid = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const data = snapshot.data();
    if (!data || data.expiresAt.toMillis() <= Date.now()) throw new HttpsError("deadline-exceeded", "Pairing session expired.");
    if (!data.claimedBy) throw new HttpsError("failed-precondition", "Pairing session has not been claimed.");
    if (data.exchanged) throw new HttpsError("already-exists", "Pairing grant was already exchanged.");
    transaction.update(ref, {exchanged: true, exchangedAt: FieldValue.serverTimestamp(), deviceId});
    return String(data.claimedBy);
  });
  await db.doc(`users/${uid}/devices/${deviceId}`).set({
    label: "Television",
    platform: "android-tv",
    revoked: false,
    createdAt: FieldValue.serverTimestamp(),
    lastSeenAt: FieldValue.serverTimestamp(),
  });
  const customToken = await getAuth().createCustomToken(uid, {deviceId, television: true});
  return {customToken, deviceId};
});

export const revokeDevice = onCall({enforceAppCheck}, async (request) => {
  const uid = requireAuth(request.auth?.uid);
  const deviceId = cleanString(request.data?.deviceId, 80);
  await db.doc(`users/${uid}/devices/${deviceId}`).set({
    revoked: true,
    revokedAt: FieldValue.serverTimestamp(),
  }, {merge: true});
  return {ok: true};
});

async function encrypt(value: string): Promise<string> {
  const name = process.env.KMS_KEY_NAME;
  if (!name) throw new HttpsError("failed-precondition", "Provider encryption is not configured.");
  const [response] = await kms.encrypt({name, plaintext: Buffer.from(value, "utf8")});
  return Buffer.from(response.ciphertext as Uint8Array).toString("base64");
}

async function decrypt(value: string): Promise<string> {
  const name = process.env.KMS_KEY_NAME;
  if (!name) throw new HttpsError("failed-precondition", "Provider encryption is not configured.");
  const [response] = await kms.decrypt({name, ciphertext: Buffer.from(value, "base64")});
  return Buffer.from(response.plaintext as Uint8Array).toString("utf8");
}

export const saveProviderConfiguration = onCall({enforceAppCheck}, async (request) => {
  const uid = requireAuth(request.auth?.uid);
  const providerId = cleanString(request.data?.providerId, 160);
  const manifestUrl = cleanString(request.data?.manifestUrl, 4096);
  if (!manifestUrl.startsWith("https://")) throw new HttpsError("invalid-argument", "HTTPS is required.");
  await db.doc(`users/${uid}/providers/${providerId}`).set({
    displayName: cleanString(request.data?.displayName, 120),
    encryptedConfiguration: await encrypt(JSON.stringify({manifestUrl})),
    enabled: request.data?.enabled !== false,
    sortOrder: Number(request.data?.sortOrder ?? 0),
    updatedAt: FieldValue.serverTimestamp(),
  });
  return {ok: true};
});

export const listProviderConfigurations = onCall({enforceAppCheck}, async (request) => {
  const uid = requireAuth(request.auth?.uid);
  const snapshots = await db.collection(`users/${uid}/providers`).orderBy("sortOrder").get();
  return {
    providers: await Promise.all(snapshots.docs.map(async (snapshot) => {
      const data = snapshot.data();
      const configuration = JSON.parse(await decrypt(String(data.encryptedConfiguration))) as {manifestUrl: string};
      return {
        providerId: snapshot.id,
        displayName: data.displayName,
        manifestUrl: configuration.manifestUrl,
        enabled: data.enabled,
        sortOrder: data.sortOrder,
        updatedAtEpochMillis: data.updatedAt?.toMillis?.() ?? 0,
      };
    })),
  };
});

export const deleteProviderConfiguration = onCall({enforceAppCheck}, async (request) => {
  const uid = requireAuth(request.auth?.uid);
  const providerId = cleanString(request.data?.providerId, 160);
  await db.doc(`users/${uid}/providers/${providerId}`).delete();
  return {ok: true};
});

export const deleteAccountData = onCall({enforceAppCheck}, async (request) => {
  const uid = requireAuth(request.auth?.uid);
  await db.recursiveDelete(db.doc(`users/${uid}`));
  await getAuth().deleteUser(uid);
  return {ok: true};
});
