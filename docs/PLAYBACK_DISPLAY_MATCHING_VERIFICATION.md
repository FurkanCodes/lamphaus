# Playback display matching verification

Scope: playback / TV output selection. Audio processors, routing, passthrough,
track defaults, buffering, and audio timing are unchanged.

Rules: SHR-PROD-07 (localized status messages), SHR-ARC-03 and SHR-ARC-15 (testable platform boundary), SHR-ARC-10 and
PLY-IMM-04 (source/lifecycle continuity), QA-01, QA-06, QA-07.
Development is isolated on `codex/fix-display-matching` from current main (REL-01).
This is a source fix, not a release or production artifact.

## Automated coverage

- `TrackSelectionTest`: independent settings, unknown/invalid FPS, cropped HD/UHD
  source sizes, 25→50 and 24→48 cadence, fractional rates, seamless eligibility.
- `VideoCadenceEstimatorTest`: absent metadata, millisecond container timestamps,
  23.976 versus 24, 29.97 versus 30, 59.94 versus 60, insufficient samples,
  source resets and discontinuities.
- `PlaybackDisplayModeControllerTest`: format stability, subsequent renditions,
  confirmed versus refused physical switches, settings changes, original window
  preference restoration, seamless alternatives, independent resolution opt-in.

Verification command:

```sh
rtk ./gradlew :core:model:test :core:player:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug --max-workers=2
```

Result (2026-09-13): passed. 199 unit tests (74 model, 7 player, 118 app),
zero failures/errors/skips; Android debug compilation and lint passed.
`git diff --check` passed. Eighteen new regression tests cover this change.

## Required physical TV checks (not performed)

`adb devices` reported no connected devices. Unit tests simulate the display;
Android/HDMI acceptance and visible switching are not proven by these tests.

1. **QA-07 output matrix**: on a TV advertising 720p/1080p/4K, play matching
   sources and cropped 1920×800/3840×1600 films. Check the TV's actual signal
   information as well as in-app stream info.
2. **QA-06 cadence matrix**: 23.976, 24, 25, 29.97, 30, 50, 59.94, and 60 fps;
   verify exact rates or integer multiples, including a file without FPS metadata.
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

Android may decline a mode request based on hardware or system policy. Seamless
refresh changes on older Android versions without advertised alternative rates
remain delegated to Media3's existing surface hint. Missing FPS requires 60 video
intervals before estimation; mode changes then use the existing two-second
format-stability gate. No pause or audio delay is added for this process.

Reference: https://developer.android.com/media/optimize/performance/frame-rate
