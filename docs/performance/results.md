# Lamphaus performance overhaul results

Source commit at the start: `aff8274b402490d4bdfa85fe5f3f98250fca07b6`.
Audit: `plans/performance-overhaul-guide.md`. Baseline and scenario matrix:
[`baseline.md`](baseline.md). Idle-work ownership:
[`idle-ownership.md`](idle-ownership.md). Rules cited per change
(`SHR-ARC-*`, `SHR-PROD-06`, `MOB-*`, `TV-*`, `PLY-*`, `QA-*`, `REL-04`).

Status: implementation and host-side verification complete; **device
measurements and profile regeneration outstanding** because no physical phone
or TV was connected. Rows separate verified behavior/build facts and host
measurements from measurements that still need matched before/after runs on
hardware.

## Results

| Change / rule IDs | Device / scenario | Before / after SHA | Metric and samples | Before | After | Difference | Trace / test evidence | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PERF-13 audio delay overflow (`PLY-IMM-04`, `QA-06`) | host JVM | `aff8274b` → this branch | audio bytes preserved, no `BufferOverflowException` | fresh processor overflowed / started audio early with positive delay | silence held back, all samples emitted in order | correctness fix (no timing claim) | `DelayAudioProcessorTest` (9 tests) | keep |
| PERF-03 repository dispatchers (`SHR-ARC-04`, `SHR-ARC-11`, `QA-05`) | host JVM | same | dispatcher dispatch counts, PIN clearing | hashing/decrypt/decode could run on Main | CPU dispatcher for hash/JSON, IO for keystore, flows `flowOn` | not measured on device | `RoomLibraryRepositoryDispatcherTest` (6); traces `lamphaus.repository.hash/decode` | keep |
| PERF-04/05/06 provider client (`SHR-ARC-11`, `SHR-ARC-13`, `SHR-PROD-06`) | host JVM (MockWebServer) | same | cache bytes, coalesced calls, stale policy, admission peak | unbounded body map, unbounded read, stale playable URLs | 8 MiB budget / 2 MiB entry / 8 MiB response caps, coalescing, 3-per-provider admission, streams never stale | bounds hold in tests; request latency unmeasured | `HttpProviderClientBoundsTest` (10), existing `HttpProviderClientTest` | keep |
| PERF-10 subtitle IO (`SHR-ARC-11`, `QA-05`) | host JVM (local HTTP server) | same | cancellation latency, byte cap | blocking `HttpURLConnection`, unbounded `readBytes`, retry after cancel | shared OkHttp call cancelled, 4 MiB cap, no retry after cancel | cancellation returns within the 5 s test bound | `SidecarSubtitleLoaderTest` (4) | keep |
| PERF-06 incremental search (`SHR-ARC-06`, `TV-CNT-02`) | host JVM | same | publication order | all providers awaited before publishing | each provider publishes as it resolves, deterministic order, bounded manifest wait | not measured on device | `SearchPublicationOrderTest` (3); trace `lamphaus.search.query.to.first.result` | keep |
| PERF-07 Compose configuration (`MOB-CMP-08`, `QA-08`) | host compiler + code inspection | same | compiler report | configuration unverified | 186/186 Lamphaus composables `restartable skippable`; strong skipping verified, no flags added | recomposition/frame time unmeasured | `app/build/compose-reports/app-composables.txt` via `-Plamphaus.composeReports=true` | keep (config verified) |
| PERF-07 artwork resolution in composition (`SHR-ARC-14`, `QA-08`) | code inspection from report | same | per-composition allocations/parses | `MediaArtwork`, `KenBurns`, mobile hero overlay resolved artwork on every recomposition | resolution remembered per `(media, resolver[, preferBackdrop])` | not measured; removes repeated copy/URI parse | `Artwork.kt`, `KenBurns.kt`, `MobileHome.kt` | keep |
| PERF-09 playback loops (`SHR-ARC-10`, `QA-06`) | host JVM + code inspection | same | wake-ups, segment reloads | 500 ms progress poll always on; any settings change reloaded segments | poll only while chrome/PiP visible; settings projected with `distinctUntilChanged` | wake-up reduction unmeasured | `PlaybackChromeTest` contract unchanged | keep |
| PERF-09 cadence estimator (`QA-06`, `QA-08`) | host JVM | same | recompute cadence, allocation | sort 60 boxed intervals every frame | primitive ring, bounded recompute, same detection | semantics preserved in tests | `VideoCadenceEstimatorTest` (4) | keep |
| PERF-11 local collection decode (`SHR-ARC-13`, `QA-05`) | host JVM, synthetic rows | same | repository decode ms (SQL excluded) | decode on Main, unbounded work at scale | off Main via CPU dispatcher | 100/1k/10k progress rows: 0/2/23 ms; library: 2/4/26 ms | `LibraryScaleDecodeTest` prints `PERF-11` lines | keep; paging not justified yet |
| PERF-12 startup/idle (`SHR-ARC-03`, `QA-04`) | code + build | same | scheduled work, idle ownership | periodic no-op worker every 6 h + WorkManager init | worker and WorkManager dependency removed; ownership table added | startup/idle delta unmeasured | [`idle-ownership.md`](idle-ownership.md) | keep |
| PERF-12 optional integrations (`SHR-ARC-03`) | code inspection | same | construction path | lazy container builds Cast/update/Supabase eagerly on first access | Cast kept (receiver contract needs it at launch); update stack objects are thin and container-scoped; Supabase needed for auth | no change justified without traces | `AppContainer`, `LamphausApplication` | keep, revisit with traces |
| PERF-08 Coil cache clear (`QA-08`, `PLY-PIP-03`) | code inspection | same | decoded memory vs. return-to-browse | full memory-cache clear before playback | unchanged, now tagged with the evidence needed to change it | none | `PlayerActivity` PERF-08 comment | keep |
| PERF-01 benchmark parity (`QA-08`, `REL-04`) | host Gradle | same | timing target optimization | `benchmarkRelease` non-minified | `benchmarkRelease` R8 + resource shrinking; `nonMinifiedRelease` stays source-name | build-level fact | `:app:minifyBenchmarkReleaseWithR8` mapping + profile rules | keep |
| PERF-02 journeys/readiness (`QA-01`, `QA-07`) | emulator (needs run) | same | journey assertions | TV journey never asserted playback; no TV uncompiled startup | TV asserts details/playback/return; mobile scroll/search/playback timing; startup readiness span + distinct usable/settled/degraded outcomes | journeys not re-run here | `BaselineProfileGenerator`, `StartupBenchmark`, `MobileStartupGateTest` | keep |
| PERF-02 production-like startup (`QA-08`, `SHR-PROD-06`) | needs credentialed run | same | labeled integration startup | fixtures could not measure cloud/update initialization | timing APK can be built with `-Plamphaus.benchmarkCloud/-Plamphaus.benchmarkUpdates=true`; `ProductionStartupBenchmark` measures signed-out readiness and is skipped without its label | not run here (no credentials/target) | `ProductionStartupBenchmark`, build properties | keep; run before release |
| PERF-02/11 stress fixture (`QA-01`, `QA-05`) | host JVM + needs device run | same | deterministic stress rows | matrix had no 20-provider/100-row/10k-entry fixture | `-Plamphaus.benchmarkStress=true` seeds 20 fixture providers, 100 rows per catalog, one 5 s provider, 10k library entries | fixture determinism verified on host; device run outstanding | `FixtureProviderClientStressTest` (3) | keep; run in matrix |

