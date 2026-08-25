# Lamphaus Authentication System — Deep Analysis

Date: 2026-08-25 · Branch: `main` @ `5a34c51`

---

## 1. Executive Summary

Lamphaus uses **Firebase Auth** behind a clean gateway abstraction, with three distinct identity surfaces:

1. **Mobile** — Google Sign-In (Credential Manager ID token) + email-link (magic link)
2. **TV** — no direct login; designed to pair with a signed-in phone via short code / QR
3. **Devices** — paired TVs receive a Firebase *custom token* carrying `deviceId` + `television` claims; every Firestore rule re-checks device revocation

The abstraction layer (`AccountGateway` / `PairingGateway` / `CloudSyncGateway` with `Firebase*` and `Local*` implementations) is solid and will make any backend swap tractable.

**Critical finding:** the TV pairing flow is **incomplete end-to-end** — the backend exists and works, the mobile side works, but the TV app **never calls `exchangeDeviceGrant`** (and there's no polling loop), so a paired TV can never actually sign in. `revokeDevice` also has no UI anywhere.

**Context:** the new Supabase project (`uhxfalgfcutwrvlgjgen`, created today) is completely empty — 0 users, no migrations, no public tables. This analysis is written so it can double as the baseline for a possible Firebase → Supabase auth migration.

---

## 2. Architecture Map

| Layer | File(s) | Role |
|---|---|---|
| Contract | `core/data/src/main/kotlin/com/lamphaus/core/data/cloud/AccountGateway.kt` | `AccountState` (Loading/SignedOut/SignedIn), `AccountGateway`, `PairingGateway` |
| Firebase impl | `core/data/.../cloud/FirebaseGateways.kt` | `FirebaseAccountGateway`, `FirebasePairingGateway` |
| Local stubs | `core/data/.../cloud/LocalGateways.kt` | Offline/dev fallbacks; all cloud ops fail with `CloudNotConfiguredException`; `openDevelopmentSession()` dev backdoor |
| Cloud sync | `core/data/.../cloud/FirebaseCloudSyncGateway.kt` | Firestore JSON payload docs + Functions calls for providers |
| DI wiring | `app/src/main/kotlin/com/lamphaus/app/AppContainer.kt` | Chooses Firebase vs Local via `BuildConfig.CLOUD_CONFIGURED` |
| ViewModel | `app/src/main/kotlin/com/lamphaus/app/ui/AppViewModel.kt` | `signInWithGoogleToken`, `sendEmailLink`, `completeEmailLink`, `createPairingSession`, `claimPairingSession`, `openDevelopmentSession` |
| Mobile UI/activity | `app/.../mobile/MobileActivity.kt`, `MobileApp.kt` | Credential Manager flow, email-link deep-link handling, pairing-claim deep links |
| TV UI | `app/.../tv/TvApp.kt` | `TvPairingScreen` (shows code + QR, refresh, dev-session action) |
| Backend | `functions/src/index.ts` | Pairing, device grants, provider config CRUD (KMS-encrypted), account deletion |
| Rules | `firestore.rules` | Owner-only data + per-request device-revocation checks |

### Build-time gates (`app/build.gradle.kts`)
- `CLOUD_CONFIGURED` (boolean, from Gradle property) — flips entire cloud stack on/off
- `EMAIL_LINK_DOMAIN` (default `links.lamphaus.app`)
- `WEB_CLIENT_ID` (Google OAuth web client; blank ⇒ Google sign-in reports "needs the production web client ID")
- Client deps: `firebase-auth/firestore/functions/appcheck-playintegrity/crashlytics/perf`, `androidx.credentials`, `googleid:1.1.1`

---

## 3. Sign-in Flows (as implemented)

### 3.1 Mobile — Google Sign-In ✅ complete
1. `MobileActivity.requestGoogleSignIn()` → Credential Manager `GetGoogleIdOption(serverClientId = WEB_CLIENT_ID)`, auto-select enabled
2. ID token extracted from `GoogleIdTokenCredential` → `viewModel.signInWithGoogleToken(token)`
3. `FirebaseAccountGateway.signInWithGoogleIdToken` → `signInWithCredential(GoogleAuthProvider.getCredential(...))`
4. `AuthStateListener` → `AccountState.SignedIn(uid, displayName, email)` → triggers profile bootstrap + `startCloudSync`
   - Auto-creates initial profile if none; ensures default catalog

### 3.2 Mobile — Email link ✅ complete (with caveats)
1. `sendEmailLink(email)` → FDL-style settings: url `https://{domain}/__/auth/links?email=…`, `handleCodeInApp=true`, Android package `com.lamphaus.app` (min v23)
2. Pending email cached in `SharedPreferences("pending_auth")` (plaintext)
3. Return path: `https://links.lamphaus.app` App Links intent-filter (`autoVerify`) → `handleIncomingIntent` → `finishPendingEmailLink()` → `completeEmailLink(email, link)`
4. `auth.isSignInWithEmailLink` guard before consuming

### 3.3 TV pairing ⚠️ half-implemented (the big one)

**Backend design (functions/index.ts):**
| Function | Auth | Controls |
|---|---|---|
| `createPairingSession` | none needed | IP rate limit 10/min; random 24-byte sessionId; 6-hex-char code stored as SHA-256; 5-min TTL; QR `lamphaus://pair?code={code}` |
| `claimPairingSession` | requires uid | Transactional claim by codeHash lookup; single-use |
| `exchangeDeviceGrant` | none (TV unauthenticated) | Requires claimed+unexchanged+unexpired session; one-time; creates `users/{uid}/devices/{deviceId}` doc; returns `createCustomToken(uid, {deviceId, television:true})` |
| `revokeDevice` | requires uid | Sets `revoked:true` on device doc |

**Mobile side ✅:** QR/custom-scheme deep link (`lamphaus://pair`) → `claimPairingSession(code)` with signed-in uid.

**TV side ❌:** `TvPairingScreen` creates the session, renders shortCode + expiry, offers Refresh — and then… nothing. Grep across `app/src` finds **zero** references to `exchangeDeviceGrant` / `signInWithCustomToken`. There is no polling loop, so after the phone claims the code the TV just sits there until the code expires. **A TV cannot currently sign in at all.**

### 3.4 Device trust model (designed, partially reachable)
- Custom token claims: `{deviceId, television: true}`
- Every Firestore rule call evaluates `activeDevice(uid)`: reads `users/{uid}/devices/{request.auth.token.deviceId}` and requires `revoked == false`
- Users without `deviceId` claim (phones) bypass the device check
- Device docs are client-read-only (`allow write: if false`) — revocation writes happen only through the Function

### 3.5 Account deletion ✅
`deleteAccountData` → recursive delete of `users/{uid}` + `getAuth().deleteUser(uid)`.

---

## 4. Firestore Rules Analysis

```
validOwner(uid) := request.auth.uid == uid AND activeDevice(uid)
users/{uid} .................................. read/write: validOwner
users/{uid}/profiles/{p}/library|progress .... read/write: validOwner
users/{uid}/devices/{d} ...................... read: validOwner · write: denied
users/{uid}/providers/{p} .................... fully denied (Functions-only)
pairingSessions, pairingRateLimits ........... fully denied
catch-all {document=**} ...................... fully denied
```

Strengths: default-deny catch-all; provider secrets never client-readable; hashed pairing codes; server timestamps everywhere.

Weaknesses:
- **`activeDevice()` performs a `get()` per rule evaluation** — an extra billed read + latency hit on *every* Firestore operation for TV devices (and rules don't cache across documents in a batch write).
- Revocation is enforced only at the rules layer; the custom token itself remains valid (~1 h) until refresh — acceptable given the rules check, but worth remembering during migration.
- `users/{uid}` root doc is writable by owner (fine) — no validation schema on writes.

## 5. Findings (severity-ordered)

| # | Sev | Finding |
|---|-----|---------|
| 1 | **P0** | **TV cannot complete pairing** — `exchangeDeviceGrant` never called; no polling/exchange loop; `signInWithCustomToken` unused. Feature is dead-ended after mobile claims the code. |
| 2 | **P1** | **No device management surface** — `revokeDevice` isn't wired to any UI; users can't see/unrevoke/revoke paired TVs. |
| 3 | **P1** | `exchangeDeviceGrant` trusts bearer knowledge of `sessionId` (24-byte URL-safe random — strong, OK) with one-time exchange + rate limit; acceptable, but there's no binding that the exchanger saw the QR (vs. sniffed the callable args). Documented risk trade-off. |
| 4 | **P2** | Per-access device `get()` in rules → cost/latency tax on every TV op. |
| 5 | **P2** | Email-link pending email in plaintext SharedPreferences (low sensitivity, but trivially encryped or moved to session state). |
| 6 | **P2** | `AccountState` has no error state — auth failures degrade to transient snackbar strings; TV users get no retry affordance beyond Refresh. |
| 7 | **P2** | Deleting an auth user doesn't invalidate outstanding tokens (standard caveat); mitigated here by rules-level revoked checks for devices. |
| 8 | **P3** | QR payload drift: Function emits `lamphaus://pair?code=X`, Local stub emits `lamphaus://pair/{id}` — mobile handles both, but contract should be pinned. |
| 9 | **P3** | App Check enforced only when `ENFORCE_APP_CHECK=true`; client dep present. Rollout documented in `docs/PRODUCTION_SETUP.md`. |
| 10 | **P3** | Dev backdoor (`openDevelopmentSession`) properly guarded by `BuildConfig.DEBUG` + non-cloud builds only. Good. |

---

## 6. Supabase Project State (connected today)

Project `lamphaus` (`uhxfalgfcutwrvlgjgen`, eu-central-1):
- **Migrations applied:** 0
- **Public tables:** none
- **Auth users:** 0
- Stock `auth.*` schema only

Clean slate — nothing to reconcile or migrate data-wise yet.

## 7. Migration Mapping (if/when we move auth to Supabase)

| Firebase today | Supabase equivalent | Notes |
|---|---|---|
| Google ID token via Credential Manager | `signIn(idToken: { provider: 'google', token })` (Android SDK `signInWithIdToken`) | Keep Credential Manager untouched; add nonce support |
| Email link (`sendSignInLinkToEmail`) | Magic Link / OTP (`signInWithOTP`) | Different return-token mechanics (token_hash / PKCE deep link); `links.lamphaus.app` handler needs rework |
| `createCustomToken(uid, {deviceId, television})` | Options: (a) Anonymous sign-in + `devices` table row + RLS; (b) Edge Function minting signed JWT (HS256/EdDSA secret in Vault); (c) `app_metadata.device_id` claim set server-side | Option (a) is simplest and keeps revocation in SQL; put authorization data in **`app_metadata`, never `user_metadata`** |
| Firestore rules `activeDevice()` get-per-read | Single SQL policy join against `public.devices` | Removes finding #4; remember UPDATE policies need SELECT policies too |
| Firestore snapshot listeners | Postgres Changes / Realtime | Payload-docs pattern maps naturally to typed tables |
| Cloud Functions (KMS encrypt/decrypt) | Edge Functions (+ Vault/pgsodium for provider config secrets) | |
| App Check (Play Integrity) | No direct equivalent — consider CAPTCHA bot-protection on auth endpoints + rate limits | |
| `deleteUser` | `admin.deleteUser` via service role in Edge Function | Remember: deleting ≠ revoking sessions; revoke sessions first |

Security checklist that must carry over: RLS enabled on every public table before exposing; no `security definer` in exposed schemas; views `security_invoker`; JWT claim freshness (tokens refresh lazily).

## 8. Suggested Order of Work (for discussion — not started)

1. **Decide target**: finish Firebase pairing (ship faster) vs. migrate auth to Supabase now (single backend, you already provisioned it).
2. Either way, **close P0**: implement TV-side polling + `exchangeDeviceGrant` + custom-token (or Supabase-equivalent) sign-in, with expiry countdown UX already present.
3. Add device management (list/revoke) to mobile settings.
4. Then layer remaining P2 polish (error states, pending-email storage, QR payload pinning).
