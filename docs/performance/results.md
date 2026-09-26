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
| PERF-14 TV Ken Burns idle redraw (`TV-MOT-01`, `QA-07`, `QA-08`) | `Television_4K` AVD, Android 16, SwiftShader, default fixture, native 4K panel (same orientation both builds) | `1d58223` → `feature/tv-focus-performance` | `gfxinfo` frames rendered in 5 s with no input | Home: 109 frames, 98% janky, every frame a full-window repaint while the 24 s drift runs; restarted on every settled focus | rows focused: 0 frames; drift runs only while the hero or top navigation has focus and holds its frame otherwise | continuous full-window repaint removed while browsing rows | `KenBurnsMotionTest` (remaining-duration resume); `tvRowBrowseFrameTiming` | keep |
| PERF-14 TV focus cascade (`TV-FOC-02`, `TV-MOT-01`, `QA-08`) | same AVD, host GPU (`-gpu host`), `wm size 1920x1080` + density 320, landscape, `benchmarkRelease`, 10 iterations | same | `tvRowBrowseFrameTiming` (`FrameTimingMetric`): frames, `frameDurationCpuMs`, `frameOverrunMs` | 425.5 frames; CPU P90 32.6 / P99 36.3 ms; overrun P90 +18.4 / P99 +23.2 ms | 238 frames; CPU P90 18.0 / P99 23.7 ms; overrun P90 +2.5 / P99 +13.5 ms | −44% frames, −45% CPU P90, −16 ms overrun P90 | ambient decode 960×540 (was 1920×1080) with one decode for image + palette; layer-free hero/ambient/label fades (`TvLayerlessCrossfade`, `ModulateAlpha`); accent read only by focused card/hero; focus moves no longer recompose `TvSignedIn`; skeleton pulses draw-phase only | keep |
| PERF-14 control journey | same | same | `tvDpadFrameTiming` (vertical, no settle) | CPU P50 18.1 / P99 47.6 ms | CPU P50 17.8 / P99 47.4 ms | unchanged (path not touched) | benchmark JSON | keep; vertical rail scroll is the next candidate |
| PERF-15 TV ambient pre-composed (resolution superseded by PERF-19) (`TV-CLR-01`, `TV-MOT-01`, `QA-08`) | SEI Robotics Box R 4K Plus (Amlogic sc2, Mali-G31, Android 14, 32-bit, UI 1920×1080 @ 59.94 Hz), `benchmarkRelease`, `dumpsys gfxinfo` + `framestats`, 3 cold runs; default fixture, row browse settling on each card | beta.13 `8fff389` → `feature/tv-smoother-scrolling` | janky %, frame median, GPU median | 58–72% janky; frame 88–92 ms; GPU 45–48 ms (whole screen ≥4× overdraw) | 8.1–8.8% janky; frame 20 ms; GPU 10–12 ms (background 1× overdraw) | ~4.4× faster frames | window background dropped after first frame, redundant ambient `Box` background removed, background + dimmed art + both scrims baked off-thread into one opaque 960×540 bitmap (`composeAmbient`) | keep |
| PERF-15 TV hero scrims baked (`SHR-PROD-01`, `QA-08`) | same TV; stress fixture, brisk vertical D-pad (0.35 s) | same | janky %, frame P50/P90, GPU P90 | 81–82% janky; P50 105–121 ms, P90 150 ms; GPU P90 ~95 ms | 8.1–8.7% janky; P50 22–30 ms, P90 65–69 ms; GPU P90 20–21 ms | P90 frame −55% | three hero gradient passes replaced by one half-resolution baked scrim (`tvHeroScrim`); on-device hero screenshot diff mean 0.5/255, P99 2/255 | keep |
| PERF-15 diagnostics (not kept) | same TV, vertical journey | same | as above | — | ambient crossfade snapped, focus halo shadow removed, fixture art via Coil: no measurable change | none | real-TV A/B; fixture art via Coil kept anyway (removes a 22 ms UI-thread PNG decode seen in the emulator trace) | halo and crossfade unchanged |
| PERF-16 full-screen sweep: details backdrop baked (`SHR-PROD-01`, `QA-08`) | same TV, default fixture, 3 cold runs, `dumpsys gfxinfo`; series details open, episodes, actions, back | beta.13 → `feature/tv-smoother-scrolling` | janky %, frame median, GPU median | 69.8% janky; frame 98 ms; GPU 50 ms | 10.4% janky; frame 25 ms; GPU 12 ms | ~3.9× faster frames | artwork plus two scrims baked off-thread into one opaque 1920×1080 bitmap (`TvDetailBackdrop`, shared `bakeArtwork`); missing-art mark baked in the same place; on-device diffs 0.03/255 (no art) and 0.8/255 (art) | keep |
| PERF-16 Settings pane settles (`TV-NAV-05`, `QA-08`) | same TV, default fixture, 3 cold runs, `dumpsys gfxinfo`; burst of 6× down + 6× up in one `input` call | same | frames >100 ms; time spent in them | 9–11 frames; 1.2–1.7 s | 2 frames; 0.21–0.24 s | ~7× less blocked time | the pane follows menu focus after `settingsPaneSettleMillis` (160 ms); Select and Right switch at once (Right then moves focus into the new pane). The trace showed 116 ms of lazy-row composition and `Text` measurement per pane | keep |
| PERF-16 sweep, no further change needed | same TV, default fixture, 3 cold runs, `dumpsys gfxinfo` | same | janky % | search/browse 74.3%; player chrome 82.3% | search 7.8%; player 8.9% | fixed by PERF-15 | — | — |
| PERF-17 Sources progressive + baked backdrop (`TV-FOC-01`, `SHR-PROD-04`, `QA-08`) | SEI Box R 4K Plus (Mali-G31, Android 14), stress fixture (20 add-ons, 120 rows, 150-1200 ms catalogs, 300 sources), 3 cold runs, `dumpsys gfxinfo` + timestamped screenshots; open a title, Play, scroll 30 sources | `7024767` (beta.14 code + fixture) → `feature/tv-large-catalogs` | time to first source row; janky %, frame P50/P90 while scrolling | first sources at 5.4-6.3 s (waited for the 5 s add-on); 51% janky, P50 57 ms, P90 73-77 ms | first sources ≤ 1.1 s (first screenshot); 7.2% janky, P50 20 ms, P90 23-24 ms | about 5× sooner, about 7× fewer janky frames | sources publish per add-on in add-on order; index-free keys; picker backdrop baked (`TvBakedBackdrop`); focused source held for 6 s while later add-ons arrived | keep |
| PERF-17 Home large catalogs (`TV-CNT-02`, `QA-08`) | same TV; 40 brisk Down presses from the hero | same | janky %, frame P90, rows reached | 12.9-14.0% janky, P90 44 ms; about 44 rows loaded, footer never reached | 9.4-10.2% janky, P90 40 ms; about 40 rows loaded, footer never reached | modest at this latency | overlapping windows (a slow catalog no longer blocks the next window), prefetch about 8 rows ahead without a settle delay, ordered reveal (empty rows never shown), no whole-catalog flattening per update; at this fixture latency the old loader also kept up | keep |
| PERF-17 first-profile seeding (`SHR-ARC-09`) | benchmark fixture | same | add-ons seeded on first launch | 1 of 20 (collectLatest cancelled the setup) | 20 of 20 | fixture correctness | `createInitialProfile` runs `NonCancellable` | keep |
| PERF-18 Background artwork setting (`TV-FOC-02`, `QA-08`) | SEI Box R 4K Plus, default fixture, row browse, 3 cold runs, `dumpsys gfxinfo` | `060329a` → `feature/tv-background-artwork-setting` | janky %, frame median, GPU median | on: 8.8%, 19.7 ms, 9.5 ms | off: 8.2%, 18.4 ms, 8.4 ms | about 1 ms GPU; also no artwork decode or palette work per focus change | device-local Appearance toggle; automatic default is off on Android low-RAM devices and on elsewhere; an explicit choice wins and survives sign-out | keep |
| PERF-19 Home background back to display resolution (`TV-CLR-01`, `QA-08`) | SEI Box R 4K Plus, default fixture, row browse, 3 cold runs | `63303a6` → same branch | janky %, frame median, GPU median; edge energy of a 640×640 right-side crop | 960×540 baked: 8.8%, 19.7 ms, 9.5 ms; edge energy 0.265 | display-size baked (1920×1080 here): 8.8%, 18.8 ms, 9.4 ms; edge energy 0.308 | same speed, 16% more on-screen detail | the owner saw the quarter-resolution background as soft; it now bakes at the drawn size (4K on 4K interfaces) and skips Coil's memory cache; artwork-free fallbacks stay at half resolution | keep |

