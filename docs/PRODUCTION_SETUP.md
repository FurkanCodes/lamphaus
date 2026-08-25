# Production setup

The debug app builds without cloud or Cast credentials (`CLOUD_CONFIGURED=false`, local-only mode). Production sign-in, TV pairing, cross-device sync, encrypted provider configs, and Play release require the following external configuration.

## Backend: Supabase

Project `lamphaus` — ref `uhxfalgfcutwrvlgjgen`, region `eu-central-1`. Firebase has been fully removed.

### 1. CLI linking

```bash
supabase link --project-ref uhxfalgfcutwrvlgjgen   # needs SUPABASE_ACCESS_TOKEN
supabase db push                                    # apply supabase/migrations/*
supabase functions deploy <name>                    # per function in supabase/functions/
```

Schema changes go through versioned SQL migrations only. All 8 tables ship with RLS enabled before exposure; `provider_configs`, `pairing_sessions`, and `pairing_rate_limits` are deny-all to clients (Edge Functions use the service role). Check Dashboard → Advisors after every migration.

### 2. Auth configuration

1. Enable the **Google** provider with the **Web OAuth client ID + secret** (the Web client is the audience; Android client IDs stay registered in Google Cloud for Credential Manager token issuance but are not entered in Supabase).
2. Redirect allow-list must include:
   - `<pages-url>/auth/callback` — web OAuth return
   - the GitHub Pages site origin
   - any custom domain added later
3. **Publish the OAuth consent screen before public launch** — unverified apps block the web `/pair` flow for arbitrary Google accounts.
4. Session hardening (time-box, inactivity timeout, single-session) stays OFF by design: TVs rely on long-lived rotating refresh tokens (plan F3).
5. Magic-link sign-in is descoped (M7). No SMTP needed at launch — TV pairing mints its one-time tokens programmatically via `admin.generateLink`; no email is ever sent.

### 3. Edge Function secrets

Set via `supabase secrets set`:

| Secret | Purpose |
|---|---|
| `SERVICE_ROLE_JWT` | Legacy-format service-role **JWT**. Required because GoTrue admin endpoints reject the new opaque `sb_secret_*` key that the platform injects as `SUPABASE_SERVICE_ROLE_KEY`. Used by `claim-pairing-session` (preferred) and `delete-account`. |
| `PROVIDER_CONFIG_KEY` | Base64 AES-256 key for provider-config encryption (`v1.<iv>.<ct>`, AAD-bound to user+provider id). Rotating it makes previously saved configs undecryptable by design — treat like a root credential. |

`verify_jwt=true` on all functions except `create-pairing-session` and `exchange-device-grant` (unauthenticated by design — rate-limited, single-use codes server-side).

### 4. Client build credentials

Private Gradle properties in `~/.gradle/gradle.properties`:

| Property | Purpose |
|---|---|
| `lamphaus.supabaseUrl` | Project URL |
| `lamphaus.supabasePublishableKey` | Publishable (anon/publishable) key — safe for clients; RLS is the boundary |
| `lamphaus.webClientId` | Web OAuth client ID for Credential Manager sign-in |
| `lamphaus.castAppId` | Cast receiver application ID |

Without these the app runs in Local mode permanently (permanent escape hatch).

### 5. Web app

Next.js static export deployed to GitHub Pages (`web-deploy.yml`). Routes: `/` product page, `/pair?code=X` scan target (Google sign-in + claim), `/auth/callback` OAuth return, `/privacy`. A live privacy policy is a launch blocker for public pairing.

## Cast Connect

1. Register the receiver and package in the Cast Developer Console.
2. Set `lamphaus.castAppId` in a private Gradle property.
3. Register development TV devices or install through a Play test track.

## Release

Use Play App Signing and inject upload credentials through CI secrets. Supply a verified privacy-policy URL, support contact, finalized package ownership, and legal approval before `bundleRelease` is promoted. Diagnostics (crash reports / performance metrics) remain off until the user opts in.

## Launch checklist

| Item | Status |
|---|---|
| Google sign-in E2E (mobile) | ✅ verified 2026-08-25 |
| Realtime sync across two signed-in devices | ✅ verified 2026-08-25 |
| TV pairing E2E on real device (QR + web claim) | ✅ verified 2026-08-25 |
| Revocation kills TV session | ✅ DB-verified · on-device observation pending |
| Account deletion leaves zero rows | ✅ FK-cascade audit + live test |
| Advisors clean | ✅ no open blockers |
| OAuth consent screen published | ⬜ owner action before public launch |
| Privacy policy live | ⬜ owner action |
| Pro tier upgrade near cutover | ⬜ owner decision (free tier pauses ~7d idle, no backups) |
