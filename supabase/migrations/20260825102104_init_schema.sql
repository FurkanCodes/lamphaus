-- Lamphaus initial schema — M1 of the Supabase migration.
-- Ownership model: every user-scoped row carries user_id = auth.uid().
-- Deny-all tables (RLS on, no policies): provider_configs, pairing_sessions,
-- pairing_rate_limits — reachable only by Edge Functions via service role.

-- ───────────────────────── profiles ─────────────────────────
create table public.profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 30),
  avatar_key text not null,
  kind text not null check (kind in ('ADULT', 'CHILD')),
  has_pin boolean not null default false,
  hide_unrated boolean not null default false,
  updated_at_epoch_millis bigint not null default 0
);

-- ─────────────────────────── library ───────────────────────────
create table public.library_entries (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  media_key text not null,
  preview jsonb not null,
  added_at_epoch_millis bigint not null default 0,
  updated_at_epoch_millis bigint not null default 0,
  primary key (profile_id, media_key)
);

-- ────────────────────────── progress ──────────────────────────
create table public.watch_progress (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  media_key text not null,
  video_id text not null,
  position_millis bigint not null default 0,
  duration_millis bigint not null default 0,
  completed boolean not null default false,
  updated_at_epoch_millis bigint not null default 0,
  primary key (profile_id, video_id)
);

-- ─────────────────────── devices (paired TVs) ───────────────────────
-- Rows are created and revoked exclusively by Edge Functions; owners may read.
create table public.devices (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  label text not null default 'Television',
  platform text not null default 'android-tv',
  auth_session_id uuid,
  revoked boolean not null default false,
  created_at timestamptz not null default now(),
  last_seen_at_epoch_millis bigint not null default 0
);

-- ───────────── provider configs (KMS successor, encrypted) ─────────────
create table public.provider_configs (
  user_id uuid not null references auth.users(id) on delete cascade,
  provider_id text not null,
  display_name text not null default '',
  encrypted_config text not null,
  enabled boolean not null default true,
  sort_order integer not null default 0,
  updated_at_epoch_millis bigint not null default 0,
  primary key (user_id, provider_id)
);

-- ─────────────── pairing (TV ⇄ phone handshake) ───────────────
create table public.pairing_sessions (
  id text primary key,
  code_hash text not null,
  device_label text not null default 'Television',
  claimed_by uuid references auth.users(id) on delete set null,
  claimed_at timestamptz,
  exchanged boolean not null default false,
  exchanged_at timestamptz,
  device_id text,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);

create table public.pairing_rate_limits (
  ip_hash text primary key,
  count integer not null default 0,
  reset_at timestamptz not null
);

-- ───────────────────────── indexes ─────────────────────────
create index idx_profiles_user on public.profiles(user_id);
create index idx_library_user on public.library_entries(user_id);
create index idx_library_profile_recent on public.library_entries(profile_id, updated_at_epoch_millis desc);
create index idx_progress_user on public.watch_progress(user_id);
create index idx_devices_user on public.devices(user_id);
create index idx_pairing_sessions_code on public.pairing_sessions(code_hash)
  where exchanged = false;

-- ──────────────── row level security ────────────────
alter table public.profiles enable row level security;
alter table public.library_entries enable row level security;
alter table public.watch_progress enable row level security;
alter table public.devices enable row level security;
alter table public.provider_configs enable row level security;
alter table public.pairing_sessions enable row level security;
alter table public.pairing_rate_limits enable row level security;

create policy "owners_manage_profiles" on public.profiles
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "owners_manage_library" on public.library_entries
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "owners_manage_progress" on public.watch_progress
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "owners_view_devices" on public.devices
  for select using (user_id = auth.uid());

-- provider_configs / pairing_sessions / pairing_rate_limits intentionally
-- have NO policies: RLS-enabled with zero policies denies all client access.

-- ─────────────── realtime (Postgres changes) ───────────────
alter publication supabase_realtime add table public.profiles;
alter publication supabase_realtime add table public.library_entries;
alter publication supabase_realtime add table public.watch_progress;
