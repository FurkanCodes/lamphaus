# Lamphaus beta release, updates, and performance plan

Status: approved direction; consolidated implementation plan. Implementation and device verification are not yet complete.

## 1. Objective and confirmed decisions

Build and sign production APKs on the owner's Mac, host them on GitHub Releases, and publish one signed release feed used by the website and app. Ship the first beta only after release, update, native playback, and performance gates pass.

- Keep GitHub Actions for tests and website deployment. Production APK building, signing, uploading, and promotion run locally.
- Keep one universal APK for mobile/tablet and TV, with production package ID `com.lamphaus.app`.
- Show an update's changelog before downloading, with **Update** and **Later**. Android presents installation confirmation.
- Remind about a deferred version after 24 hours. A newer version is independently eligible.
- Beta users receive newer beta and stable releases. Offer a stable-only channel without downgrading.
- No release APKs have already been distributed. Establish permanent signing now; debug and staging installations remain separate.
- Include MPV packaging and playback verification before the first public beta.
- Include R8 optimization, Baseline Profiles, Startup Profiles, startup improvements, and mobile/TV performance verification before promotion.
- Default first release: `1.0.0-beta.1`, version code `2`. Use globally increasing version codes across channels, including withdrawn releases.
- Updates are optional. Do not force updates, download without an Update action, or interrupt playback.

Platform scope: **shared update infrastructure, mobile UI, TV UI, and playback integration**. Share data and behavior, not platform layouts, navigation geometry, or input behavior. Follow [RULES.md](../RULES.md), particularly `SHR-ARC-02–11`, `SHR-ARC-14–15`, `SHR-PROD-04/06/07`, and `QA-01–08`.

### Evidence informing this plan

At inspection, the app had no configured release signing or updater, the public repository had no releases, and the website was a static GitHub Pages export at <https://furkancodes.github.io/lamphaus/>. R8 and resource shrinking were already enabled. Baseline-profile tooling existed, but coverage only launched activities, and the startup benchmark covered mobile only.

NuvioTV separates discovery, ABI selection, preferences, downloads, installation, and presentation, and includes local release tooling. Use that separation as a behavioral reference, with durable recovery and artifact verification added for Lamphaus. The inspected downloader did not itself verify hashes or signing identity, and transient download state was ViewModel-owned. Reference revision: `cf83131c7ca445e68ee57cd1fa89349a8a98ff94`.

