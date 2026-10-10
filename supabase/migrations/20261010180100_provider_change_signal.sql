-- Add-ons and artwork keys (provider_configs) are encrypted and reach a
-- device only through Edge Functions, so every launch called two of them
-- (list-provider-configs, artwork-key-status): most of the free plan's
-- 500,000 invocations a month. Now the account's change clock also notes
-- when provider_configs last changed. The pull reports providers_changed,
-- and a device calls those functions only then (or when it never fetched
-- them). A change signals the account's other devices like any synced row,
-- so an add-on added on the phone reaches the TV without a restart.
--
-- Additive: app versions that ignore providers_changed keep fetching at
-- every launch.

alter table public.sync_clock add column providers_seq bigint not null default 0;

create or replace function public.sync_note_provider_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid;
  v_seq bigint;
begin
  if tg_op = 'DELETE' then
    v_user := old.user_id;
  else
    v_user := new.user_id;
  end if;
  v_seq := public.sync_next_seq(v_user);
  update public.sync_clock set providers_seq = v_seq where user_id = v_user;
  perform public.sync_signal_once(v_user);
  return null;
end;
$$;

revoke execute on function public.sync_note_provider_change() from anon, authenticated, public;

create trigger provider_configs_sync_insert
  after insert on public.provider_configs
  for each row execute function public.sync_note_provider_change();
create trigger provider_configs_sync_update
  after update on public.provider_configs
  for each row when (old.* is distinct from new.*) execute function public.sync_note_provider_change();
create trigger provider_configs_sync_delete
  after delete on public.provider_configs
  for each row execute function public.sync_note_provider_change();

-- Unchanged from 20261010120000_push_to_pull_sync.sql except providers_changed.
create or replace function public.pull_sync_changes(
  p_since bigint,
  p_all_overrides boolean default false
)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  with me as (
    select (select auth.uid()) as uid
     where (select public.caller_user_exists())
  ),
  clock as (
    select coalesce(c.seq, 0) as seq,
           coalesce(c.tombstone_floor, 0) as floor,
           coalesce(c.providers_seq, 0) as providers_seq
      from me left join public.sync_clock c on c.user_id = me.uid
  ),
  mode as (
    select (p_since <= 0 or p_since < clock.floor or p_since > clock.seq) as is_full
      from clock
  )
  select jsonb_build_object(
    'cursor', clock.seq,
    'full', mode.is_full,
    'profile_count', (select count(*) from public.profiles p where p.user_id = me.uid),
    'has_settings', exists (select 1 from public.user_settings s where s.user_id = me.uid),
    'providers_changed', (mode.is_full or clock.providers_seq > p_since),
    'profiles', coalesce((
      select jsonb_agg(to_jsonb(p) - 'sync_seq' order by p.sync_seq)
        from public.profiles p
       where p.user_id = me.uid and (mode.is_full or p.sync_seq > p_since)
    ), '[]'::jsonb),
    'library', coalesce((
      select jsonb_agg(to_jsonb(l) - 'sync_seq' order by l.sync_seq)
        from public.library_entries l
       where l.user_id = me.uid and (mode.is_full or l.sync_seq > p_since)
    ), '[]'::jsonb),
    'progress', coalesce((
      select jsonb_agg(to_jsonb(w) - 'sync_seq' order by w.sync_seq)
        from public.watch_progress w
       where w.user_id = me.uid and (mode.is_full or w.sync_seq > p_since)
    ), '[]'::jsonb),
    'settings', (
      select to_jsonb(s) - 'sync_seq'
        from public.user_settings s
       where s.user_id = me.uid and (mode.is_full or s.sync_seq > p_since)
    ),
    'overrides_complete', (mode.is_full or p_all_overrides),
    'overrides', coalesce((
      select jsonb_agg(to_jsonb(o) - 'sync_seq' - 'user_id' order by o.sync_seq)
        from public.media_artwork_overrides o
       where o.user_id = me.uid and (mode.is_full or p_all_overrides or o.sync_seq > p_since)
    ), '[]'::jsonb),
    'deleted', case when mode.is_full then '[]'::jsonb else coalesce((
      select jsonb_agg(
               jsonb_build_object('c', t.collection, 'p', t.profile_id, 'k', t.item_key)
               order by t.sync_seq
             )
        from public.sync_tombstones t
       where t.user_id = me.uid and t.sync_seq > p_since
    ), '[]'::jsonb) end
  )
  from me, clock, mode;
$$;

revoke execute on function public.pull_sync_changes(bigint, boolean) from anon, public;
grant execute on function public.pull_sync_changes(bigint, boolean) to authenticated;
