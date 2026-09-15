# Lamphaus performance overhaul: implementation guide

## 1. Mission and audit status

Make startup, browsing, search, and playback consistently responsive on low-end phones and TVs while bounding memory, network use, and battery cost. Optimize useful work per resource consumed; maximizing CPU utilization is not the objective.

**Scope:** mobile, TV, playback, and shared UI/data infrastructure. Keep platform navigation, layout, focus, and motion behavior separate.

**Audit:** 2026-09-15, source commit `aff8274b402490d4bdfa85fe5f3f98250fca07b6`. This is a source-level assessment and execution plan, not a measured performance report. No app code was changed and no runtime benchmark or test suite was run for this audit. ADB listed phone and TV emulators; no physical target was connected. There are no measured speedup, memory, or energy claims below.

Before implementation, read `AGENTS.md`, `RULES.md`, and relevant child instructions again. Read `docs/DEVELOPMENT_AND_RELEASE.md` before profile/release artifact work. Prefix shell commands with `rtk`; bound Gradle to two workers. The root `RTK.md` reference resolves to `/Users/furkan/.codex/RTK.md` in the audited environment.

### Required rules

- Architecture and concurrency: `SHR-ARC-02`–`08`, `SHR-ARC-10`–`16`; preserve immutable state, repository boundaries, main-safe operations, lifecycle ownership, and offline recovery.
- Privacy: `SHR-PROD-06`; traces and benchmark reports must not contain provider URLs, credentials, queries, tokens, stream locations, or account identifiers. Use synthetic fixtures and fixed operation labels.
- Mobile: `MOB-LAY-01`–`03`, `MOB-LAY-11`, `MOB-CMP-08`–`09`, `MOB-MOT-03`.
- TV: `TV-FOC-01`–`02`, `TV-NAV-03`, `TV-NAV-05`–`07`, `TV-CNT-02`, `TV-MOT-01`, `TV-ART-01`.
- Playback: `PLY-IMM-04`, `PLY-PIP-01`–`05`; retain session ownership and continuity.
- Verification: `QA-01`–`08`, with `QA-08` the performance gate. Profile provenance: `REL-04`. Production packaging/publication: `REL-01`–`09` when a release is separately requested.

## 2. Findings and priorities

Paths below are relative to the repository root. Search by symbol if lines move. “Confirmed” means the implementation pattern was inspected, not that its runtime cost was measured.

