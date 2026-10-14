# Playback diagnostics

How to find out why a playback dropped (`SHR-PROD-18`). Every device keeps a journal of its last 30 playbacks and crashes. Viewers who opt in also send crashes and bad playbacks to Sentry.

## What a report holds

One JSON file per playback (`kind: PLAYBACK`) or crash (`kind: CRASH`), written while it happens:

- `playback`: the engine setting and the engine it started on, stream type (`progressive`, `hls`, `dash`), whether it resumed, the traits the add-on's label claims, and the device's playback settings.
- `media`: the decoded container, video, and audio formats.
- `events`: a timeline in milliseconds since the start. It covers readiness, first frame, buffering (with buffer level and bandwidth), seeks, and non-viewer pauses (audio focus, output removed). It also covers errors (code name, HTTP status, renderer and decoder, cause classes) and load errors. Then retries, stall skips, the Dolby Vision base-layer fallback, engine fallbacks, decoders and the audio output, dropped-frame bursts, audio underruns, network changes, low memory, background and foreground, and the close.
- `totals`: startup time, rebuffers and their time, play time, dropped frames, underruns, errors, retries, seeks.
- `outcome`: `ENDED`, `CLOSED`, `CLOSED_WHILE_LOADING`, `CLOSED_WHILE_BUFFERING`, `FAILED`, `CLOSED_IN_BACKGROUND`, or `PROCESS_DIED`.
- `closeReason`: why the player closed (`viewer` for Back or Close, `switched playback`, `up next declined`, `end screen timed out`, …). Android 13+ hands Back to the app as a gesture without its key, so which remote sent it is not knowable.
- `exit`: when the process died, Android's reason from the next launch (`CRASH`, `CRASH_NATIVE`, `ANR` with the main thread's stack, `LOW_MEMORY`, …), with memory use.
- `crash`: exception classes and stack frames, never messages.

Reports never hold URLs, hosts, headers, add-on names, titles, or exception messages (`SHR-PROD-06`).

A playback counts as **bad** when any of these happened (`PlaybackReportPolicy.isBad`):

- it failed, or the viewer left during buffering;
- the viewer gave up on a start that took over 15 s;
- the process died for a reason that wasn't the viewer's (crash, ANR, memory);
- it had any error, retry, or engine fallback;
- it rebuffered 3+ times or for 30+ s;
- it stuttered: 120+ dropped frames at 6+ a minute;
- it had 5+ audio underruns.

## On a device

The journal is in the app's external files folder, readable over adb from release builds too:

```sh
PKG=com.lamphaus.app            # .debug for debug builds
adb pull /sdcard/Android/data/$PKG/files/diagnostics ./diagnostics
ls -1 diagnostics/*.json | tail -5          # newest last
jq '{outcome, closeReason, engine: .state.engine, totals, exit: .exit.reason}' "$(ls -1 diagnostics/*.json | tail -1)"
jq -r '.events[] | "\(.at)\t\(.type)\t\(.position // "")\t\(.value // "")\t\(.detail // "")"' "$(ls -1 diagnostics/*.json | tail -1)"
```

Each playback also logs a one-line summary when it closes:

```sh
adb logcat -s Lamphaus.Playback
```

## In Sentry

Sentry runs only while Settings → Privacy allows it (TV and mobile, synced with the account):

- **Crash reports** turns on Sentry's own capture: Java crashes, native crashes in libmpv, FFmpeg, or MediaCodec, and ANRs. A crash during a playback carries the tags `playback_report` (the report's id) and `playback_engine`.
- **Performance metrics** sends each bad playback as an event titled "Playback failed: IO_BAD_HTTP_STATUS http=403", "Playback process died: CRASH_NATIVE", "Playback closed while buffering: rebuffering", and so on. Events are grouped by outcome and cause. The tags `outcome`, `cause`, `engine`, `stream_type`, `video_codec`, `hdr`, `dolby_vision`, `exit_reason`, and `television` filter them. The timeline shows as breadcrumbs, the totals and formats as the `playback` context, and the whole report is attached as `playback-report.json`. `recent` in that context says how many playbacks the device finished in the past week, and how many were bad.

Useful searches: `report:playback`, `report:playback engine:MPV`, `cause:"process died: CRASH_NATIVE"`. To see the crash behind a "process died" playback, search `playback_report:<id>`.

### Sending rules

- Only what happens after a switch is turned on is sent; playback reports older than a week never are.
- A bad playback is handed to Sentry when it closes, and anything pending 15 s after the next launch. Sentry queues offline sends itself.
- Each device sends at most 10 playbacks a day, to keep within Sentry's free plan (5k events a month).
- Before anything leaves, events lose exception messages, the device name, the IP address, and any address in thread names or breadcrumbs. There are no sessions, tracing, screenshots, or replays.

### Setup

1. Create a Sentry project (platform: Android). In its settings, turn on **Data Scrubbing** and **Prevent Storing of IP Addresses**.
2. Add the public identifiers to `~/.gradle/gradle.properties`. Without a DSN, Sentry never starts and reports stay on the device:

   ```properties
   lamphaus.sentryDsn=https://…@….ingest.sentry.io/…
   lamphaus.sentryOrg=your-org
   lamphaus.sentryProject=lamphaus-android
   ```

3. Release builds upload their R8 mapping so Java stacks read as source. Create an organization auth token with the `project:releases` scope, then store it in the Keychain:

   ```sh
   security add-generic-password -s app.lamphaus.release.sentry -a lamphaus-sentry -w '<token>'
   ```

   Name that Keychain item in `release/signing.properties` (`sentry.keychainService`, `sentry.keychainAccount`). The release script then passes the token to the build as `SENTRY_AUTH_TOKEN`, and fails if the item is named but missing. Other builds upload nothing (REL-05).

libmpv and FFmpeg ship stripped. Native crashes show the library and exported functions, not internal ones. Full native symbols would need unstripped copies kept from `scripts/build-mpv-libs.sh`, uploaded with `sentry-cli debug-files upload`.
