-- Pairing-session retention (2026-08-27 audit: P1-8).
--
-- pairing_sessions rows previously lived forever, holding grant_email /
-- grant_otp plaintext (claimed-but-never-exchanged rows) indefinitely.
-- Purge everything past its 5-minute TTL by one day, daily via pg_cron.
-- Consumed rows already have their grant columns nulled atomically by
-- exchange-device-grant; this sweep covers everything else left behind.

delete from public.pairing_sessions where expires_at < now() - interval '1 day';

create extension if not exists pg_cron with schema pg_catalog;
grant usage on schema cron to postgres;

do $$
begin
  if exists (select 1 from cron.job where jobname = 'purge-pairing-sessions') then
    perform cron.unschedule('purge-pairing-sessions');
  end if;
end $$;

select cron.schedule(
  'purge-pairing-sessions',
  '23 3 * * *',
  $$delete from public.pairing_sessions where expires_at < now() - interval '1 day'$$
);
