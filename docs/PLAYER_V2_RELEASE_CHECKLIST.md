# Player V2 — Release Verification

Engineering-verified items are checked. Physical-device items are the manual
release gate: run this list on the target hardware before shipping.

## Engineering-verified (CI + emulator)

- [x] Track selection precedence (session → source → semantic → profile → stream default) — `TrackSelectionTest`
- [x] BCP-47 normalization and base-language matching — `TrackSelectionTest`
- [x] Forced/SDH/commentary role detection and forced-only semantics — `TrackSelectionTest`
- [x] Display-mode selection order and 23.976/24 distinction — `TrackSelectionTest`
- [x] Dolby Vision policy ladder — `DolbyVisionPolicyTest`
- [x] Audio route passthrough/decode policy with downmix — `AudioRoutePolicyTest`
- [x] Delay clamps (±180s/100ms, ±3s/25ms) and 300ms line-sync formula — `TrackSelectionTest`
- [x] Engine fallback policy (decoder/format only, never network/auth/provider, no bounce) — `PlaybackEnginePolicyTest`, `EngineHandoffMappingTest`
- [x] Device playback config mapping with unknown-value fallback — `DevicePlaybackConfigMappingTest`
- [x] Room 5→6 migration validated against exported schema — `LamphausDatabaseMigrationTest` (instrumented)
- [x] Subtitle sidecar parsing (SRT/VTT/TTML/ASS) and charset detection — `SubtitleCuesTest`
- [x] Supabase playback preference tables with owner RLS — migration `20260903220000` applied
- [x] Emulator smoke: catalog → detail → source picker → player service → decoder-failure error surface, zero crashes

## Manual device matrix (release gate)

### Video / HDR
- [ ] SDR phone: playback, subtitle rendering, PiP enter/exit, return state
- [ ] HDR10 device: HDR playback with Original colors on, tone-map path with Original colors off
- [ ] Dolby Vision TV: profile 5/8 native; profile 7 sample with `Convert P7 to P8.1` (requires libdovi-packaged libmpv)
- [ ] Non-DV TV: profile 7/5 falls back to HDR10 base layer without color washout
- [ ] Frame-rate matching: 23.976/24, 25/50, 29.97/59.94, 30/60 sources on a mode-switching TV;
      `Seamless only` never blanks; `Always` blanks and restores; ABR switches do not re-trigger
- [ ] Resolution matching `Match source` on 720p/1080p/4K TVs; original mode restored after exit
- [ ] Stream info shows the applied mode or the skip reason

### Audio
- [ ] HDMI receiver/soundbar: E-AC-3/JOC, TrueHD, DTS passthrough (Stream info shows `passthrough`)
- [ ] TV speakers: same sources decode to PCM; AUTO downmix of 5.1 → stereo
- [ ] Bluetooth: no passthrough attempts, per-route delay remembered across reconnects
- [ ] Audio delay ±3000 ms audible in 25 ms steps; survives route switch and return

### Subtitles
- [ ] SRT/VTT/TTML/ASS sidecars: sync-by-line picker on real files; ±3min window; 300ms lead feels right
- [ ] Negative subtitle delay on MPV engine; positive delay on Media3 engine
- [ ] Embedded ASS styling preserved by default; style editor overrides when "Keep original styling" is off
- [ ] PGS/DVB bitmap tracks selectable; sync-by-line picker hidden for them

### Engines
- [ ] Package `libmpv.so` via `scripts/build-mpv-libs.sh`; confirm Stream info/fallback triggers on a
      decoder-failure source (4K HEVC profile that hardware rejects)
- [ ] Handoff preserves position, play state, speed, selected tracks, and delays
- [ ] No repeated Media3↔MPV bouncing on a persistently failing item

### System
- [ ] Media session: lock-screen controls, notification actions, PiP transport (mobile)
- [ ] Back unwinding: editor → submenu → controls → exit with focus restored (TV)
- [ ] Process recreation during playback restores position and state
- [ ] Per-ABI APK/AAB size with and without libmpv; document deltas
- [ ] 200% font scale on mobile player panels; TalkBack labels on every control

### Privacy
- [ ] Logs and diagnostics contain no stream URLs, headers, tokens, or provider credentials
- [ ] Diagnostics remain consent-gated and record only engine, codec category, fallback reason,
      output capability, and anonymized performance data
