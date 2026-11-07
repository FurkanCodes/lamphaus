# How Nuvio's playback differs from Lamphaus

Compared on 2026-11-07: NuvioTV at `fe5c314` (NuvioMedia/NuvioTV) against Lamphaus 1.0.0-beta.52, measured on the SEI Box R 4K Plus. Nuvio is GPL-3.0, so these are behaviour notes only and no code was copied (`core/player/NOTICE.md`).

## Summary

- **Mid-playback buffering.** With "Custom Playback Buffers" and "ExoPlayer Native Memory" off, as the user reports for their Nuvio, the two apps stream almost identically: same load control, same HTTP client setup. The code does not explain why Lamphaus stalled and Nuvio did not.
- **The symptom went away on 2026-11-07 with no code change.** That points to the network or the provider, but it is not proven. The two apps were never measured side by side.
- **The margin on a remux is still thin.** One connection on this box tops out near 120 Mbit/s and a 4K remux pulled about 100 Mbit/s. Any dip can stall it again, in either app.
- **The clear differences are at startup and in subtitles.** Nuvio starts the stream without waiting for add-on subtitles and shows them outside the player. Lamphaus waits first, and a slow subtitle host can hold back its video.

## Measured on the SEI

The box is an Amlogic S905X4 with a 32-bit userspace, Android 14 and 3.4 GB of RAM, on Wi-Fi 6.

### Network

| Test | Result |
| --- | --- |
| Round trip to the stream host | 70 ms |
| TV, 1 connection | 117–129 Mbit/s |
| TV, 2 connections to one host | 234 Mbit/s |
| TV, 3 connections to 3 hosts | 295 Mbit/s |
| Mac on the same network, 1 connection | 369 Mbit/s |

The cap on one connection comes from the box's kernel, not the internet line. `tcp_rmem` tops out at 2 MiB, which with `tcp_adv_win_scale=1` is a window of about 1 MiB, and 1 MiB per 70 ms is about 120 Mbit/s. `rmem_max` is 262144, so an app cannot raise it with `SO_RCVBUF`. Only more connections get past it. A public test host answered 429 above two connections per host, so the count matters.

### Buffer and streams

| Stream | Download rate | Ahead of playback |
| --- | --- | --- |
| 3840×1920 DV WEB stream, E-AC-3 | 25–30 Mbit/s, steady | About 40 s (measured) |
| 2160p DV remux, DTS-HD | 100–124 Mbit/s for about 100 s | About 12 s (calculated) |

Media3's stock byte target is about 144 MiB, so the buffer is a fixed size rather than a fixed time. A higher bitrate means fewer seconds. On the remux the download thread averaged 32% of a core, peaking near 50%, with 114 of 400 CPU idle, so the box's CPU was not the limit.

### Startup

The journal's startup figure begins at the engine, so it misses everything before that.

| Play pressed → engine begins | Engine begins → first frame | Total |
| --- | --- | --- |
| 2.2 s | 3.5 s | 5.7 s |
| 5.1 s | 4.8 s (remux) | 9.9 s |
| 2.2 s | 2.0 s | 4.2 s |
| 1.5 s | 2.2 s | 3.7 s |

## Where the two match

On the default path, with the switches above off:

| Area | Both apps |
| --- | --- |
| Load control | Stock `DefaultLoadControl` with a 1.5 s back buffer |
| HTTP client | OkHttp, HTTP/1.1 only, IPv4 first, 64 requests and 32 per host, 15 s timeouts |
| Load errors | Six retries; 400, 401, 403, 404 and 410 fail fast |
| Frame rate strategy | Media3's own switching off |
| Parallel downloads | Off by default, 2–4 connections, 16 MiB chunks |

Lamphaus's parallel download and native memory options are behaviour ports of Nuvio's.

## Where they differ

