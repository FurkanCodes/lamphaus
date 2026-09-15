# Lamphaus performance baseline

Status: **incomplete on physical targets**. This report records what the
overhaul could and could not measure, the fixtures used, and the exact
commands to reproduce each scenario. No speedup, memory, or energy claim is
made here that a matched before/after run does not support.

- Source commit at the start of the overhaul: `aff8274b402490d4bdfa85fe5f3f98250fca07b6`
- Audit: `plans/performance-overhaul-guide.md` (2026-09-15)
- Measurement rules: `QA-01`–`QA-08` (performance gate `QA-08`), privacy
  `SHR-PROD-06`, profile provenance `REL-04`

## Device availability

| Target | Availability during this work | Consequence |
| --- | --- | --- |
| Physical low-end phone | not connected | no release-latency numbers |
| Physical low-memory TV | not connected | no TV release-latency numbers |
| Phone emulator | available in prior sessions | journeys/correctness only |
| TV emulator | available in prior sessions | journeys/correctness only |
| Host JVM unit tests | available | dispatcher, bounds, cancellation, and ordering gates |

Emulators validate journeys and correctness, never release performance. Until
both physical targets are connected, the baseline table below cannot be
filled and the completion checklist keeps its "physical baseline" item
unchecked.

## What was measured instead

Host-side gates that do not need a device (all green on this branch):

- Dispatcher ownership: PBKDF2, JSON mapping/encoding, and keystore calls run
  on injected CPU/IO dispatchers (`RoomLibraryRepositoryDispatcherTest`).
- Provider request bounds: byte-budget eviction, per-entry cap, declared and
  chunked oversize rejection, 304 revalidation, stale-on-error policy,
  coalescing, cancellation ownership, per-provider admission
  (`HttpProviderClientBoundsTest`, existing `HttpProviderClientTest`).
- Subtitle IO: header forwarding, oversize rejection, single retry, and
  cancellation that unblocks the transport (`SidecarSubtitleLoaderTest`).
- Audio delay correctness: fresh-processor overflow, sample preservation,
  negative-delay drop semantics, flush re-application
  (`DelayAudioProcessorTest`).
- Search publication: fast provider first, deterministic order, local error
  slots (`SearchPublicationOrderTest`).
- Startup readiness: usable/settled/degraded outcomes
  (`MobileStartupGateTest`).
- Cadence estimation semantics preserved
  (`VideoCadenceEstimatorTest`).

Build-level verification that does need a configured Android SDK:

| Claim | Evidence |
| --- | --- |
| Timing target is optimized (`PERF-01`) | `:app:minifyBenchmarkReleaseWithR8` produces `app/build/outputs/mapping/benchmarkRelease/mapping.prt` |
| Profile rules reach the timing APK (`PERF-01`) | `app/build/intermediates/r8_art_profile/benchmarkRelease/minifyBenchmarkReleaseWithR8/baseline-prof.txt` |
| Timing target is non-debuggable/profileable | baseline-profile plugin applies `isDebuggable = false`, `isProfileable = true` to synthetic types |
| Fixture data is synthetic and offline | `FixtureProviderClient`, `BENCHMARK_FIXTURES`, `CLOUD_CONFIGURED=false`, `UPDATES_ENABLED=false` |

## Scenario matrix and commands

Run from the repository root with Gradle bounded to two workers and select
the target explicitly with `ANDROID_SERIAL`. Exact task names come from
`:benchmark:tasks --all`.

```bash
rtk ./gradlew :benchmark:tasks --all --max-workers=2
rtk ./gradlew :app:tasks --all --max-workers=2
rtk proxy adb devices -l
ANDROID_SERIAL=<phone> rtk ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lamphaus.benchmark.StartupBenchmark \
  --max-workers=2
```

Production-like startup (default fixture APK keeps cloud/updates off):

```bash
rtk ./gradlew :app:assembleBenchmarkRelease \
  -Plamphaus.benchmarkCloud=true -Plamphaus.benchmarkUpdates=true \
  -Plamphaus.supabaseUrl=<project-url> -Plamphaus.supabasePublishableKey=<publishable-key> \
  --max-workers=2
rtk ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lamphaus.benchmark.ProductionStartupBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.lamphaus.integration=true \
  --max-workers=2
```

