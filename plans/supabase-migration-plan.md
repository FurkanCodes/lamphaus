# Lamphaus — Supabase Migration Plan (Master Document)

> **Single source of truth for the Firebase → Supabase migration.**
> Created 2026-08-25 · Branch: `feature/supabase-migration`
> Companion documents: `plans/auth-analysis.md` (baseline findings), `docs/PRODUCTION_SETUP.md` (rewritten at M8)

---

## 0. Goal

Replace Firebase entirely — Auth, Firestore, Cloud Functions, App Check — with Supabase:

| Firebase today | Supabase target |
|---|---|
| Firebase Auth (Google + email link + custom tokens) | GoTrue via supabase-kt — Credential Manager stays, token sink changes (`signInWith(IDToken)` + nonce) |
| Firestore payload docs + snapshot listeners | Typed Postgres tables + RLS + Postgres Changes realtime |
| Cloud Functions (pairing, devices, KMS provider CRUD) | Deno Edge Functions; KMS → AES-GCM in-function |
| App Check | Dropped — RLS + publishable-key model replaces it |
| Custom-token TV auth | Server-minted one-time tokens (`generateLink` magiclink) → TV gets a real, revocable GoTrue session |

Target project: `lamphaus` (`uhxfalgfcutwrvlgjgen`, eu-central-1).
**Product rule (owner-confirmed): pairing does NOT require the Lamphaus mobile app — any phone with a browser can pair a TV. The web app is therefore an auth surface, not just marketing.**

---

## 1. Decisions (all confirmed by owner)

### D1 — minSdk raised 23 → 26 ✅
supabase-kt requires SDK 26. Clean bump, no desugaring. Drops pre-Android-8 devices (accepted). Lint fallout cleaned in M0.

### D2 — TV pairing: server-minted one-time tokens ✅
Phone/web claims code → Edge Function calls `admin.generateLink(magiclink, claimer.email)` → TV polls `exchange-device-grant`, receives one-time `hashed_token`, exchanges at `/auth/v1/verify` → real session with standard refresh rotation.

### D3 — Device identity & revocation (REVISED during architecture review)
No device claims inside JWTs (`app_metadata.device_id` breaks multi-TV households — user-level field).
- `public.devices` row per paired TV incl. `auth_session_id` — the exact GoTrue session the TV obtained at pairing.
- Revocation = mark `revoked=true` **and** delete that session (Admin API) → TV's next refresh fails → QR screen.
- RLS stays pure ownership (`user_id = auth.uid()`) — no device joins anywhere.

### D4 — Provider-config encryption
Edge Function-only CRUD; AES-GCM (Deno WebCrypto) with key from function env `PROVIDER_CONFIG_KEY`; ciphertext in `provider_configs.encrypted_config`. Table is deny-all to clients.

### D5 — Firebase dropped entirely ✅
No Crashlytics / Performance Monitoring replacement planned. Removed: google-services/crashlytics/perf plugins, all firebase-* libraries, Firebase init & consent wiring, gateway classes. Kept: AndroidX credentials + googleid (Google sign-in needs no Firebase), GMS Cast. Legacy backend files (`functions/`, `firestore.rules`, `firebase.json`) deleted at M8 after E2E passes.

### D6 — Data migration ✅ resolved: none needed
Owner confirmed Firestore was never used. Exporter permanently descoped.

---

## 2. Verified Building Blocks (checked against docs 2026-08-25)

- supabase-kt **3.7.0** (auth/postgrest/realtime/functions + BOM), Ktor 3.4.x engine, minSdk 26.
- Kotlin Google sign-in: Credential Manager → `supabase.auth.signInWith(IDToken) { idToken; provider = Google; nonce }`. Requires Web OAuth client registered in Supabase Google provider (+ Android client IDs for token issuance).
- Sessions doc: sessions last indefinitely by default; refresh tokens never expire (single-use rotation). Time-box/inactivity/single-session settings are opt-in — leave ALL OFF.
- Magic link: `signInWith(OTP)` / admin `generateLink(magiclink)` returns one-time hashed_token consumable via verify endpoint → real session.
- Realtime: postgres_changes over WebSocket; Kotlin `channel.postgresChangeFlow<T>(schema) { table; filter = "col=eq.v" }` per current docs (⚠️ 3.7.0 artifact differs slightly — see M3 retry note).

