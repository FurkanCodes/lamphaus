-- Per-user app settings (theme, dynamic color, diagnostics consent...).
-- One row per user; payload is jsonb so the settings shape can evolve
-- without migrations. Clients last-writer-wins via updated_at_epoch_millis.
-- Deliberately device-local and NOT synced: active profile choice,
-- profile PINs (never leave the device).

create table public.user_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  payload jsonb not null default '{}'::jsonb,
  updated_at_epoch_millis bigint not null default 0
);

alter table public.user_settings enable row level security;

create policy "owners_manage_settings" on public.user_settings
  for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

alter publication supabase_realtime add table public.user_settings;