| ID | Priority / confidence | Evidence | Consequence to investigate |
| --- | --- | --- | --- |
| PERF-01 | P0, confirmed measurement gap | `app/build.gradle.kts`, `androidComponents.finalizeDsl`: both `benchmarkRelease` and `nonMinifiedRelease` set `isMinifyEnabled = false`, with shrinking also disabled | Existing benchmarks do not represent the optimized production artifact. |
| PERF-02 | P0, confirmed coverage gap | `benchmark/.../BaselineProfileGenerator.kt`, `televisionDetailsAndPlayback`: D-pad down, Select, Back only; no playback assertion. `StartupBenchmark.kt` lacks mobile scrolling/search/playback timing and TV uncompiled startup comparison | Profile generation and startup tests cannot establish end-to-end performance. |
| PERF-03 | P1, confirmed dispatcher gap | `core/data/.../repository/RoomLibraryRepository.kt`: PIN hashing uses 120,000-round PBKDF2; provider encryption/decryption and JSON mapping have no repository dispatcher boundary. `AppViewModel.init` collects repository flows from `viewModelScope` | CPU work and keystore calls can execute on Main. Room moving SQL off Main does not move downstream `map` transformations. |
| PERF-04 | P1, confirmed memory/caching gap | `core/provider/.../HttpProviderClient.kt`: unbounded `ConcurrentHashMap<String, CacheEntry>` stores full bodies; `response.body.string()` has no explicit size cap; 304 responses reparse bodies | Retained data grows with distinct requests, including search/pages. Large responses create allocation spikes. |
| PERF-05 | P1, confirmed dispatcher gap | Same client: `fetch` switches to IO for DOM parsing, but `catalog`, `meta`, `streams`, and `manifest` map the returned DOM after resuming the caller | Large model conversions can run on Main despite asynchronous networking. |
| PERF-06 | P1, confirmed latency structure | `AppViewModel.searchContent`: cancel + 300ms delay already exist, then manifest `awaitAll`, catalog `awaitAll`, then one publication. Other fan-out exists in `ProviderAggregator`, detail/source work, and `PlayerActivity` | Slow providers delay fast results; multiple operations can queue excessive work. Home's semaphore does not govern other paths. |
| PERF-07 | P2, confirmed structure; cost unmeasured | `MobileApp` and `TvApp` collect a broad `AppUiState`; `MediaArtwork` resolves/copies models in composition; several browse filters are computed inline | Unrelated updates may invalidate broad scopes or recreate inputs. Compiler reports and traces must determine actual recomposition cost. |
| PERF-08 | P1 investigation, confirmed tradeoff | `PlayerActivity.onCreate` clears Coil's entire memory cache; `Media3EngineFactory` already uses a 32 MiB target and 10–30s buffer durations | Cache clearing protects playback memory but can make return-to-browse decode-heavy. Do not remove the protection without low-memory evidence. |
| PERF-09 | P2, confirmed recurring work | `PlaybackScreen` polls every 500ms; `PlayerActivity` has display/progress loops and broad settings collectors; `VideoCadenceEstimator.onFrame` sorts/filters 60 boxed intervals per frame after warm-up | Possible unnecessary wakeups, repeated loading, and render-thread allocations. Measure before changing cadence. |
| PERF-10 | P1, confirmed resource gap | `SidecarSubtitleLoader`: blocking `HttpURLConnection`, unbounded `readBytes`, retry, disconnect outside `finally` | Cancellation may wait for blocking IO, oversized subtitle files allocate heavily, exceptional paths may delay connection cleanup. |
| PERF-11 | P2, confirmed scale exposure | `LamphausDao.observeLibrary/observeProgress` select full profile collections; repository decodes preview JSON for emitted rows | Progress writes can trigger repeated full-list decoding as history grows. Composite profile/time indexes already exist. |
| PERF-12 | P2, startup hypothesis | Lazy `LamphausApplication.container` creates a broad eager dependency graph when accessed. Application schedules `MetadataSyncWorker`, whose `doWork` currently returns success without syncing | Trace initialization and worker overhead; lazy construction alone does not make the first access cheap. |
| PERF-13 | P0 correctness prerequisite, static risk | `DelayAudioProcessor.queueInput` requests `remaining` output bytes, writes leading silence, then writes all `remaining` input bytes | Positive delay can exceed the output buffer's remaining capacity. A previously larger reused buffer can mask this. Reproduce with a fresh processor before audio performance tuning. |

### Preserve existing strengths

Keep Home's bounded window/semaphore, per-row results, generation/cancellation protection, existing 300ms search debounce, lifecycle-aware UI collection, stable lazy keys, 24-entry recent-detail cache, TV palette LRU and delayed hero work, constrained TMDB artwork URLs, graphics-layer motion, Room profile/time indexes, sticky completion transaction, release R8/resource shrinking, and existing baseline/startup profile integration. Improve these where measurements justify it; do not replace them just to introduce a fashionable abstraction.

## 3. Phase 0 — establish trustworthy measurements

**Dependency:** first phase. **Rules:** `QA-01`, `QA-05`–`08`, `SHR-PROD-06`, `REL-04`.

1. Separate profile generation from performance measurement. Keep a non-minified fixture target for source-name profile generation. Make the timing target mirror production R8/resource optimization while retaining an isolated package, test signing, and synthetic data. Verify the installed package is non-debuggable/profileable as required and actually optimized. Confirm generated profile rules reach the timing APK.
2. Keep fixture startup and production-like startup as distinct experiments. Current fixtures disable cloud and updates; they cannot measure those initialization costs. Introduce injectable deterministic startup dependencies with production-equivalent construction where feasible, plus a separately labeled controlled integration run. Do not benchmark personal accounts.
3. Fix the TV profile journey to assert details, start the fixture clip, verify first video output, and return to the originating focus target. Opening a destination is not proof of playback.
4. Add explicit time-to-usable-content traces alongside time to initial display (TTID) and fully drawn (TTFD). TV currently calls `ReportDrawnWhen` when account loading ends; verify readiness includes usable content or an actionable empty/error state. Mobile's bounded startup timeout also needs a distinct degraded-readiness result. Never wait for every remote poster to report readiness.
5. Add fixed-name trace spans for dependency initialization, repository decode/hash, manifest/catalog work, first usable row, query-to-first-result, source resolution, controller connection, first video frame, and return-to-browse. Use monotonic time. Keep diagnostic listeners cheap and removable.