Never run the labeled integration benchmark against a default fixture APK or
with a personal account signed in; it is skipped unless its label argument is
present, and it must be rebuilt without the properties afterwards.

Compose compiler reports (diagnostic only):

```bash
rtk ./gradlew :app:compileBenchmarkReleaseKotlin -Plamphaus.composeReports=true --max-workers=2
# reports: app/build/compose-reports/app-composables.txt
```

Stress fixture for the Home/Discover, search, and library-scale scenarios
(20 providers, 100 rows per catalog, one provider failing, one delayed 5 s,
10k library entries):

```bash
rtk ./gradlew :app:assembleBenchmarkRelease -Plamphaus.benchmarkStress=true --max-workers=2
ANDROID_SERIAL=<device> rtk ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lamphaus.benchmark.StartupBenchmark \
  --max-workers=2
```

Rebuild without `-Plamphaus.benchmarkStress` afterwards; the stress fixture is
deterministic, offline, and never touches a provider or personal account.

| Scenario | Fixture / procedure | Measurements |
| --- | --- | --- |
| Cold and warm startup, both platforms | fixture account (`BENCHMARK_FIXTURES`), signed out / resident / populated library; warm and cold disk caches separately | TTID, TTFD, `lamphaus.startup.usable.content`, `lamphaus.startup.settled`, `lamphaus.startup.degraded`, `lamphaus.home.window.load` |
| Home and Discover | fixture catalog; a real-account run with the stress fixture (20 providers, 100 rows, one delayed/failing provider) | frame timing, `lamphaus.home.first.usable.row`, peak memory |
| Search | type, replace quickly, clear, navigate away; providers at 50 ms / 500 ms / timeout | `lamphaus.search.query.to.first.result`, frame timing, request count |
| Mobile scrolling / TV D-pad | `mobileHomeScrollFrameTiming`, `tvDpadFrameTiming` | frame overrun distribution, focus restoration |
| Library / progress | 100 / 1,000 / 10,000 synthetic rows, periodic progress update while browsing | SQL + `lamphaus.repository.decode` time, update-to-visible latency |
| Artwork | cold/warm cache, large source image, missing art, rapid hero changes | decode time/dimensions, hit rate, Java/native/graphics memory |
| Playback | bundled fixture clip, then controlled HLS/high-bitrate file on Media3 and MPV | `lamphaus.playback.controller.connect`, `lamphaus.playback.first.video.frame`, dropped frames, rebuffer count/duration |
| Lifecycle / soak | 20 browse→play→return cycles; 30-minute playback; idle foreground/background | retained objects, memory trend, CPU/wakeups, `lamphaus.playback.return.to.browse` |

## Gates, to calibrate after the first physical baseline

- Zero reproducible ANRs, OOMs, audio-buffer exceptions, broken focus, lost
  final progress, or duplicate players in the matrix.
- A changed high-priority journey targets at least 15% improvement in its
  primary latency or peak-memory metric without material regressions
  elsewhere. This is an engineering target, not a promised result.
- Flag matched-run p50/p95 regressions above 5%; repeat before accepting or
  rejecting. Record justified tradeoffs explicitly.
- Evaluate frame-deadline overruns at the actual device rate (about 16.7 ms
  at 60 Hz, 8.3 ms at 120 Hz); aim for non-positive p95 overrun in stable
  browse interactions once the baseline exists.
- Cache memory must stay inside the documented budget (8 MiB provider bodies,
  2 MiB per entry, 4 MiB per sidecar subtitle) and plateau after warm-up.
  Separate managed, native, graphics, and decoder memory; the 32 MiB Media3
  target is not a whole-process cap.
- The first search result publishes before a delayed provider finishes, and
  obsolete queries never publish.
- Background idle retains no screen-only polling.

## Recording a run

Record for every target: model, RAM, OS, refresh/display mode, build SHA, APK
SHA, fixture revision, compilation mode, cache/network state, and thermal
conditions. Keep raw JSON and Perfetto traces under `artifacts/performance/`
(git-ignored) and reference them by path and SHA-256 from
[`results.md`](results.md). Never capture provider URLs, credentials, queries,
tokens, stream locations, or account identifiers (`SHR-PROD-06`).