## Tradeoffs recorded

- Provider caching keeps raw bodies and re-parses on 304 rather than caching
  parsed trees, so retained bytes stay predictable; the cost is a rare parse.
- `streams` and `subtitles` responses are never revalidated from cache because
  their URLs can expire; this trades a network round trip for correctness.
- `benchmarkRelease` now pays R8 at build time; profile generation still uses
  the non-minified source-name target.
- The PlaybackScreen progress poll stops while chrome is hidden; revealing
  chrome re-samples immediately, so visible values are unaffected.
- `MetadataSyncWorker` was removed rather than implemented. Background sync is
  a functional change with its own retention/auth policy and is not claimed.
- Artwork resolution is now remembered; resolver identity changes whenever
  overrides change, which is the correct invalidation key (verified by
  `ArtworkResolverTest`).
- Compose reports stay opt-in (`-Plamphaus.composeReports=true`) so normal
  builds do not pay report generation.

## Deliberately not changed (needs device evidence)

- Frame time, decoded-image memory, Coil budget alternatives (`PERF-07`/`PERF-08`).
- Display-mode/format tick cadence and the 32 MiB Media3 buffer target
  (`PERF-09`, playback memory), because both back approved playback behavior.
- Continue Watching paging / new indexes (`PERF-11`): host decode is bounded
  and linear, and SQL/device time has not been measured.

## Regeneration and verification still required (`REL-04`, `QA-08`)

1. Connect the physical phone and TV (and set `ANDROID_SERIAL`).
2. Run the scenario matrix in `baseline.md` for the pre-change commit and this
   branch; fill the unmeasured rows with real numbers and store raw captures
   under `artifacts/performance/`.
3. Also run the labeled integration startup once with a dedicated test
   account: build with
   `-Plamphaus.benchmarkCloud=true -Plamphaus.benchmarkUpdates=true` plus the
   Supabase properties, install the timing APK, and run
   `ProductionStartupBenchmark` with
   `-Pandroid.testInstrumentationRunnerArguments.lamphaus.integration=true`.
   Record network conditions.
4. Regenerate mobile and TV startup profiles independently, merge without
   losing either platform, update `release/profiles_provenance.json`, and
   verify the packaged profiles.
5. Host gates already pass on this branch: `:core:model:test`,
   `:core:provider:test`, `:core:data:testDebugUnitTest`,
   `:core:player:testDebugUnitTest`, `:app:testDebugUnitTest`,
   `:app:lintDebug`, and `scripts/check-neutrality.sh`. Connected
   instrumentation and the named TV text-field browse/edit manual gate
   (`TV-NAV-03`, `QA-07`) still need a device.

## Completion checklist

- [ ] **Baseline measured on physical mobile and TV** — marked incomplete:
      targets unavailable; host-side evidence and the exact matrix are recorded.
- [x] Every confirmed issue is fixed or has an evidence-backed disposition
      (PERF-01–06, 09–13; PERF-07 config verified + artwork fix; PERF-08 and
      PERF-11 paging explicitly held with the evidence required to change them).
- [x] Timing artifacts mirror production optimization; profile journeys assert
      real outcomes (build-level verification recorded above).
- [x] Memory and request bounds hold under stress and cancellation (unit gates).
- [ ] Startup, search, browse, playback, return, and idle results **compared on
      matched conditions** — blocked on step 1/2; idle ownership is inventoried.
- [x] Required correctness/focus/lifecycle host tests pass; connected gates
      remain for step 5.
- [ ] Changed startup profiles and provenance verified — blocked on step 4.
- [x] Final report states measured improvements, regressions, unresolved risks,
      and exact reproduction commands without unsupported "fully optimized"
      claims.