---

## 3. Component Map

```
┌──────────────────────────── MONOREPO ────────────────────────────┐
│ /app      Android: phone + TV (Compose, shared core/)             │
│ /web      Next.js+Tailwind v4 → static export → GitHub Pages     │
│           AUTH SURFACE (/pair login+claim) + product page        │
│ /supabase config.toml · migrations/*.sql · functions/*.ts (Deno) │
│ /.github  android CI · web deploy · supabase deploy             │
└──────────────────────────────────────────────────────────────────┘
     │ publishable key only              ▲ service role
     ▼                                   │
┌────────────────────────┐   ┌─────────────────────────────────────┐
│ SUPABASE               │   │ EDGE FUNCTIONS                       │
│  GoTrue (Google prov.) │◄──┤ create-pairing-session               │
│  Postgres + RLS (8 tbl)│   │ claim-pairing-session                │
│  Realtime publication  │   │ exchange-device-grant                │
│                        │   │ register-device-session              │
│  deny-all 🔒:          │   │ revoke-device                        │
│   provider_configs     │   │ save/list/delete-provider-config     │
│   pairing_sessions     │   │ delete-account                       │
│   pairing_rate_limits  │   └─────────────────────────────────────┘
└────────────────────────┘
```

## 4. Identity Model

| Actor | Identity | Obtained via |
|---|---|---|
| Phone user | Supabase account (`auth.users`) | Native Google sign-in in-app (M2); magic-link descoped |
| **Any phone w/o the app** | Same accounts, via web | `/pair` page → Supabase Google OAuth (web PKCE) |
| TV | A `devices` row bound to a user + that user's GoTrue session | Never signs into Google; inherits identity through pairing |

Chain: Google proves who you are once → `auth.users` row auto-created → every feature hangs off that `user_id`.

---

## 5. Flows

### F1 — Mobile sign-in (M2)
Credential Manager → ID token (+nonce) → `signInWith(IDToken)` → SDK-persisted session → `AccountState.SignedIn`.

### F2 — Pairing (web-first, REVISED)
```
TV     : cold start, no valid session → POST create-pairing-session
         ← {sessionId, shortCode, qrPayload=https://<site>/pair?code=X, expiry}
         renders QR + shortCode (manual entry fallback); polls ~3s
PHONE  : scan (no app required) → opens web page
WEB    : "Pair this TV?" → Sign in with Google (Supabase OAuth, PKCE client-side)
WEB    : POST claim-pairing-session {code} authenticated by WEB session
         → txn marks claimed_by = uid
TV     : next poll → {ok, token_hash, device_id}  (single-use)
Server : devices row created + admin.generateLink(magiclink, claimer.email)
TV     : verifyEmailOtp(MAGIC_LINK, token_hash) → REAL GoTrue session
         → RPC register-device-session binds auth_session_id 🎬
```
- Mobile-app fast path kept: installed+signed-in apps intercept `lamphaus://pair?code=X` and claim with their own session — same endpoints.
- Accepted trade-offs: scanned phone decides owning account; consent-screen publishing becomes a launch blocker.

### F3 — "Pairing sticks" (persistence)
Codes are ephemeral (5-min TTL, single-use, SHA-256-hashed). What persists: `devices` row + GoTrue session (refresh tokens never expire, single-use rotation, SDK auto-refresh).
Guardrails: time-box/inactivity/single-session settings stay OFF; TV distinguishes auth-rejected refresh (→ unpaired state) from network error (→ retry, never wipe stored session); deliberate unpair via TV settings sign-out.

### F4 — Cloud sync (offline-first)
Room = source of truth on device. Writes mirror upserts to Postgres (last-writer-wins by `updated_at_epoch_millis`). Realtime postgres_changes re-fetch flows mirror old Firestore snapshot listeners. UI layer untouched behind `CloudSyncGateway`.