| # | Area | Nuvio | Lamphaus | Effect on this box |
| --- | --- | --- | --- | --- |
| 1 | Add-on subtitles at start | Fetched in the background; playback does not wait | Stream waits for every add-on's list, up to 8 s (`ADDON_SUBTITLES_WAIT_MILLIS`). One hung add-on loses all of them | Likely the main part of the 1.5–5.1 s gap; not split out yet |
| 2 | Showing add-on subtitles | SRT, VTT and TTML are parsed outside the player and pushed to `SubtitleView`, so the video pipeline is never touched (`PlayerSidecarSubtitles.kt`). ASS stays in the player | All go through ExoPlayer as sidecar tracks | A selected subtitle can hold back video; see below |
| 3 | Media3 | Custom 1.8.0 build of several modules, including a patched extractor | 1.11.0 stock plus one off-heap patch to `media3-exoplayer` | Extractor patch below |
| 4 | MP4 | Extractor patch for `moov` handling and out-of-band subtitle prefetch (`da4a6e853`) | Stock extractor | Open lead: a 24 Mbit/s MP4 stalled after seeks in Lamphaus |
| 5 | Buffer options | Also offers Custom Playback Buffers: 15–45 s, 5 s to start, and time wins over the byte budget until the minimum is met (`BitrateAwareLoadControl`) | Native memory buffer only | Nuvio's own comment names this path as the fix for 100 Mbit/s remuxes that use up a byte cap. The user reports it off |
| 6 | Disk cache | Optional VOD disk cache, off by default | None | None while off |
| 7 | Sockets | `TCP_NODELAY`, and an 8 MiB `SO_RCVBUF` only when the box grants more than its own ceiling (`PlaybackSocketFactory`) | Neither | Almost none: this box refuses the larger buffer |
| 8 | Dolby Vision conversion | Native libdovi, off-heap | Kotlin port of libdovi, on the loader thread | Not a bottleneck at the load measured |
| 9 | Player control | Runtime controller drives ExoPlayer directly | `MediaController` to a session service | One extra connection step before the engine begins; cost not measured |
| 10 | TS streams | Sets HDMV DTS and a wider timestamp search in the extractor | Defaults | Only affects `.ts` sources |

### Proven: a selected subtitle holds back video in Lamphaus

Media3 advances only the loader furthest behind, so a selected subtitle still downloading freezes the video beside it. Time to start, on the emulator with a real player and a local server:

| Selected subtitle host | Lamphaus today | With the prototype patch |
| --- | --- | --- |
| Answers in 12 s | 12.3 s | 0.15 s |
| Returns 503 | 15.3 s | 0.16 s |
| Never answers | Never started in 32 s | 0.19 s |

Nuvio avoids this for SRT, VTT and TTML through item 2.

## Not proven

- **Nuvio was never measured on this box.** Everything about Nuvio above comes from its source.
- **Nuvio's settings are the user's report.** Nuvio logs `BUFFER_GATE` and `BUFFER_NETWORK` under the tag `PlayerViewModel` when playback starts, so one play under logcat shows the real path. Whether Parallel Connections is on is still unconfirmed, and it would explain a smooth remux on its own.
- **The remux's 12 s buffer and its bitrate of about 100 Mbit/s are inferred.** They come from the byte target and the download rate, not a direct reading on a remux.
- **The 1.5–5.1 s gap is not broken down.** It covers add-on manifests, subtitle lists, and connecting the player.

## What came of it

The buffering work resumed on 2026-11-07 and is on the branch `feature/streaming-and-tv-focus`, not yet released. `PLY-NET-01` and `PLY-SUB-01` in `RULES.md` hold what it does.

- Parallel connections and native memory are on by default, and parallel is off on metered networks.
- The native buffer is one 50 s target that tops up continuously, 2 s to start, with a 15 s back buffer where RAM allows.
- `connections` is a real limit, and prefetch no longer waits for playback to be ready.
- `0002-subtitles-never-hold-back-media.patch` for Media3, as `1.11.0-lamphaus.2`. On the emulator a slow subtitle host went from 12.3 s to 0.15 s, a 503 from 15.3 s to 0.16 s, and a host that hangs from never starting to 0.19 s.
- The stream no longer waits for slow add-on subtitles; late ones wait behind "Add N more subtitles" in the panel. This is simpler than Nuvio's virtual tracks.

Unit tests pass. The instrumented player tests pass except `MpvEnginePlaybackTest.playsThroughAMediaSession`, which fails the same way on untouched `main`.

None of it has run on the real TV or against a real debrid host yet, and Nuvio itself has still never been measured. The check that matters is one remux on the SEI, reading `dumpsys media_session` for position and buffered position, `/proc/net/dev` for download rate, and `ss -tn` for socket queue, then the same remux in Nuvio with logcat's `BUFFER_GATE` line.