### Scenario matrix

| Scenario | Fixtures / procedure | Measurements |
| --- | --- | --- |
| Cold and warm startup, both platforms | Signed out, resident account, populated library, empty/error state; warm/cold disk caches separately | TTID, TTFD, usable-content p50/p95, Main-thread slices |
| Home and Discover | Small set and stress fixture: 20 providers, 100 rows, paginated items; one delayed/failing provider | Frame timing, request concurrency, first-row latency, peak memory |
| Search | Type, replace query rapidly, clear, navigate away; providers at 50ms/500ms/timeout | First result and settled result p50/p95, cancellations, request count |
| Mobile scrolling / TV D-pad | Vertical rows plus horizontal traversal, long repeat-key burst, details and return | Frame overrun distribution, hitch count, input-to-focus, focus restoration |
| Library / progress | 100, 1,000, and 10,000 synthetic entries; periodic progress update during browsing | SQL/decoding time, allocations, update-to-visible latency |
| Artwork | Cold/warm cache, large source image, missing art, rapid hero changes | Decode dimensions/time, hit rate, Java/native/graphics memory |
| Playback | Local clip, controlled HLS/DASH and high-bitrate file; Media3 and supported MPV fallback | Selection-to-first-frame, decoder init, dropped frames, rebuffer count/duration, seek latency |
| Lifecycle / soak | 20 browse→play→return cycles; 30-minute playback; idle foreground/background | Retained objects, memory trend, CPU/wakeups, thermal state, progress correctness |

Use a physical low-memory TV and a low-end phone as primary targets, plus a representative modern phone. Emulators validate journeys and correctness, not release performance. Record model, RAM, OS, refresh/display mode, build SHA, APK SHA, fixture revision, compilation mode, cache/network state, and thermal conditions. Media frame drops and Compose frame overruns are different metrics.

Run at least 20 startup iterations, preserving the existing convention. Repeat matched before/after batches; investigate outliers and thermal drift. Use enough samples before interpreting p95; do not derive a credible p99 from 20 starts. Keep raw benchmark JSON and Perfetto traces. Profile detailed allocations separately because profiler overhead can distort timing.

### Proposed gates, to calibrate after baseline

- Zero reproducible ANRs, OOMs, audio-buffer exceptions, broken focus, lost final progress, or duplicate players in the matrix.
- Changed high-priority journey: target at least 15% improvement in its primary latency or peak-memory metric, without material regressions elsewhere. This is an engineering target, not an expected result.
- Flag matched-run p50/p95 regressions above 5%; repeat to distinguish noise before accepting/rejecting. Record justified tradeoffs explicitly.
- At 60Hz the frame interval is about 16.7ms; at 120Hz about 8.3ms. Evaluate deadline overruns at the actual device rate, not an arbitrary universal “60 FPS” claim. Aim for non-positive p95 frame overrun in stable browse interactions on supported measurement targets; calibrate with the baseline.
- Cache memory must stay within a documented byte budget; repeated identical soak cycles must plateau after warm-up. Separate managed, native, graphics, and decoder memory. The 32 MiB Media3 target is not a whole-process memory cap.
- First search result must be publishable before a delayed provider finishes; obsolete queries must not publish. Background idle must not retain screen-only polling.