**Synced:** profiles · library_entries · watch_progress · user_settings.
**Device-local by design:** active profile choice · profile PINs (never leave the device; synced rows carry only `has_pin`).

### F5 — Provider configs
Client → Edge Function (AES-GCM) → ciphertext in `provider_configs`. Clients never read that table. Addon manifests still fetched directly from provider CDNs by the app.

### F6 — Unpair / revoke / delete
Revoke-device flips flag + deletes the bound session → that TV drops to QR on next launch; multi-TV safe. Delete-account revokes sessions first, then cascades, then deletes auth user.

---

## 6. Database Schema (migrations 20260825102104 + 20260825102413 + 20260825115953)

All 8 tables RLS-enabled before exposure. Columns snake_case; clients map via `@SerialName` DTOs.

| Table | PK | Access |
|---|---|---|
| `profiles` | `id uuid` | owner-all `(select auth.uid())` |
| `library_entries` | `(profile_id, media_key)` | owner-all · `preview jsonb` |
| `watch_progress` | `(profile_id, video_id)` | owner-all |
| `user_settings` | `user_id uuid` | owner-all · jsonb payload |
| `devices` | `id text` (+`auth_session_id`) | owner SELECT only |
| `provider_configs` | `(user_id, provider_id)` | 🔒 deny-all |
| `pairing_sessions` | `id text` | 🔒 deny-all |
| `pairing_rate_limits` | `ip_hash text` | 🔒 deny-all |

Indexes: user/profile lookups, `(profile_id, updated_at_epoch_millis desc)`, partial `pairing_sessions(code_hash) where exchanged=false`, `claimed_by` FK index.
Realtime publication: profiles, library_entries, watch_progress, user_settings.
RLS smoke tests PASSED: owner read ✓ cross-user denial ✓ forged user_id rejection ✓ deny-all leak-proof ✓ anonymous denial ✓.
Instant removal hardening (`20260825190045`): all five owner policies additionally require a live `auth.users` row via `caller_user_exists()` (security definer) — tokens surviving an account deletion lose read AND write access immediately, not at next refresh. DB-proven 2026-08-25.

---

## 7. Client Changes (file-by-file)

| File | Change | Status |
|---|---|---|
| `gradle/libs.versions.toml` | − firebase entries · + supabase BOM/modules/Ktor | ✅ M0 |
| `app/build.gradle.kts` | minSdk 26 · − firebase plugins/deps · + SUPABASE_URL/PUBLISHABLE_KEY BuildConfig from gradle properties | ✅ M0 |
| `core/*/build.gradle.kts` | firebase deps removed · supabase deps added · minSdk 26 | ✅ M0 |
| `FirebaseGateways.kt`, `FirebaseCloudSyncGateway.kt` | deleted | ✅ M0 |
| `AppContainer.kt` | builds shared `SupabaseClient`; gateways swapped per milestone | ✅ M0/M2/M3 pending |
| `LamphausApplication.kt` / manifest | Firebase init + meta-data flags removed | ✅ M0 |
| `SupabaseGateways.kt` (new) | AccountGateway impl: sessionStatus→AccountState, IDToken+nonce sign-in | ✅ M2 |
| `MobileActivity.kt` / `AppViewModel.kt` | nonce generation + pass-through | ✅ M2 |
| `SupabaseCloudSyncGateway.kt` (new) | typed Postgrest DTOs + realtime Flows | ✅ M3 |
| `TvApp.kt` / `AppViewModel.kt` | polling loop + exchange + auto-transition | ✅ M4 code |
| Mobile settings section (new) | paired-devices list + revoke + delete-account | ✅ M6 |
| Magic-link methods | OTP implementation replacing stubs | ⬜ M7 |

## 8. Web Presence

