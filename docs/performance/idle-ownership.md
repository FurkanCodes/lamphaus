# Idle and foreground work ownership

PERF-12/Phase 5 (`SHR-ARC-03`, `SHR-ARC-10`, `QA-04`, `QA-08`). Every
repeating loop, listener, and retry in the app/core sources is listed with its
owner and cancellation path. A device idle trace is still required to quantify
CPU/wakeups; this table establishes that nothing screen-only keeps running in
the background.

| Work | Location | Owner | Ends when | Foreground/background |
| --- | --- | --- | --- | --- |
| Playback progress sampling (500 ms) | `PlaybackScreen` | `LaunchedEffect`, keyed by visibility | chrome hidden, PiP off, or screen leaves composition | foreground-paused |
| Progress persistence pulse (10 s) | `PlayerActivity` | `applicationScope` job, canceled in `onDestroy` | activity destroyed | survives teardown by design; terminal saves intact |
| Display-mode/format tick (500 ms) | `PlayerActivity` | `lifecycleScope` job + `STARTED` guard | activity stopped/destroyed | foreground only |
| Audio route callback | `PlayerActivity` | activity, unregistered in `onDestroy` | route change or destroy | foreground only |
| Device binding retry (backoff 1s→30s) | `AppViewModel` | `viewModelScope` `collectLatest` | binding succeeded, terminal error, or ViewModel cleared | foreground+background while signed in; bounded backoff |
| TV pairing poll (3 s) | `AppViewModel` | `pairingPollJob`, replaced per session | sign-in, session expiry, new session, ViewModel clear | foreground QR screen |
| Update download poll (500 ms) | `UpdateCoordinator` | `downloadJob`, canceled by `cancelDownload()` | terminal download status | only during a user-initiated download |
| Auth retry (bounded attempts) | `SupabaseSessionRecovery` | caller's coroutine | success, terminal error, cancellation | request-scoped |
| Provider request admission/coalescing | `HttpProviderClient` | client-owned shared job | last waiter cancels | request-scoped, no idle work |
| Subtitle fetch | `SidecarSubtitleLoader` | caller's coroutine | completion or cancellation | screen-scoped |
| Preferences bridge (`Media3EngineFactory.deviceConfig`) | `AppContainer` | application scope `collect` | process death | intended: playback service needs it before any screen |
| Artwork palette LRU + hero delay | `TvContentAmbient` | composition + `LaunchedEffect` | invisible/hero change | foreground only |
| Periodic metadata sync | removed with `MetadataSyncWorker` (PERF-12) | — | — | no background sync is claimed |

Guidelines preserved: durable progress writes coalesce to the 10 s pulse plus
terminal saves; every retry is bounded or tied to a visible user action; no
screen-only polling survives backgrounding.
