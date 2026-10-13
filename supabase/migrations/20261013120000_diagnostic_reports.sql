-- Playback diagnostics (SHR-PROD-18): bad playbacks and crashes from
-- accounts that turned on Performance metrics or Crash reports.
--
-- Write-only for clients: a device may add rows for its own account and can
-- read nothing back; the developer reads them with the service role (SQL
-- editor or Studio). Reports carry no addresses, titles, add-on names, or
-- exception messages (SHR-PROD-06). Kept small for the free plan's 500 MB:
-- at most 20 reports per account a day, 32 KB each, deleted after 30 days
-- and with the account.

create table public.diagnostic_reports (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  -- One-way hash of the device's ANDROID_ID, the same one sync signals use.
  installation_id text not null check (char_length(installation_id) <= 64),
  kind text not null check (kind in ('playback', 'crash')),
  outcome text check (char_length(outcome) <= 32),
  app_version text not null check (char_length(app_version) <= 64),
  device_model text not null check (char_length(device_model) <= 128),
  sdk integer not null,
  television boolean not null,
  started_at_epoch_millis bigint not null,
  created_at timestamptz not null default now(),
  payload jsonb not null check (octet_length(payload::text) <= 32768)
);

-- Serves the daily cap and "this account's latest reports".
create index diagnostic_reports_user_created on public.diagnostic_reports (user_id, created_at);

alter table public.diagnostic_reports enable row level security;

create policy "owners_send_reports" on public.diagnostic_reports
  for insert to authenticated
  with check (user_id = (select auth.uid()));

revoke all on public.diagnostic_reports from anon;
revoke select, update, delete, truncate on public.diagnostic_reports from authenticated;

-- The daily cap. Security definer so it can count rows the sender cannot
-- read; PT429 makes PostgREST answer 429, which the app takes as "later".
create or replace function public.cap_diagnostic_reports()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (
    select count(*)
      from public.diagnostic_reports r
     where r.user_id = new.user_id
       and r.created_at > now() - interval '1 day'
  ) >= 20 then
    raise sqlstate 'PT429' using message = 'diagnostics_rate_limited';
  end if;
  return new;
end;
$$;

revoke execute on function public.cap_diagnostic_reports() from anon, authenticated, public;

create trigger diagnostic_reports_daily_cap
  before insert on public.diagnostic_reports
  for each row execute function public.cap_diagnostic_reports();

create or replace function public.prune_diagnostic_reports()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.diagnostic_reports
   where created_at < now() - interval '30 days';
$$;

revoke execute on function public.prune_diagnostic_reports() from anon, authenticated, public;

do $$
begin
  if exists (select 1 from cron.job where jobname = 'prune-diagnostic-reports') then
    perform cron.unschedule('prune-diagnostic-reports');
  end if;
end $$;

select cron.schedule('prune-diagnostic-reports', '23 4 * * *', $$select public.prune_diagnostic_reports()$$);