- Stack: Next.js (App Router) + Tailwind v4 (CSS-first `@theme`), static export on GitHub Pages (`<user>.github.io/<repo>/`, basePath configured). Domain attach later without redesign.
- Routes: `/` product · `/pair?code=X` scan-target with Sign-in-with-Google + claim call · `/auth/callback` OAuth return · `/privacy` (launch blocker for pairing) · terms.
- PKCE runs fully client-side (supabase-js stores verifier; no server needed) — static-hosting compatible.
- Redirect allow-list must include `<site>/auth/callback`.
- Deep-link coexistence: installed mobile app intercepts `lamphaus://pair?code=X` and claims instantly; both paths hit identical endpoints.
- `assetlinks.json` deferred until domain exists (must live at domain root, all build variants' fingerprints).

### 8.1 Design system parity (owner directive)
Dark-first port of Android tokens into Tailwind `@theme`: beam `#A8C8FF` · primary `#AFC2FF`/on `#10234F` · secondary `#68D4E8` · bg `#090A0D` · surfaces `#15171E`/`#1E2023`/`#292A2D` · radius 4px cards / 12px hero · motion 160ms/220ms. Semantic tokens only; light values reserved; anti-references honored (no glass/gradients).

### 8.2 Brand logo (owner directive)
Lamp-house mark is a vector drawable: `ic_lamphaus_foreground.xml` (house `#4058D8`, beam `#68D4E8`, cutouts `#090A0D`, 108×108) + monochrome variant. Port verbatim pathData → `web/public/logo.svg` + React component; favicon from monochrome; og-image with full-color mark. Android XML stays canonical.

---

## 9. Repo & Ops Setup

1. ✅ `supabase init` + `link --project-ref uhxfalgfcutwrvlgjgen`
2. Schema work: local-first when Docker available; else frozen advisor-clean migrations pushed via `supabase db push` (current practice — project was empty)
3. Dashboard config done/pending: Google provider enabled ✅ (Web ID+secret; Android IDs optional field left empty — audience is the Web client) · redirect allow-list needs `<site>/auth/callback` + Pages URL · custom SMTP before magic-link testing (M7)
4. Edge Functions deployed via CLI; secrets via `supabase secrets set`. Project runs on new-format API keys: the platform-injected `SUPABASE_SERVICE_ROLE_KEY` is an opaque `sb_secret` — PostgREST accepts it, but **GoTrue admin endpoints require the legacy JWT**, stored as function secret `SERVICE_ROLE_JWT` (claim-pairing-session prefers it). Pairing + provider-config functions answer CORS preflights (browser claims/settings). Provider configs encrypt with `PROVIDER_CONFIG_KEY` (base64 AES-256 key; regenerate ⇒ old rows undecryptable by design).
5. CI secrets (owner): `SUPABASE_ACCESS_TOKEN` for functions deploys
6. Local cloud builds (owner): `~/.gradle/gradle.properties` → `lamphaus.supabaseUrl` / `lamphaus.supabasePublishableKey` / `lamphaus.webClientId` ✅ configured
7. Rewrite `docs/PRODUCTION_SETUP.md` at M8

## 10. Cutover Plan

- `CLOUD_CONFIGURED` gate remains; Local mode is the permanent escape hatch after every milestone.
- Big-bang switch acceptable pre-launch; delete legacy files only after full E2E passes (M8).
- Verification checklist: Google sign-in E2E · TV pairing E2E on real device · revocation kills TV instantly · realtime sync across two signed-in devices · account deletion leaves zero rows · advisors clean.

## 11. Risks & Notes

| Risk | Mitigation |
|---|---|
| supabase-kt 3.7.0 realtime API ≠ latest docs (`channel()` needs builder arg; string-filter setter private) | **M3 retry protocol:** pin exact call shapes with a throwaway compile-test BEFORE writing the gateway; or bump library if a newer release matches docs |
| Magic-link email deliverability | descoped with M7 — only relevant if mobile magic-link sign-in returns |
| Consent screen must be published for web pairing | privacy policy live before public launch (owner input on legal text) |
| Realtime RLS filtering nuances | explicit cross-profile isolation test in M3 |
| OAuth client misconfig (nonce/idToken rejection) | fingerprints documented in PRODUCTION_SETUP rewrite |
| Free tier pauses (~7d idle), no backups | accepted now; Pro upgrade near cutover |

Pre-lock additions carried: PIN invariant regression test in M3 · Firestore exporter descoped · RLS smoke tests as milestone DoD · rollback stance every milestone.

---

## 12. Milestones & Progress

Branch: `feature/supabase-migration` · base: main @ `5a34c51`

| # | Scope | Status |
|---|---|---|
| M0 | scaffold · link project · minSdk 26 · de-Firebase gradle/code · CI-parity build | ✅ `21ebb09` |
| M1 | schema(7tbl)+RLS+realtime+advisors green | ✅ `372da82` |
| M1b | `user_settings` table (synced settings) | ✅ `fb62066` |
| M2 | mobile Google sign-in (gateway+nonce+wiring) | ✅ `3badbe9` · live E2E verified 2026-08-25 |
| M3 | sync swap (Postgrest+realtime gateway wired into container) | ✅ `8096f26`→`495e584` · live E2E verified 2026-08-25 · PGRST303 retry + empty-cloud seeding (supabase#48123 armor) |
| Web | Next.js site: pages, design tokens, logo, `/pair` auth+claim, Pages deploy | ✅ static export green · Pages workflow ready · claim call live-pending M4 functions |
| M4 | TV pairing E2E: functions(create/claim/exchange/register-session) + TV polling + QR live | ✅ `32d44c6`→`95cb9bb` · device E2E verified 2026-08-25: QR claim via web Google sign-in, OTP session minted, devices bound, session survives TV cold start |
| M5 | provider-config Edge Functions + encryption parity test | ✅ · functions live E2E verified 2026-08-25 (401 anon · save/upsert/list/delete roundtrip) · AES-256-GCM `v1.<iv>.<ct>` AAD-bound to user+provider, NIST-vector parity test green (`supabase/tests/`) |
| M6 | device management UI + revoke E2E + delete-account | ✅ `revoke-device`+`delete-account` functions live (verify_jwt) · `revoke_device` RPC DB-verified 2026-08-25: owner revoke flips flag + destroys bound GoTrue session & refresh tokens; cross-user revoke blocked (`DEVICE_NOT_FOUND_OR_FORBIDDEN`); delete-account cascades all owner tables (FK audit) · settings UI: paired-devices list + disconnect dialog + account deletion · revoked-session RefreshFailure→SignedOut (TV drops to QR). On-device unpair observation pending next TV build. Post-delete register regression fixed: stale `cloudSyncJob` bound to the dead uid silently blocked the successor's sync (profiles never seeded/written) — job now identity-bound, restarted on account change, torn down on signed-out; delete-account additionally wipes the local Room cache (`clearLocalAccountData`, previously never wired) |
| M7 | magic-link migration + SMTP + pending-email fix | ⏸ **Descoped 2026-08-25 (owner)**: Google sign-in covers phone + web pairing, and TV-pairing `generateLink` tokens are programmatic (never hit SMTP). Client stubs (`sendEmailLink`/`completeEmailLink`) stay; revisit post-launch if non-Google email sign-in is wanted |
| M8 | cutover → delete legacy Firebase files → docs rewrite | ✅ `firebase.json` · `firestore.rules` · `firestore.indexes.json` · `functions/` deleted (incl. 119 MB node_modules) · zero firebase references in source/gradle/CI (verified by sweep; only stale `app/build/` lint caches) · README de-Firebase'd · `docs/PRODUCTION_SETUP.md` fully rewritten (Supabase auth/secrets/functions/web + launch checklist) · assembleDebug+tests green |

Execution order note: web build precedes M4 (pairing depends on `/pair`). M3 may run parallel or after M2 verification.

---

## 13. Definition of Done (project)

Full device login and linking working on BOTH mobile and TV builds:
phone users sign in natively; any browser pairs a TV via QR+Google; TVs persist sessions across restarts; owners manage/revoke devices; data syncs live across devices; settings follow the account; account deletion wipes everything; zero Firebase remnants.
