# Lamphaus release runbook (plan §9)

Scope: **shared update infrastructure, mobile UI, TV UI, playback
integration**. Layouts, navigation geometry, and input stay platform-specific
(RULES.md; `SHR-ARC-02–11`, `SHR-ARC-14–15`, `SHR-PROD-04/06/07`, `QA-01–08`).

## Key inventory

| Key | Lives | Backup |
| --- | --- | --- |
| APK signing key (permanent) | Owner's Mac, passwords in macOS Keychain | Encrypted offline copy; recovery verified before first publication |
| Metadata signing key `lamphaus-metadata-1` | `~/.config/lamphaus/keys/metadata-primary.pem` (outside repo) | Encrypted offline copy |
| Metadata recovery key `lamphaus-metadata-recovery-1` | `~/.config/lamphaus/keys/metadata-recovery.pem` (outside repo) | Encrypted offline copy, stored separately |

Public halves are embedded in `UpdateFeed.Keys` and `web/src/lib/updates.ts`.
`release/signing.properties` (local, never committed) carries the expected
APK fingerprint; see `release/signing.properties.example`. `prepare` fails
when credentials are missing or the fingerprint differs — never substitutes
a debug key and never prints secrets.

## Ordinary publication

1. Bump `gradle/version.properties` (globally increasing versionCode across
   channels, including withdrawals). Write `release/notes/<version>.md`.
2. Develop on a focused branch, run the applicable platform checks, merge the
   verified change to `main`, and push it.
3. When startup-sensitive paths changed, generate profiles on the mobile
   device and TV emulator, then commit the merged profile and provenance.
4. `python3 scripts/release/lamphaus_release.py prepare --commit <sha>` — clean tree, tests, lint, neutrality,
   signed universal APK for all four ABIs, profile + MPV gates.
5. Create the GitHub draft locally, upload the APK, checksum, and build report,
   verify the downloaded bytes, then publish with the correct prerelease flag.
6. Advance and sign `release-metadata`, preserving its increasing revision,
   and push it only after the GitHub asset is verified.
7. Verify the release page, signed feed, anonymous download, website card, and
   in-app update selection before declaring the release complete.

The script's `prepare` command is the validated build entry point. Until its
`draft`, `publish`, and `verify` commands implement every step above, perform
publication and feed promotion as explicit local steps and do not treat those
placeholder commands as evidence of completion. See
`docs/DEVELOPMENT_AND_RELEASE.md` for the agent checklist (`REL-01–09`).

## Interrupted publication

- Failed uploads leave a draft: re-run `draft` (resumes, never rebuilds).
- Failure after GitHub publication leaves **published but unpromoted**:
  re-run `publish` (never overwrites a published APK).
- Concurrent publishers fail on conflicting version allocation or feed
  revision — rebase on the newest feed revision and retry.

## Signing-key recovery

Recover the APK key from the offline backup and re-verify the fingerprint
against `production.signerFingerprint`. Never silently change the key or
recommend uninstalling as ordinary update recovery — installed devices
recover through a higher-code corrective release.

## Metadata-key rotation

1. Generate a new key, add its ID + public half to the app and website.
2. Ship the app/site with old + new IDs trusted.
3. After propagation, sign with the new ID; retire the old ID in a later
   release. Reject unknown keys, stale revisions, and equal revisions with
   differing content.

## Withdrawal and hotfixes

`... withdraw --version-code N` removes the release from recommendations and
records it; release notices are updated. No downgrades: ship a higher-code
corrective release. Old artifacts and records are retained for traceability.

## Performance regressions

Benchmarks: same device/OS/refresh-rate/data/network/thermal conditions, ≥20
startup iterations, compiled vs uncompiled comparison. Flag >10% median
startup or peak-memory regression, or >2pp frame-deadline misses; repeat
flagged measurements — unresolved regressions block promotion. Reports and
traces archive with each release.

## Deliberately retained (not drift)

- Broad `proguard-rules.pro` keeps for serialization/Cast: narrowing waits
  for instrumented release-smoke evidence on optimized auth, playback, and
  JNI (plan §7). R8 full-mode, resource shrinking, and
  `proguard-android-optimize.txt` stay on.
- `prepare` checks `liblamphaus_mpv.so` presence per ABI; full MPV behavior (HTTPS,
  ASS, fallback, lazy init) is verified on hardware per
  `docs/PLAYER_V2_RELEASE_CHECKLIST.md` before promotion.
- Device verification (QA-02–08: matrices, API 26–37 installer boundaries,
  physical phone + TV + 32-bit ARM, A→B rehearsal on the updater QA variant)
  runs before the first public beta; `publish`/`verify` report health.

## Diagnostics privacy

Local update diagnostics record only version, stage, and error category —
never provider credentials, URLs, tokens, or media data (`SHR-PROD-06`).