References: [Macrobenchmark setup](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview), [metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics), [baseline profiles](https://developer.android.com/topic/performance/baselineprofiles/overview).

## 4. Phase 1 — make data work main-safe and bounded

**Targets:** `RoomLibraryRepository`, `HttpProviderClient`, `SidecarSubtitleLoader`. **Rules:** `SHR-ARC-04`, `SHR-ARC-11`, `SHR-ARC-15`, `QA-05`, `QA-08`.

### Repository dispatcher ownership

Inject shared IO and CPU dispatchers with production defaults. Move PBKDF2 and substantial JSON/model transformation to the CPU dispatcher; put blocking keystore/storage work on IO. Add `flowOn` after expensive upstream transformations, not after state publication. Room suspend SQL is already scheduled by Room; the missing boundary is surrounding application work.

Audit both reads and writes, including `saveProvider`, `saveLibrary`, `saveProgress`, PIN creation and verification. Keep PIN work-factor strength unchanged. Clear supplied PIN arrays in `finally`, including error and early-return paths. Avoid moving mutable UI state or Media3 calls to worker threads.

**Acceptance:** deterministic repository tests exercise main-safe entry points, dispatcher injection, cancellation and malformed JSON; device traces show no hashing, blocking keystore work, or large decode/mapping work on Main.

### Provider requests and cache

1. Put complete decode-to-domain conversion behind the provider/data-layer dispatcher boundary. Suspended network IO does not automatically move subsequent code off Main.
2. Replace the unbounded body map with a thread-safe byte-budgeted cache, with count and per-entry limits as secondary protections. Account for representation overhead and in-flight bodies; a count-only LRU is insufficient.
3. Bound streamed response bytes even when Content-Length is absent or inaccurate. Return a local recoverable failure for oversized/malformed payloads. Do not silently truncate catalog data.
4. Define freshness, validation, stale-on-error, and eviction policies by resource. Preserve ETag/Last-Modified support. Refresh validation timestamps after a successful 304. Avoid serving expired playable URLs from a general metadata cache.
5. Add request coalescing for identical simultaneous requests. Define cancellation ownership: one canceled subscriber must not kill another subscriber's shared request; when nobody needs it, cancel and remove in-flight state.
6. Include provider configuration/auth scope in identity. Invalidate on provider change/removal and account transitions. Never emit sensitive cache keys to diagnostics. Evaluate persistent metadata caching separately with explicit retention/privacy policy.
7. Only add parsed-result caching if repeated parse cost warrants its additional memory and correct provider-specific model identity.

**Acceptance:** HTTP tests cover concurrent duplicate requests, byte/count eviction, oversized chunked bodies, 304 freshness, stale fallback, redirects, auth/config changes, cancellation, and response closure. A 30-minute search/browse fixture stays bounded.

### Subtitle IO

Use a cancelable transport or explicitly bridge cancellation to connection shutdown. Always close streams and disconnect in `finally`; cap decoded/downloaded bytes, check cancellation before retry, and keep parsing off Main. Preserve required per-source headers and subtitle formats. Verify repeated track changes leave no obsolete request or cue set active.

## 5. Phase 2 — improve first-result latency and state ownership

**Targets:** `AppViewModel`, `HomeCatalogLoader`, `ProviderAggregator`, source/detail paths in `PlayerActivity`. **Rules:** `SHR-ARC-02`–`08`, `SHR-ARC-10`–`15`, `SHR-PROD-04`, `TV-CNT-02`, `QA-05`, `QA-08`.

- Introduce a shared request admission policy with global and per-provider limits and priorities: explicit playback/search before speculative prefetch. A semaphore controls outstanding work; dispatcher parallelism alone does not bound suspended HTTP requests. Start conservatively and tune from queue latency and memory traces.
- Preserve search's existing debounce/cancellation. Publish prepared sections and completed results incrementally, maintaining deterministic row order. Use query/profile/provider generation identity to reject stale results. Localize each provider's failure.
- Give manifest discovery bounded time and independent progress where provider ordering permits. Home's `awaitAll` discovery can still wait for a slow manifest; preserve stable row geometry and focus while improving readiness.
- Preserve source-ranking and explicit user-choice semantics when making resolution incremental. A faster result must not silently select a different source or start multiple players.
- Extract one coherent state owner at a time: Home, Search/Discover, Details, Library. Shared session/preferences remain shared. Expose narrow immutable flows and collect only where needed. Do not perform a whole navigation rewrite as a performance prerequisite.
- Use `stateIn(..., SharingStarted.WhileSubscribed(5_000), ...)` for screen-derived streams where appropriate. Define exceptions for progress persistence, account/session work, and active playback explicitly. UI lifecycle collection does not automatically stop work launched in ViewModel `init`.
- Project settings to the relevant fields and apply `distinctUntilChanged` before costly side effects. In particular, unrelated settings should not reload playback segments or reapply device configuration.

**Acceptance:** fake-provider tests cover fast/slow/failing providers, generation switches, rapid typing, cancellation during admission, partial retries, and bounded concurrency across simultaneous Home/search/source work. Existing `HomeCatalogLoaderTest`, `CatalogRefreshPolicyTest`, `SourceResolverTest`, and provider tests remain valid.

## 6. Phase 3 — reduce rendering and artwork cost

**Targets:** `MobileApp`, `MobileHome`, `MobileDetail`, `TvApp`, `TvComponents`, `TvContentAmbient`, `Artwork`, `KenBurns`. **Rules:** matching mobile/TV rules in section 1; `SHR-ARC-14`, `QA-02`–`05`, `QA-07`–`08`.

1. Enable Compose compiler reports for the diagnostic build and inspect affected hot composables. The repository uses Kotlin 2.3.10; strong skipping is already the modern default, so verify configuration instead of adding obsolete compiler flags. An ordinary `List` parameter does not prove a composable always recomposes.
2. Pass narrow screen/row inputs with stable identity. Move demonstrated expensive filters, grouping, sorting, and artwork resolution outside frequently recomposing bodies, or `remember` them with complete keys. Do not cache calculations with stale artwork/profile inputs.
3. Preserve existing item keys. Add `contentType` for heterogeneous lazy content where reuse benefits; use semantic item types rather than unique IDs. Profile before changing prefetch distances. Stop catalog/image prefetch at a bounded viewport lookahead.
4. Keep animation/scroll reads in layout/draw/graphics-layer lambdas where possible. Use `derivedStateOf` for threshold changes, not trivial calculations. Stop decorative work when invisible; honor reduced motion and approved TV timing.
5. Keep Coil `AsyncImage` constraint-aware sizing. Audit actual decode dimensions for provider art, not just constrained TMDB overrides. Do not introduce original-size painter loading or expensive subcomposition in scrolling rows.
6. Measure a process-shared image-loader budget together with playback/native memory. Compare current full-cache clearing against smaller cache budgets or targeted retention under pressure. Keep the existing clear until the alternative survives low-memory playback and return navigation.
7. Preserve the palette LRU, software-bitmap requirement for palette extraction, and hero delay. Measure cancellation on rapid focus changes, duplicate palette/hero decodes, and temporary coexistence of crossfade images. Reduce redundant work without changing approved TV focus/art behavior.

**Acceptance:** same-device frame-time and decoded-memory comparisons improve targeted scenes; artwork replacements, long labels, accessibility, reduced motion, mobile resizing, and TV focus/Back restoration pass. Recomposition count alone is not the success metric.

References: [Compose performance practices](https://developer.android.com/develop/ui/compose/performance/bestpractices), [strong skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping), [Coil Compose sizing](https://coil-kt.github.io/coil/compose/).

## 7. Phase 4 — playback correctness, memory, and steady-state cost

**Targets:** `DelayAudioProcessor`, `Media3EngineFactory`, `LamphausPlaybackService`, `MpvPlayer`, `PlaybackScreen`, `PlayerActivity`, `VideoCadenceEstimator`. **Rules:** `PLY-IMM-04`, `PLY-PIP-01`–`05`, `QA-06`–`08`.

First reproduce PERF-13 with a fresh processor, positive delay, and known PCM bytes. Fix output capacity/input consumption so injected silence plus copied audio cannot overflow and no samples disappear. Specify negative-delay behavior explicitly. Test zero/positive/negative settings, chunk sizes, channels, repeated flushes, and supported format changes before tuning allocations.

Then:

- Capture decoder initialization, first frame, rebuffer duration, dropped frames, and engine handoff using lightweight Media3 analytics and equivalent MPV instrumentation. Keep logs free of source URLs and headers.
- Audit one-player ownership, surface/controller release, native handle/event thread shutdown, and canceled activity jobs over repeated handoffs. Capture managed and native retention separately.
- Keep Media3 access on its application looper. UI-only progress sampling should depend on visibility/lifecycle; final persistence must survive teardown. Preserve PiP/media-session playback and sticky completion. Replace periodic scans with player callbacks where reliable; retain cadence/stability timing where required.
- Microbenchmark the cadence estimator. If material, use a bounded primitive ring/scratch buffer and compute at an appropriate cadence while preserving discontinuity/VFR detection. Re-run `VideoCadenceEstimatorTest`; do not introduce unsynchronized access across render/Main threads.
- Compare the existing 32 MiB target against controlled alternatives across bitrate/network/device classes. Report startup/rebuffer/memory tradeoffs. Do not globally enlarge buffers, force software decode, or force tunneling/passthrough as a generic performance fix.
- Audit MPV demux/cache options and native allocations before tuning them. Hardware capability and selected user settings remain authoritative. Test subtitles, seek, HDR/display-rate changes, audio routes, fallback, and next-episode behavior.

**Acceptance:** no audio overflow, retained dead player/activity, increased rebuffering, lost final progress, or broken PiP/Cast behavior; document both engines' memory and first-frame results. [Media3 analytics reference](https://developer.android.com/media/media3/exoplayer/analytics).

## 8. Phase 5 — large collections, startup, and idle work

**Rules:** `SHR-ARC-03`–`05`, `SHR-ARC-10`–`15`, `QA-04`–`08`.

### Large local collections

Measure query plans and JSON decode separately at the fixture sizes. Existing `(profileId, updatedAtEpochMillis)` indexes already support the principal ordering; add indexes only for demonstrated query needs. Use bounded Continue Watching queries and paged Library presentation when scale warrants it. Keep full data available to synchronization through a separate API; never truncate sync state to a visible page. Avoid decoding an unchanged preview on every position update if measurements show this dominates. Add migration and reconciliation tests for any schema change; preserve sticky completion and deletion semantics.

### Startup initialization

Trace first `AppContainer` access and dependency construction. Defer optional integrations until first use or after usable content only when their lifetime allows it; keep dependencies needed for immediate auth, playback, or restoration ready. Avoid replacing synchronous initialization with an unbounded burst of background jobs. Inspect Cast and WorkManager initialization before changing them.

`MetadataSyncWorker` currently schedules a periodic no-op. Decide whether to remove unnecessary scheduling or implement its intended repository-owned work in a separate functional change. Measure startup/idle consequences; do not claim it currently provides background sync.

### Idle ownership

Inventory polling, listeners, subscriptions, retries, and application scopes. Establish foreground/background ownership for each. Coalesce durable progress writes without losing terminal saves. Verify backoff and cancellation under repeated failures. If implementation reaches cloud synchronization code, load the repository's applicable Supabase skill first and preserve reconciliation/auth behavior.

**Acceptance:** startup traces identify reduced critical-path work, large-library scrolling remains bounded, process recreation restores state, and idle CPU/wakeups improve without losing persistence.

## 9. Kotlin implementation rules for the executing agent

- Make expensive suspend APIs main-safe at the owning layer; inject dispatchers. CPU transforms use Default-like scheduling, blocking IO uses IO-like scheduling. `suspend` and `async` do not inherently move work off Main.
- Use structured scopes, cancel obsolete work, and rethrow `CancellationException` before generic exception handling. Audit `runCatching` around suspending work rather than blindly replacing every occurrence.
- Bound request fan-out, buffers, caches, queued work, and retries. Reuse clients and serializers at appropriate lifetimes. Do not create dispatchers/clients per item.
- Use immutable externally visible state. Keep expensive calculations and side effects outside `MutableStateFlow.update` lambdas, which may retry. Confine mutable caches or synchronize their complete compound operations.
- Choose data structures from access patterns: map/set for repeated lookup, bounded primitive arrays for proven hot numeric loops. Avoid repeated sorting/decoding and avoid unnecessary intermediate collections on measured hot paths.
- Do not mechanically convert every list pipeline to `Sequence`; small pipelines can become slower. Do not blanket-apply `inline`, `@Stable`, or `@Immutable`. An annotation is a correctness contract, not an optimization switch.
- Do not weaken PIN hashing, remove useful content, reduce supported image fidelity blindly, or conceal loading time behind longer animation to improve a number.
- Prefer constructor injection and focused extraction. Adding Hilt, Paging, modules, or a domain layer must solve a demonstrated ownership/scale problem; it is not independently evidence of a speedup.

References: [Android coroutine guidance](https://developer.android.com/kotlin/coroutines/coroutines-best-practices), [Kotlin sequences and overhead](https://kotlinlang.org/docs/sequences.html).

## 10. Execution order, verification, and handoff

Implement one reviewable change at a time:

1. `PERF-01/02`: benchmark parity, truthful readiness, complete journeys, baseline report.
2. `PERF-13`: audio correctness reproducer and fix.
3. `PERF-03/05`: repository/provider dispatcher boundaries and focused tests.
4. `PERF-04/10`: bounded caching, response/subtitle limits, cancellation and cleanup.
5. `PERF-06`: request admission and incremental search/provider results.
6. `PERF-07/08`: measured Compose/artwork changes.
7. `PERF-09/11/12`: measured playback loops, collection scaling, startup/idle changes.
8. Regenerate affected profiles, rerun matched measurements, and assemble final evidence.

For each step record: hypothesis, scope/rule IDs, source paths, before data, implementation, tests, after data, tradeoffs, and keep/revert decision. Keep old benchmark data so later changes cannot erase regressions. Revert optional tuning that yields no reproducible benefit. Correctness/resource-bound fixes remain valuable even when median timing is unchanged.

### Commands and artifacts

From the repository root, inspect actual tasks before choosing variant-specific benchmark commands:

```bash
rtk git status --short
rtk ./gradlew :benchmark:tasks --all --max-workers=2
rtk ./gradlew :app:tasks --all --max-workers=2
rtk proxy adb devices -l
```

Select the correct phone or TV explicitly using `ANDROID_SERIAL` for connected-device tasks. Use the task names discovered above and an instrumentation class/method filter; do not run phone-only journeys on the TV or vice versa. Exact commands must be recorded in the resulting report after variant separation. Keep fixture packages separate from installed production accounts.

Relevant local validation commands (run those affected, then required gates):

```bash
rtk ./gradlew :core:model:test :core:provider:test :core:data:testDebugUnitTest :core:player:testDebugUnitTest :app:testDebugUnitTest --max-workers=2
rtk ./gradlew :app:lintDebug --max-workers=2
rtk ./scripts/check-neutrality.sh
```

Confirm task availability if module plugins change. Run connected instrumentation for modified Room migrations, playback behavior, and platform UI. Preserve `TvNavigationBehaviorTest` and the named **TV text-field browse/edit flow** manual gate (`TV-NAV-03`, `QA-07`). Apply `QA-02`–`04` for changed mobile layout/input and `QA-06` for playback. Unit tests cannot establish frame-time or decoder performance.

Create `docs/performance/baseline.md` and `docs/performance/results.md` during execution. Store large raw captures in an appropriate ignored artifact directory, referenced by path/hash from those reports. Each results row must contain:

| Change / rule IDs | Device / scenario | Before / after SHA | Metric and samples | Before | After | Difference | Trace / test evidence | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Populate from actual runs | | | | | | | | |

After startup/navigation/dependency/home/playback-start changes, regenerate mobile and TV profiles independently, merge without losing either platform, record device/source provenance, and verify packaged profiles (`REL-04`, `QA-08`). Profile installation/runtime compilation and Startup Profile build-time layout are separate experiments.

This guide does not request version changes or publication. If subsequently releasing, follow `docs/DEVELOPMENT_AND_RELEASE.md` and `REL-01`–`09`: local signed production build, checksum and build report, verified GitHub download, signed feed, public download, and website agreement. GitHub Actions must not build, sign, or publish production APKs.

### Completion checklist

- [ ] Baseline is measured on physical mobile and TV targets, or unavailable targets are explicitly marked incomplete.
- [ ] Every confirmed issue is fixed or has an evidence-backed disposition.
- [ ] Timing artifacts mirror production optimization; profile journeys assert real outcomes.
- [ ] Memory and request bounds hold under stress and cancellation.
- [ ] Startup, search, browse, playback, return, and idle results are compared on matched conditions.
- [ ] Required correctness/accessibility/focus/lifecycle tests pass.
- [ ] Changed startup profiles and provenance are verified.
- [ ] Final report states measured improvements, regressions, unresolved risks, and exact reproduction commands without unsupported “fully optimized” claims.
