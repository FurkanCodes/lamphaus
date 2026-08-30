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
| `PROVIDER_CONFIG_ACTIVE_KEY_ID` | Active lowercase key ID, matching one entry in `PROVIDER_CONFIG_KEYRING`. New provider ciphertext is written as `v2.<key_id>.<iv>.<ciphertext>`. |
| `PROVIDER_CONFIG_KEYRING` | JSON object mapping lowercase key IDs to base64-encoded 32-byte AES-256 keys. Keep the active key and any retired keys here until rotation is verified. |
| `PROVIDER_CONFIG_KEY` | Temporary legacy base64 AES-256 key for decrypting existing `v1.<iv>.<ciphertext>` rows. Keep it until the rotation tool proves that no v1 rows remain, then remove it. |

`PROVIDER_CONFIG_KEYRING` and `PROVIDER_CONFIG_ACTIVE_KEY_ID` are required by all four provider-config Edge Functions. Never pass any secret as a command-line argument or commit it to source control.

`verify_jwt=true` on all functions except `create-pairing-session` and `exchange-device-grant` (unauthenticated by design — rate-limited, single-use codes server-side).

### Provider-config key rotation

The codec keeps provider credentials encrypted in `public.provider_configs.encrypted_config`. The database stores ciphertext only; a database-only compromise does not reveal API keys. An Edge Function runtime or keyring compromise can decrypt user credentials because outbound provider calls require the original value. Hashing is not an option: provider APIs require the original API key for authorization.


Run this sequence for every rotation:

1. Generate a new 32-byte key and encode it as base64:

   ```bash
   NEW_KEY="$(openssl rand -base64 32)"
   ```

2. Add the new key to `PROVIDER_CONFIG_KEYRING`, set `PROVIDER_CONFIG_ACTIVE_KEY_ID` to its lowercase ID, and retain the old key plus `PROVIDER_CONFIG_KEY`:

   ```bash
   supabase secrets set \
     PROVIDER_CONFIG_KEYRING='{"k1":"<retired-key-base64>","k2":"'"$NEW_KEY"'"}' \
     PROVIDER_CONFIG_ACTIVE_KEY_ID=k2 \
     PROVIDER_CONFIG_KEY='<legacy-v1-key-base64>'
   ```

3. Deploy all four codec consumers while the old key remains available:

   ```bash
   for function in save-provider-config list-provider-configs save-artwork-config resolve-artwork; do
     supabase functions deploy "$function"
   done
   ```

4. With `SUPABASE_URL`, `SERVICE_ROLE_JWT`, `PROVIDER_CONFIG_ACTIVE_KEY_ID`, `PROVIDER_CONFIG_KEYRING`, and `PROVIDER_CONFIG_KEY` supplied through the process environment, run a dry-run first:

   ```bash
   deno run --allow-env --allow-net supabase/scripts/rotate-provider-configs.ts
   ```

   Confirm the output reports ciphertext totals by version/key ID, reports the expected rows requiring rotation, performs zero PATCH requests, and contains no plaintext/API keys.

5. Apply the rotation:

   ```bash
   deno run --allow-env --allow-net supabase/scripts/rotate-provider-configs.ts --apply
   ```

   The tool re-encrypts unchanged JSON, PATCHes only `encrypted_config` for each exact `(user_id, provider_id)` row, continues after row-local failures, and exits nonzero if any row fails. A successful apply verifies that every row is `v2.<active-key-id>.*`.

6. Run a second dry-run and confirm zero rows require rotation. Query only ciphertext prefixes and key IDs, never decrypted configs. Remove retired keyring entries and the legacy `PROVIDER_CONFIG_KEY` only after that verification, then redeploy the four functions.

### Local-only artwork keys

Users can enable **Keep artwork keys on this device** in Artwork settings. In this mode, keys are encrypted with Android Keystore-backed storage and artwork lookups call TMDB and Fanart.tv directly from the device. The keys are not uploaded to Lamphaus, stored in Supabase, or included in synced settings.

This is a separate trust boundary: the device and its Android Keystore protect the local keys, while the provider receives the key and lookup request. Cloud artwork resolution does not use these local keys. Both directions are destructive: enabling local-only mode permanently deletes every cloud `artwork.*` key and any stale local artwork keys, while disabling it permanently deletes every device-local artwork key and leaves cloud storage empty. Keys cannot be recovered, and no credential migration occurs; users must re-enter keys in the destination mode.

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
| Leaked Password Protection enabled (Dashboard → Authentication → Security) | ⬜ one click; advisors-flagged, zero passwords today |
| Production Auth site_url / redirect URLs set | ⬜ config still carries local `127.0.0.1` values |
| OAuth consent screen published | ⬜ owner action before public launch |
| Privacy policy live | ⬜ owner action |
| Pro tier upgrade near cutover | ⬜ owner decision (free tier pauses ~7d idle, no backups) |