- [Nuvio updater](https://github.com/NuvioMedia/NuvioTV/tree/cf83131c7ca445e68ee57cd1fa89349a8a98ff94/app/src/full/java/com/nuvio/tv/updater)
- [Nuvio local release script](https://github.com/NuvioMedia/NuvioTV/blob/cf83131c7ca445e68ee57cd1fa89349a8a98ff94/scripts/release_beta.py)

## 2. Local release pipeline

### Signing and version identity

- Create a permanent APK signing key outside the repository. Keep passwords in macOS Keychain, maintain an encrypted offline backup, and verify recovery before first publication.
- Fail release preparation when signing credentials are missing or the fingerprint differs from the configured production identity. Never substitute a debug key.
- Introduce one tracked version configuration consumed by Gradle and release tooling. Never reuse a published tag, version code, or APK filename for different bytes.
- Keep debug/staging IDs separate and disable production update discovery in those builds. Add an isolated updater QA variant with a separate package, certificate, and feed, mirroring release optimization.
- Recover a lost signing key from backup. Never silently change it or recommend uninstalling as ordinary update recovery. See [Android app signing](https://developer.android.com/studio/publish/app-signing).

### Release command interface

Implement a Python CLI with these explicit stages. Commands that perform remote mutations also support a non-mutating `--dry-run`.

| Command | Responsibility |
| --- | --- |
| `profiles` | Generate mobile and TV profiles, merge production rules, and record provenance |
| `prepare` | Validate inputs, run release checks, build/sign the APK, and produce reviewable artifacts |
| `draft` | Create or resume a GitHub draft and upload the prepared artifacts |
| `publish` | Verify uploaded artifacts, publish the release, and promote it into the feed |
| `verify` | Check an existing release, feed, public download, and website integration |
| `withdraw --version-code …` | Remove a release from recommendations and record its withdrawal |

`prepare` requires a clean source checkout, committed release notes and profiles, and an explicit source commit. It must not stash, discard, or automatically commit unrelated work. Profile generation is an explicit preceding task; normal APK assembly must not unexpectedly require connected devices.

Preflight checks cover:

- Pinned JDK, Gradle wrapper, SDK, Build Tools, NDK, and native dependency configuration.
- Signing fingerprint and production configuration presence, without printing secrets.
- Production package, version, minimum SDK, non-debuggable flag, and configured update endpoint.
- Complete native libraries, APK signature, alignment, byte length, and SHA-256.
- Unit tests, release lint, provider-neutrality checks, and release smoke-test evidence.
- Profile provenance, packaged profile presence, Startup Profile application, and performance results.

Produce a universal APK for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`, release notes, checksums, and a build report containing the source commit and toolchain. Keep R8 mappings, native symbols, benchmark reports, and traces in the local release archive.

### Publication transaction

1. Prepare and validate the exact signed APK.
2. Upload the complete artifact set to a draft tied to the recorded source commit.
3. Verify uploaded artifacts against the local build report.
4. Publish the GitHub release with its correct prerelease flag.
5. Download the public APK anonymously and verify its size and hash.
6. Sign and atomically advance the release feed using a compare-and-swap branch update.
7. Verify public feed propagation and website resolution of the promoted version.

Failed uploads leave a draft. Failure after GitHub publication leaves a **published but unpromoted** release. Retrying resumes the same operation without rebuilding or overwriting a published APK. Concurrent publishers must fail on conflicting version allocation or feed revision.

Remove the production release-bundle build from Android CI while retaining verification jobs. Exclude metadata-branch pushes from Android CI. Keep the existing website deployment workflow.

Withdrawal updates the feed and release notice. Installed devices recover through a higher-code corrective release; do not implement automatic downgrades. Retain old artifacts and release records for traceability unless a separately authorized removal is necessary.

## 3. Shared signed release feed

Create a dedicated `release-metadata` branch containing `updates/v1/index.json`, served through `raw.githubusercontent.com`. Both app and website consume it; APK bytes remain GitHub Release assets. Release promotion does not require a website rebuild.

Do not discover releases through GitHub's latest-release endpoint: it excludes prereleases. A dedicated feed also avoids per-device authenticated requests and shared-IP REST API limits. See [release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release) and [rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api).

| Public type | Required information |
| --- | --- |
| Signed envelope | Key ID, exact payload bytes encoded as Base64, signature |
| Feed | Schema version, increasing revision, publication timestamp, releases, withdrawn version codes |
| Release | Channel, version code/name, tag, commit, publication date, minimum SDK, changelog, release-page URL |
| APK | Package ID, canonical asset URL, supported ABIs, byte length, SHA-256, signer certificate fingerprint |

- Use a separate locally held metadata signing key with `SHA256withRSA`. Embed trusted public keys in Android and the website, including an offline recovery metadata key from the first release. Document rotation.
- Verify the signature over decoded payload bytes before parsing. Schema version and revision belong inside the signed payload.
- Reject unknown keys, unsupported schemas, invalid signatures, duplicate/conflicting versions, malformed fields, oversized data, and APK URLs outside the configured repository. Bound the decoded feed to 1 MiB and enforce the bound in the publisher and clients.
- Persist the highest accepted revision and reject older revisions. Equal revisions must identify identical content.
- Generate changelog data from reviewed release notes. Render restricted text and lists, without executable HTML, remote images, or arbitrary embedded navigation.
- Use conditional HTTP requests. Revalidate before installation; cached metadata must not silently authorize an installation after a failed revalidation.
- Cache propagation is not instantaneous. Neither signing nor withdrawal can recall an already installed APK; document that operational limitation.

## 4. In-app update implementation

### Ownership and lifecycle

Add a dedicated update repository and application-scoped coordinator through the existing manual dependency container. Keep updates independent of account synchronization and separate from the large application ViewModel.

Expose immutable state from a dedicated `UpdateViewModel`:

`Checking → Available → Downloading/Waiting → Verifying → Ready → Permission required → Installing → Complete/Error`

Persist channel choice, reminder deadline, accepted feed/cache revision, selected artifact identity, download ID, installer session ID, and recoverable operation state. Android's actual download/session/package state is authoritative during reconciliation; broadcasts and saved IDs are hints, not proof of completion.

Activities handle system permission/installer launches. Repositories own data and decisions. Follow `SHR-ARC-02–11`, `SHR-ARC-14–15`, and `SHR-PROD-04/06/07`.

### Discovery and selection

- Check asynchronously on cold launch and foreground return after at least 30 minutes away. Never delay first display.
- Coalesce requests from multiple hosts. Use a 15-minute automatic-check cooldown and bounded failure backoff.
- Manual checks bypass reminders and normal cooldown but honor server retry instructions.
- Beta accepts newer beta and stable candidates; stable-only accepts stable candidates.
- Require a higher version code, compatible SDK/ABI, no withdrawal, and no semantic-version regression. Switching from a newer beta to stable reports **Waiting for a newer stable release** rather than downgrading product behavior.
- Distinguish **You're up to date**, **Couldn't check**, and **No compatible update**. A network failure is never an up-to-date result.
- Support checking and updating while signed out, without backend authentication.

### Mobile and TV presentation

An eligible prompt shows installed/new versions, beta designation, APK size, changelog, **Update**, and **Later**.

- Mobile: use a Material 3 bottom sheet that adapts to a bounded supporting surface on larger windows. Expose About/Updates through secondary app navigation and the signed-out surface.
- TV: use an overscan-safe dialog, scrollable notes, reachable fixed actions, explicit focus, opening-key suppression, and restoration of origin focus. Expose manual checks in the existing About pane.
- Later, Back, and dismissal defer that version for 24 hours. A different newer version is independently eligible, subject to once-per-session automatic presentation.
- Manual checks can reopen a deferred update. Preserve reminders across process death and handle clock changes without indefinite suppression.
- Defer automatic prompts during playback, PiP, Cast playback, authentication handoffs, and another active modal. Never steal focus from an ongoing task.
- Keep all copy localizable and all errors local and recoverable.

Rule mapping: `MOB-CMP-03/08/09`, `MOB-LAY-01/06`, `MOB-A11Y-01–06`, `MOB-PERM-01/02`, `TV-LAY-01`, `TV-FOC-01/02`, `TV-NAV-02/04/05`, and `PLY-IMM-04`.

### Download, verification, and installation

Use Android [DownloadManager](https://developer.android.com/reference/android/app/DownloadManager) for user-requested transfers and recovery across connectivity changes and reboots.

- Download to an app-specific staging directory with a staging filename, not public Downloads. Route download-notification interactions to the updater rather than direct installation.
- Show truthful progress, waiting reasons, and Cancel. Explain metered-data use before enabling it; disable roaming transfers.
- Permit one active operation. Repeated taps must not enqueue duplicates. Reconcile state whenever the app returns.
- Copy completed bytes into private staging and verify length, SHA-256, package, version, signer, and compatibility.
- Revalidate the feed before installing. Block withdrawn artifacts; if revalidation fails, preserve the verified download and offer Retry.
- Request **Allow from this source** only within the user-initiated update flow. Recheck on return without repeatedly reopening Settings after denial.
- Use a PackageInstaller session with explicit user confirmation. Handle pending confirmation and terminal status using an explicitly targeted callback with the PendingIntent behavior required by the target SDK. See [installer contract](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)).
- Verify the bytes written into the installer session against the signed hash before committing. Never install directly from externally mutable staging files.
- Launch installation UI only from a resumed host in the active update flow. Otherwise retain **Ready to install**. Do not launch an installer over playback that began while downloading.
- Confirm success from the installed package/version on the next launch: self-update can terminate the old process before its callback arrives.
- Keep retryable downloads for up to seven days. Remove cancelled, corrupt, withdrawn, superseded, and installed artifacts and abandoned sessions.

When DownloadManager, storage, settings, or an installer is unavailable, show an actionable error and the verified website download page. TV also shows the URL and QR code. Never suggest uninstalling as routine recovery.

## 5. Website integration

- Add a Download beta CTA and a dedicated download page containing version, changelog, publication date, size, minimum Android version, and installation instructions.
- Phone and TV download buttons resolve to the same universal APK.
- Fetch and verify the same signed feed at runtime. Keep the `/lamphaus/` base path and existing pairing routes working.
- Handle loading, no release, withdrawal, invalid metadata, offline, and GitHub unavailability explicitly.
- Without JavaScript or successful validation, offer the GitHub Releases page rather than an invented latest-APK link.
- Revalidate before following a cached download recommendation. A newly promoted version becomes visible without rebuilding the website, subject to cache propagation.

## 6. MPV and production-readiness gate

Repair [scripts/build-mpv-libs.sh](../scripts/build-mpv-libs.sh) as a separate milestone. At inspection, no `libmpv.so` was packaged, and the script contained incomplete source verification and invalid build arguments.

- Retain MPV `v0.40.0`, FFmpeg `n7.1`, and libass `0.17.3` as the baseline unless compatibility testing requires a documented change.
- Replace placeholder pins and invalid arguments with verified commits, checksums, correct architecture mappings, and isolated per-ABI builds.
- Build the dependency graph required for HTTPS playback, software video output, audio, ASS rendering, fonts, and rendering support.
- Package every non-system shared-library dependency for each advertised ABI. Copying only `libmpv.so` is insufficient when its dependencies are dynamically linked.
- Pin the locally installed NDK `28.2.13676358`. Verify ELF and APK alignment and run on a 16 KB page-size device/emulator. See [Android native compatibility](https://developer.android.com/guide/practices/page-sizes).
- Preserve the repository's intended native-library configuration and ship accurate notices and corresponding source/build information.
- Verify JNI under R8, MPV initialization, real video/audio output, ASS subtitles, delays, and Media3-to-MPV fallback with preserved playback state and no repeated engine bouncing.
- Keep MPV initialization lazy so packaging it does not add native work to normal app startup.
- Gate specialized capabilities by demonstrated support. MPV presence must not certify the existing no-op Dolby Vision conversion path; unsupported controls remain disabled with an explanation.

Complete applicable physical-device checks in [PLAYER_V2_RELEASE_CHECKLIST.md](../docs/PLAYER_V2_RELEASE_CHECKLIST.md). Verify production authentication and pairing with the release certificate and production configuration, plus a complete privacy page and working support contact.

Missing or failing required checks block publication. An updater that works does not by itself make the app beta-ready.

## 7. Mobile and TV performance milestone

### Release optimization

R8, resource shrinking, and `proguard-android-optimize.txt` are already enabled in [app/build.gradle.kts](../app/build.gradle.kts). Preserve them. AGP 9.2 already applies optimized resource shrinking with this configuration; do not add obsolete optimizer flags. See [R8 guidance](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).

- Audit blanket keep rules for Media3, serialization, and Cast. Replace unnecessary broad rules with narrowly required rules and retain dependency-provided rules where sufficient.
- Test optimized authentication, serialization, Cast, playback, and JNI before accepting any keep-rule reduction.
- Measure performance using non-debuggable, release-optimized builds. Profile-generation variants follow the Baseline Profile plugin's generation requirements; they are not the variants used to claim release speed.
- Existing Gradle caching and parallelism speed local builds; they are not evidence of better installed-app performance.

### Baseline Profiles and Startup Profiles

Extend the existing benchmark module with deterministic journeys:

| Platform | Profile coverage |
| --- | --- |
| Mobile | Signed-out and returning-user startup, Home scrolling, details, search, playback entry, and return |
| TV | Signed-out and returning-user startup, D-pad navigation across rails, details, search, playback entry, and focus restoration |

Use isolated deterministic test accounts/data and controlled media fixtures. Any fixture provisioning or authentication bypass must be unavailable in production releases.

The existing activities redirect to the device's actual form factor: launching TvActivity on a phone does not capture TV behavior. Generate on separate mobile and TV targets, assert the expected form factor and screen, and merge the production-code rules into the universal APK without overwriting the first device's output.

- Mark only startup journeys with `includeInStartupProfile = true`. Other journeys belong in Baseline Profiles, keeping startup-specific code compact.
- Configure `baselineProfile { saveInSrc = true; automaticGenerationDuringBuild = false }` in the app module.
- Run the explicit `profiles` command before release preparation, commit generated profiles, and record both device coverage and a fingerprint of the relevant application sources/configuration. Avoid a circular requirement that the generated files encode the final commit containing themselves.
- Release preparation rejects missing profiles or provenance that no longer matches relevant application code. Confirm packaged `assets/dexopt/baseline.prof` and Startup Profile application through build/DEX-layout diagnostics.
- Keep ProfileInstaller. Test profile installation and compilation status after both a fresh website install and an in-app upgrade.
- Measure fresh first launch separately from subsequent compiled launches. For sideloaded APKs, Baseline Profile compilation can occur later during Android background optimization. Startup Profile layout optimization is already applied at build time.

References: [profile differences](https://developer.android.com/topic/performance/baselineprofiles/difference-baseline-startup), [Gradle configuration](https://developer.android.com/topic/performance/baselineprofiles/configure-baselineprofiles), [sideloaded profile behavior](https://developer.android.com/topic/performance/baselineprofiles/overview).

### Startup, rendering, and memory

- Replace the mobile startup gate's fixed 900 ms warm-up delay with readiness-driven presentation. Reveal usable content promptly, preserve placeholders, and retain bounded failure recovery.
- Add `ReportDrawnWhen` for genuinely usable Home or sign-in content. Measure initial display and full usable-content display separately; neither metric should be satisfied merely by a splash screen. See [startup metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics).
- Use traces to identify main-thread initialization, excessive recomposition, decoding cost, and allocation pressure. Prioritize visible content and defer nonessential initialization and updater work until after first display.
- Replace original-resolution backdrop requests on small surfaces with appropriately sized variants. Bound decoding/cache memory and nearby-item prefetch without discarding focus restoration or useful offline caching.
- Preserve stable lazy-list keys; add appropriate content types and reduce broad state reads where traces demonstrate unnecessary work. Do not apply blanket stability annotations to mutable models.
- Keep playback/native initialization lazy and verify repeated browse → play → return cycles do not retain activities, surfaces, or growing image allocations.
- Preserve TV focus treatment, geometry, and approved animation timing while optimizing execution. Reduced-motion behavior must continue working.

Rule mapping: `SHR-ARC-08/10/11`, `TV-FOC-01`, `TV-MOT-01`, and `QA-08`, alongside the relevant accessibility and state-restoration rules.

### Performance release gates

Benchmark on a physical lower-end phone and TV. Use the same device, OS, display refresh rate, data, network conditions, and thermal conditions for comparisons.

| Measurement | Scenarios |
| --- | --- |
| Startup | Cold/warm, signed out/in, cached/offline; first launch and compiled launches |
| Frame timing | Mobile scrolling and rapid TV D-pad navigation |
| Playback | Time to first frame, return, MPV fallback |
| Memory | Repeated browse → play → return cycles |
| Update overhead | Checking/downloading while browsing and playback |
| Distribution size | APK size, installed size, and native-library size contribution |

- Use at least 20 startup iterations. Compare uncompiled execution with required Baseline Profile compilation; use separate controlled APK builds when isolating Startup Profile build-time effects.
- Establish the first measured release-optimized baseline before changing performance behavior. Subsequent releases compare against the approved baseline on the same hardware.
- Flag regressions above 10% in median startup or peak memory, or frame-deadline-miss increases above two percentage points. Repeat flagged measurements; unresolved regressions block promotion.
- Archive reports and traces with each release. Confirm improvement from measurements rather than promising a fixed percentage gain.

## 8. Functional verification and failure matrix

Use shared release-feed fixtures across Python, Kotlin, and TypeScript. Add publisher failure-injection tests and Android repository/ViewModel/instrumentation tests.

| Scenario | Required result |
| --- | --- |
| Offline, timeout, TLS failure, 403/429/5xx | Usable startup, bounded retry, accurate manual-check error |
| Invalid signature/schema, duplicate versions, stale revision | Reject feed; never advertise or install untrusted data |
| Beta/stable switch, equal/lower code, semantic regression | Only forward-compatible updates; no downgrade |
| Wrong SDK/ABI/package/certificate | Reject before installer handoff |
| Partial/corrupt APK, bad length, disk full, lost storage | No install; actionable Retry/Cancel and safe cleanup |
| Connectivity change, reboot, process death, lost broadcast | Reconcile state; recover or explicitly restart transfer |
| Repeated taps, competing hosts, channel change during transfer | One operation; cancel or ignore obsolete work |
| Permission denied/revoked, Settings cancelled, OEM restrictions | Usable app without permission loops |
| Installer cancelled, blocked, conflicting, or timed out | Accurate recoverable state; never false success |
| Self-update kills app before callback | Next launch confirms installed version and clears old operation |
| Playback/PiP/Cast, rotation, split screen, another modal | No interruption, duplicate prompt, or lost focus |
| Interrupted publish, duplicate command, competing publisher | Safe resume; feed never advertises incomplete artifacts |
| Withdrawal with downloaded APK | Revalidation blocks install; higher-code hotfix remains possible |
| Upgrade with populated account/library database | Preserve profiles, credentials, providers, progress, and preferences |
| Profiles missing, stale, overwritten by second device, or not compiled | Detect failure; do not claim performance coverage |
| Optimized build shrinks reflective/JNI entry points | Functional smoke tests fail preparation before publication |
| Performance regression or sustained memory growth | Repeat to confirm; unresolved regression blocks promotion |

Run release unit tests, release lint, feed contracts, publisher failure injection, website production builds, and instrumented updater tests.

Exercise installer boundaries on API 26, 28, 31, 33, 35, 36, and API 37 compatibility. Include a physical phone, physical Android/Google TV device, and 32-bit ARM target. Additional OEM platforms are supported only after their installation path is tested.

Map UI verification to `QA-01–08`: compact/expanded mobile layouts, 200% text, RTL, accessibility input, reduced motion, TV resolutions, D-pad traversal, Back behavior, startup, and playback continuity. Include the applicable system UI, font/contrast, and device-state matrices from RULES.md.

## 9. Implementation order and operational handoff

1. Establish keys, versioning, signed-feed contracts, and local release tooling.
2. Establish deterministic mobile/TV benchmark fixtures and capture the initial release-optimized performance baseline.
3. Repair and verify MPV packaging and required playback behavior.
4. Implement shared updater infrastructure and separate mobile/TV surfaces.
5. Implement website feed consumption and deploy it with an empty valid feed.
6. Narrow keep rules, improve measured startup/rendering/memory bottlenecks, and expand both platform profile journeys.
7. Generate and commit final profiles after application changes settle; run release preparation and performance gates against the actual optimized artifact.
8. Rehearse an A→B update using the isolated release-like QA variant and populated user data; verify sideloaded profile handling too.
9. Prepare and smoke-test the actual signed beta APK on required hardware.
10. Publish the complete GitHub release and promote the feed last.

Provide a runbook for ordinary publication, profile regeneration, interrupted publication, signing-key recovery, metadata-key rotation, withdrawal, forward hotfixes, and performance regressions. `publish` and `verify` produce concise health reports. Local update diagnostics record only version, stage, and error category; do not include provider credentials, URLs, tokens, or media data.

Acceptance: a locally built, signed, optimized APK is downloadable from the website; existing beta installs discover a newer eligible release with changelog and complete a user-confirmed update without data loss; mobile and TV profile/performance gates pass; MPV and production-readiness gates pass; publication and withdrawal are recoverable and documented.
