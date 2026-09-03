-- Player V2 cloud-synced playback preferences (plan §5).
--
-- profile_playback_preferences: one row per profile carrying the synced
-- subset of ProfilePlaybackPreferences (languages, subtitle mode/style,
-- original colors) as an evolving jsonb payload.
--
-- media_playback_preferences: semantic per-title choices ("this series plays
-- with Japanese audio and English subtitles"). Exact track ids, URLs, source
-- fingerprints, subtitle timing, audio-route delay, engine override, and
-- frame-rate/resolution matching stay device-local and never land here.
--
-- Both tables are owner-only (RLS keyed on auth.uid() and a profile-ownership
-- check), realtime-published, and cascade-delete with the profile.

create table public.profile_playback_preferences (
  profile_id uuid primary key references public.profiles(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  payload jsonb not null default '{}'::jsonb,
  updated_at_epoch_millis bigint not null default 0
);

create index profile_playback_preferences_user_idx
  on public.profile_playback_preferences (user_id);

create table public.media_playback_preferences (
  profile_id uuid not null references public.profiles(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  media_key text not null,
  payload jsonb not null default '{}'::jsonb,
  updated_at_epoch_millis bigint not null default 0,
  primary key (profile_id, media_key)
);

create index media_playback_preferences_user_idx
  on public.media_playback_preferences (user_id, updated_at_epoch_millis);

alter table public.profile_playback_preferences enable row level security;
alter table public.media_playback_preferences enable row level security;

create policy "owners_manage_profile_playback_prefs" on public.profile_playback_preferences
  for all using (
    user_id = (select auth.uid())
    and exists (
      select 1 from public.profiles p
      where p.id = profile_id and p.user_id = (select auth.uid())
    )
  ) with check (
    user_id = (select auth.uid())
    and exists (
      select 1 from public.profiles p
      where p.id = profile_id and p.user_id = (select auth.uid())
    )
  );

create policy "owners_manage_media_playback_prefs" on public.media_playback_preferences
  for all using (
    user_id = (select auth.uid())
    and exists (
      select 1 from public.profiles p
      where p.id = profile_id and p.user_id = (select auth.uid())
    )
  ) with check (
    user_id = (select auth.uid())
    and exists (
      select 1 from public.profiles p
      where p.id = profile_id and p.user_id = (select auth.uid())
    )
  );

alter publication supabase_realtime add table public.profile_playback_preferences;
alter publication supabase_realtime add table public.media_playback_preferences;
