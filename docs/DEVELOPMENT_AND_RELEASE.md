# Lamphaus development and release guidelines

These instructions are mandatory for contributors and agents. `RULES.md`
contains the normative `REL-*` and `QA-*` requirements; this document turns
them into an executable workflow.

## Current release system

- `main` is the source of released code.
- `gradle/version.properties` is the single version identity.
- Production APKs are built and signed locally on the owner’s Mac.
- GitHub Actions runs validation and website deployment only. It never builds,
  signs, uploads, or promotes a production APK.
- GitHub Releases stores immutable APKs, SHA-256 files, and build reports.
- The orphan `release-metadata` branch stores the signed update feed at
  `updates/v1/index.json`.
- The website and Android app verify and consume that feed. A GitHub Release
  without a matching signed-feed revision is incomplete.

## Develop a feature

1. Update local `main` and create a focused branch:

   ```bash
   rtk git switch main
   rtk git pull --ff-only
   rtk git switch -c codex/<feature-name>
   ```

2. Declare the affected scope: mobile, TV, playback, or shared infrastructure.
   Apply and cite the corresponding rules from `RULES.md`.
3. Implement the smallest coherent change. Do not mix unrelated cleanup into
   the feature branch.
4. Run targeted tests while developing, then the standard local gates:

   ```bash
   rtk ./gradlew :app:testDebugUnitTest :app:lintDebug --max-workers=2
   rtk ./scripts/check-neutrality.sh
   ```

5. Test on the affected form factor. Mobile behavior requires phone coverage;
   TV focus, text entry, Back, and playback behavior require TV/emulator
   coverage. Apply the relevant `QA-*` matrix.
6. Commit and push the branch. Merge only after review and required checks pass.

## Prepare a new version

1. Choose a unique semantic version and increase the global version code in
   `gradle/version.properties`. After `1.0.0-beta.1` / code `2`, a typical next
   beta is `1.0.0-beta.2` / code `3`. If code `3` is published, every later
   beta or stable release must use code `4` or higher.
2. Add `release/notes/<versionName>.md`. Every release must have this structure:

   ```markdown
   # Lamphaus <versionName>

   <One sentence describing the release for users.>

   - <User-visible capability or improvement.>
   - <Another user-visible capability or improvement.>
   - Known limits: <honest remaining limitation>, when applicable.
   ```

   Keep the language concise and user-facing. Do not paste commit logs,
   implementation details, secrets, provider URLs, or internal diagnostics.
   The same file must be used without rewriting as the GitHub Release body and
   the changelog stored in the signed feed (`REL-03`).
3. If startup-sensitive paths changed, regenerate mobile and TV startup
   profiles, merge them, and commit the profile plus provenance (`REL-04`,
   `QA-08`). Do not regenerate profiles merely because a release is being cut.
4. Commit the final version, notes, code, and profile changes to `main`, push,
   and record the full source commit:

   ```bash
   rtk git rev-parse HEAD
   ```

## Build locally

From a clean `main` checkout at the recorded commit, run:

```bash
rtk python3 scripts/release/lamphaus_release.py prepare --commit <full-sha>
```

`prepare` runs unit tests, release lint, provider-neutrality checks, a
two-worker production build, signing verification, all-ABI and MPV checks, and
the packaged-profile check. It writes the immutable APK, checksum, build
report, and pipeline state under ignored `dist/release/`.

Stop Gradle after the build:

```bash
rtk ./gradlew --stop
```

## Publish and promote

Perform these stages in order on the owner’s Mac:

1. Create or resume a GitHub draft for `v<versionName>` at the exact source
   commit. Mark versions containing a prerelease suffix as prereleases.
2. Upload the APK, its `.sha256` file, and `build_report.json` from
   `dist/release/`.
3. Download the GitHub asset into a temporary directory and compare its size
   and SHA-256 with the local build report. Publish only after they match.
4. Build the next signed-feed payload from that verified GitHub asset. Increase
   the feed revision, include the exact release-notes text, sign with the
   trusted metadata key, verify the signature locally, and push the new
   `updates/v1/index.json` to `release-metadata` with compare-and-swap behavior.
5. Fetch the public feed again and verify its signature, revision, version,
   source commit, APK URL, size, checksum, signer fingerprint, and ABI list.
6. Confirm an anonymous APK download and confirm the website displays the new
   release. Test in-app update selection when the release is meant to update an
   installed build.
7. Report the evidence required by `REL-09`. If GitHub publication succeeds but
   feed promotion fails, describe the state as published but unpromoted and
   resume feed promotion without rebuilding or replacing the APK.

The `draft`, `publish`, and `verify` subcommands in
`scripts/release/lamphaus_release.py` are not completion evidence until they
implement all publication and promotion checks above. Agents must inspect their
implementation before relying on them.

## Canonical first-beta changelog

The following text is the permanent changelog for `1.0.0-beta.1`. Keep it in
`release/notes/1.0.0-beta.1.md`, the GitHub Release, and the signed feed:

```markdown
# Lamphaus 1.0.0-beta.1

First public beta: library, playback, and provider setup.

- Library, progress sync, and profiles across phone and TV.
- Media3 playback with MPV fallback, subtitles, and audio controls.
- Provider-neutral sources: install any compatible HTTPS manifest.
- Known limits: 32-bit ARM smoke-tested on emulator only; TV text entry
  uses browse/edit mode with the D-pad.
```

Future releases must add their own accurate changelog in the same format. Do
not copy beta 1 claims into later releases unless they remain relevant to that
specific release.

## Signing and recovery

Never commit private keys or passwords. The APK signing key and metadata keys
live outside the repository; the APK password lives in macOS Keychain. Maintain
verified encrypted offline backups. Losing the APK signing key prevents normal
updates to existing installations. Never replace it silently or suggest
uninstalling as the standard recovery path (`REL-06`).
