-- Custom artwork overrides (BYOK metadata provider, profile-scoped).
--
-- One row per (profile, title). Paths are provider-relative image paths
-- (e.g. "/abc123.jpg"), never URLs or key material — the API key lives
-- encrypted in provider_configs and is only ever used server-side.
--
-- Profile-scoped by design: each profile curates its own look, mirroring
-- library_entries' ownership and cascade semantics.

create table public.media_artwork_overrides (
  profile_id uuid not null references public.profiles(id) on delete cascade,
  media_key text not null,
  poster_path text,
  backdrop_path text,
  updated_at_epoch_millis bigint not null default 0,
  primary key (profile_id, media_key)
);

alter table public.media_artwork_overrides enable row level security;

create policy "owners_manage_artwork_overrides" on public.media_artwork_overrides
  for all using (
    profile_id in (select id from public.profiles where user_id = (select auth.uid()))
  ) with check (
    profile_id in (select id from public.profiles where user_id = (select auth.uid()))
  );

-- Realtime fan-out for the live overrides channel.
alter publication supabase_realtime add table public.media_artwork_overrides;
