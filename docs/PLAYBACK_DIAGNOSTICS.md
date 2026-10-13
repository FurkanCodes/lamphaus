# Playback diagnostics

How to find out why a playback dropped (`SHR-PROD-18`). Every device keeps a journal of its last 30 playbacks and crashes. Accounts that opt in also send the bad ones to Supabase.

## What a report holds

One JSON file per playback (`kind: PLAYBACK`) or crash (`kind: CRASH`), written while it happens:

- `playback`: the engine setting and the engine it started on, stream type (`progressive`, `hls`, `dash`), whether it resumed, the traits the add-on's label claims, and the device's playback settings.
- `media`: the decoded container, video, and audio formats.
- `events`: a timeline in milliseconds since the start. It covers readiness, first frame, buffering (with buffer level and bandwidth), seeks, and non-viewer pauses (audio focus, output removed). It also covers errors (code name, HTTP status, renderer and decoder, cause classes) and load errors. Then retries, stall skips, the Dolby Vision base-layer fallback, engine fallbacks, decoders and the audio output, dropped-frame bursts, audio underruns, network changes, low memory, background and foreground, and the close.
- `totals`: startup time, rebuffers and their time, play time, dropped frames, underruns, errors, retries, seeks.
- `outcome`: `ENDED`, `CLOSED`, `CLOSED_WHILE_LOADING`, `CLOSED_WHILE_BUFFERING`, `FAILED`, `CLOSED_IN_BACKGROUND`, or `PROCESS_DIED`.
- `closeReason`: why the player closed. When the Back key did it, this adds the key's source and device ids, so a remote or HDMI-CEC device sending Back on its own shows up (`source=0x2000001` is HDMI).
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

## In Supabase

Reports from opted-in accounts land in `public.diagnostic_reports`, which clients can only insert into. Read it in the SQL editor:

```sql
-- Latest bad playbacks and crashes
select created_at, kind, outcome, device_model, app_version,
       payload->'state'->>'engine' as engine,
       payload->>'closeReason' as close_reason,
       payload->'exit'->>'reason' as exit_reason,
       payload->'totals' as totals
  from public.diagnostic_reports
 order by created_at desc
 limit 50;

-- Which errors happen most
select e->>'detail' as error, count(*)
  from public.diagnostic_reports, jsonb_array_elements(payload->'events') e
 where e->>'type' = 'ERROR'
 group by 1 order by 2 desc;

-- Outcomes per device and engine
select device_model, payload->'state'->>'engine' as engine, outcome, count(*)
  from public.diagnostic_reports
 where kind = 'playback'
 group by 1, 2, 3 order by 4 desc;

-- One account's reports
select created_at, outcome, payload
  from public.diagnostic_reports
 where user_id = (select id from auth.users where email = 'someone@example.com')
 order by created_at desc;
```

`payload->'recent'` gives each report a baseline: how many playbacks the device finished in the past week, and how many were bad.

## Sending rules

- Performance metrics sends bad playbacks; Crash reports sends crashes and ANRs. Both switches are in Settings → Privacy on TV and mobile, and are account-wide.
- Only reports begun after the switch was turned on are sent, and none older than a week.
- A report is sent when its playback closes, and anything pending is sent 15 s after the next launch.
- The table takes at most 20 reports per account a day (PostgREST answers 429 and the app tries again on a later launch). Reports must be under 32 KB, so the app drops events from the middle of an eventful report to fit. Rows are deleted after 30 days by the `prune-diagnostic-reports` cron job, and with the account.

Apply the migration (`supabase/migrations/20261013120000_diagnostic_reports.sql`) with `supabase db push` before shipping a build that sends reports. Until then, sends fail quietly and reports stay pending on the device for a week.