## Tradeoffs recorded

- PERF-17: Home rows reveal only as an in-order settled prefix (user
  decision), so a very slow add-on still delays the rows after it. They are
  already loaded when it answers, and the request starts about 6 rows
  earlier than before. Inserting sources above the focused row was not
  exercised on the TV, because fixture add-on 1 answers first; the unit
  test and lazy-list key anchoring cover it. One run started before Home
  had loaded and was discarded; three fresh runs replaced it.
- PERF-16: Discover could not be measured; the default fixture has no
  Discover content. Settings sections still cost about 150 ms to build once
  focus rests; row-level work there was judged not worth the churn for a
  rarely used screen. Direct `perfetto -a <pkg>` tracing works on the SEI
  Box even though Macrobenchmark's does not.
- PERF-15: pivot scrolling needed no change. Compose foundation 1.11.3
  already applies `PivotBringIntoViewSpec` on leanback devices. The
  Macrobenchmark frame metric cannot run on the SEI Box (its firmware
  records no app RenderThread slices), so real-TV numbers come from
  `dumpsys gfxinfo`. The ambient composite and hero scrim cost about 3 MB
  of bitmaps in exchange for removing four and two blended passes.
- TV hero drift (PERF-14) pauses while focus is in the rows below the hero and
  resumes from the same frame; the approved hero delay, crossfade, drift and
  focus treatment are unchanged. Emulator timings are relative: SwiftShader
  exaggerates fill cost and the host GPU hides it, so physical low-end TV
  frame times remain unmeasured.
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
