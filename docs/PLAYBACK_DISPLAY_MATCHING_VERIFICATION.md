# Playback display matching verification

Scope: playback / TV output selection, explicit surface frame-rate voting, and
MPV video-cadence reporting. Audio processors, routing, passthrough, track
defaults, buffering, and audio timing are unchanged.

Rules: SHR-PROD-07 (localized status messages), SHR-ARC-03 and SHR-ARC-15 (testable platform boundary), SHR-ARC-10 and
PLY-IMM-04 (source/lifecycle continuity), QA-01, QA-06, QA-07.
Development is isolated on `codex/playback-frame-rate-matching` from current main (REL-01).
This is a source fix, not a release or production artifact.

## Automated coverage

- `TrackSelectionTest`: independent settings, unknown/invalid FPS, cropped HD/UHD
  source sizes, 25→50 and 24→48 cadence, fractional rates, seamless eligibility.
- `VideoCadenceEstimatorTest`: absent metadata, millisecond container timestamps,
  23.976 versus 24, 29.97 versus 30, 59.94 versus 60, insufficient samples,
  source resets and discontinuities.
- `PlaybackDisplayModeControllerTest`: format stability, subsequent renditions,
  confirmed versus refused physical switches, settings changes, original window
  preference restoration, seamless alternatives, independent resolution opt-in,
  explicit Always surface votes, and surface-vote restoration.
- `Media3EngineFactoryTest`: Media3 votes only for Seamless-only; Always disables
  Media3 voting so the activity's non-seamless surface request is authoritative.
- `MpvVideoFormatTest`: estimated MPV cadence, container-cadence fallback,
  unknown cadence, and invalid-dimension handling.

Verification command:

```sh
rtk ./gradlew :core:player:testDebugUnitTest :app:testDebugUnitTest --max-workers=2
rtk ./gradlew :core:player:lintDebug :app:lintDebug --max-workers=2
rtk ./scripts/check-neutrality.sh
```

Result (2026-09-18): passed with zero test failures; Android debug compilation,
core/player lint, app lint, provider-neutrality, and `git diff --check` passed.

## Required physical TV checks (not performed)

`adb devices` reported an API 36 TV emulator (`sdk_google_atv64_arm64`) and an
API 36 phone emulator, but no physical TV. Unit tests simulate the display;
Android/HDMI acceptance and visible switching are not proven by these tests.

1. **QA-07 output matrix**: on a TV advertising 720p/1080p/4K, play matching
   sources and cropped 1920×800/3840×1600 films. Check the TV's actual signal
   information as well as in-app stream info.
2. **QA-06 cadence matrix**: 23.976, 24, 25, 29.97, 30, 50, 59.94, and 60 fps;
   verify exact rates or integer multiples, including a file without FPS metadata
   and an MPV fallback item whose cadence comes from `estimated-vf-fps` or
   `container-fps`.
3. **QA-07 setting independence**: Off/Off retains system policy; resolution-only
   changes resolution at the current refresh; frame-rate-only preserves resolution.
   Seamless-only permits advertised same-resolution refresh changes. Explicit
   resolution opt-in also permits a resolution change at the current refresh.
4. **PLY-IMM-04 continuity**: next item, source replacement, sustained adaptive
   format change, Home/return, playback end, failure, and Back all preserve playback
   state and release the window override when leaving playback.
5. **QA-06 refusal and audio regression**: when the display rejects a request,
   stream info must not claim a match. Confirm existing audio tracks, delay,
   passthrough, subtitles, and session controls retain their previous behavior.
6. **QA-06/QA-07 named device check — Physical TV Always + MPV cadence**: on a
   named Android 12+ television and HDMI chain, verify that Always may perform a
   visible non-seamless switch, Seamless-only never does, both restore on stop,
   and a forced MPV fallback selects the expected output cadence. Record the TV,
   receiver, Android version, source cadence, and observed HDMI output mode.

Android may decline a mode request based on hardware or system policy. Android
12+ uses `CHANGE_FRAME_RATE_ALWAYS`; Android 11 has no such strategy argument and
receives the two-argument seamless hint while the physical display-mode path
remains active. Missing Media3 FPS requires 60 video intervals before estimation;
MPV uses its estimated cadence with container cadence as fallback. Requests then
use the existing two-second format-stability gate. No pause or audio delay is
added for this process.

Reference: https://developer.android.com/media/optimize/performance/frame-rate
