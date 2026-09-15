# Lamphaus performance overhaul results

Source commit at the start: `aff8274b402490d4bdfa85fe5f3f98250fca07b6`.
Audit: `plans/performance-overhaul-guide.md`. Baseline and scenario matrix:
[`baseline.md`](baseline.md). Rules cited per change (`SHR-ARC-*`, `SHR-PROD-06`,
`MOB-*`, `TV-*`, `PLY-*`, `QA-*`, `REL-04`).

Status: implementation and host-side verification complete; **device
measurements outstanding** because no physical phone or TV was connected.
Rows below therefore separate verified behavior/build facts from measurements
that still need a matched before/after run on hardware.

## Results

| Change / rule IDs | Device / scenario | Before / after SHA | Metric and samples | Before | After | Difference | Trace / test evidence | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PERF-13 audio delay overflow (`PLY-IMM-04`, `QA-06`) | host JVM | `aff8274b` → this branch | audio bytes preserved, no `BufferOverflowException` | fresh processor overflowed / started audio early with positive delay | silence held back, all samples emitted in order | correctness fix (no timing claim) | `DelayAudioProcessorTest` (9 tests) | keep |
| PERF-03 repository dispatchers (`SHR-ARC-04`, `SHR-ARC-11`, `QA-05`) | host JVM | same | dispatcher dispatch counts, PIN clearing | hashing/decrypt/decode could run on Main | CPU dispatcher for hash/JSON, IO for keystore, flows `flowOn` | not measured on device | `RoomLibraryRepositoryDispatcherTest`; traces `lamphaus.repository.hash/decode` | keep |
| PERF-04/05/06 provider client (`SHR-ARC-11`, `SHR-ARC-13`, `SHR-PROD-06`) | host JVM (MockWebServer) | same | cache bytes, coalesced calls, stale policy, admission peak | unbounded body map, unbounded read, stale playable URLs | 8 MiB budget / 2 MiB entry / 8 MiB response caps, coalescing, 3-per-provider admission, streams never stale | bounds hold in tests; request latency unmeasured | `HttpProviderClientBoundsTest` (10), existing `HttpProviderClientTest` | keep |
| PERF-10 subtitle IO (`SHR-ARC-11`, `QA-05`) | host JVM (local HTTP server) | same | cancellation latency, byte cap | blocking `HttpURLConnection`, unbounded `readBytes`, retry after cancel | shared OkHttp call cancelled, 4 MiB cap, no retry after cancel | cancellation returns within 5 s bound in test | `SidecarSubtitleLoaderTest` (4) | keep |
| PERF-06 incremental search (`SHR-ARC-06`, `TV-CNT-02`) | host JVM | same | publication order | all providers awaited before publishing | each provider publishes as it resolves, deterministic order, bounded manifest wait | not measured on device | `SearchPublicationOrderTest`; trace `lamphaus.search.query.to.first.result` | keep |
| PERF-09 playback loops (`SHR-ARC-10`, `QA-06`) | host JVM + code inspection | same | wake-ups, segment reloads | 500 ms progress poll always on; any settings change reloaded segments | poll only while chrome/PiP visible; settings projected with `distinctUntilChanged` | wake-up reduction unmeasured | `PlaybackChromeTest` contract unchanged | keep |
| PERF-09 cadence estimator (`QA-06`, `QA-08`) | host JVM | same | recompute cadence, allocation | sort 60 boxed intervals every frame | primitive ring, bounded recompute, same detection | semantics preserved in tests | `VideoCadenceEstimatorTest` (4) | keep |
| PERF-12 startup/idle (`SHR-ARC-03`, `QA-04`) | code + build | same | scheduled work | periodic no-op worker every 6 h + WorkManager init | worker and WorkManager dependency removed | startup/idle delta unmeasured | `LamphausApplication`; no WorkManager references | keep |
| PERF-01 benchmark parity (`QA-08`, `REL-04`) | host Gradle | same | timing target optimization | `benchmarkRelease` non-minified | `benchmarkRelease` R8 + resource shrinking; `nonMinifiedRelease` stays source-name | build-level fact | `:app:minifyBenchmarkReleaseWithR8` mapping + profile rules | keep |
| PERF-02 journeys/readiness (`QA-01`, `QA-07`) | emulator (needs run) | same | journey assertions | TV journey never asserted playback; no TV uncompiled startup | TV asserts details/playback/return; mobile scroll/search/playback timing; usable vs degraded startup outcomes | journeys not re-run here | `BaselineProfileGenerator`, `StartupBenchmark`, `MobileStartupGateTest` | keep |
| PERF-07/08 Compose/artwork (`MOB-CMP-08`, `QA-08`) | not measured | same | frame time, decoded memory | — | — | — | — | hold: no evidence yet |
| PERF-11 large collections (`SHR-ARC-13`, `QA-05`) | not measured | same | SQL/decode time at 100/1k/10k rows | — | repository decode bounded and off Main | scale latency unmeasured | `RoomLibraryRepositoryDispatcherTest` | partial: measure before paging |

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
  a functional change with its own retention/auth policy and is not claimed
  here.

## Regeneration and verification still required (`REL-04`, `QA-08`)

1. Connect the physical phone and TV (and set `ANDROID_SERIAL`).
2. Run the scenario matrix in `baseline.md` for the pre-change commit and this
   branch; fill the table above with real numbers and store raw captures under
   `artifacts/performance/`.
3. Regenerate mobile and TV startup profiles independently, merge without
   losing either platform, update `release/profiles_provenance.json`, and
   verify the packaged profiles.
4. Host gates already pass on this branch: `:core:model:test`,
   `:core:provider:test`, `:core:data:testDebugUnitTest`,
   `:core:player:testDebugUnitTest`, `:app:testDebugUnitTest`,
   `:app:lintDebug`, and `scripts/check-neutrality.sh`. Connected
   instrumentation and the named TV text-field browse/edit manual gate still
   need a device.
5. Keep this file's unmeasured rows honest until step 2 completes; no full
   optimization claim is supported before then.
