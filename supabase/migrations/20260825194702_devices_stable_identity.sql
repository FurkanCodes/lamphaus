-- Stable physical-device identity for pairing dedup (plan D3 refinement).
--
-- TVs re-enter the QR flow whenever their session is lost (revoked,
-- deleted account recovery, expiry). Without a hardware-bound key every
-- re-pair spawned ANOTHER devices row for the same screen. ANDROID_ID
-- travels through create-pairing-session -> pairing_sessions -> claim,
-- letting claim find-or-create ONE devices entry per physical TV.
alter table public.devices
  add column device_key text;
alter table public.pairing_sessions
  add column device_key text;

create index if not exists devices_user_device_key_idx
  on public.devices (user_id, device_key);
