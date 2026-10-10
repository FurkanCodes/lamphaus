-- Push-to-pull sync: devices download only what changed, woken by a push.
--
-- Postgres Changes held one realtime connection per open or backgrounded
-- device (200 at once on the free plan). Instead, every synced row now carries
-- a per-account change number, deletions leave a short-lived marker, and one
-- RPC (pull_sync_changes) returns everything newer than the number a device
-- last saw. After a change commits, the account's other devices receive an
-- empty "sync" signal: through Firebase Cloud Messaging where the device has
-- Google Play services, otherwise through a private Realtime broadcast it
-- listens to only while on screen. The signal carries no titles or ids
-- (SHR-PROD-06); a lost signal only delays a change until the next pull.
--
-- Additive by design: app versions that still use Postgres Changes keep
-- working, so the realtime publication stays until they have updated.

create extension if not exists pg_net with schema extensions;

-- ───────────────────── per-account change clock ─────────────────────
-- One row per account. Taking the next number locks the row until the
-- writing transaction ends, so an account's numbers always commit in order:
-- a reader that sees number N has seen every row numbered below it.
create table public.sync_clock (
  user_id uuid primary key,
  seq bigint not null default 0,
  -- The transaction that last sent this account's signal (one per transaction).
  signalled_xact bigint,
  -- Deletion markers at or below this number were purged; an older cursor
  -- must download the full snapshot instead.
  tombstone_floor bigint not null default 0
);

-- Deliberately no foreign key to auth.users: account deletion cascades
-- through the synced tables, whose triggers still stamp this account.
-- Orphans are purged daily.
create table public.sync_tombstones (
  user_id uuid not null,
  sync_seq bigint not null,
  collection text not null check (collection in ('library', 'progress', 'artwork_override')),
  profile_id uuid not null,
  item_key text not null,
  deleted_at timestamptz not null default now(),
  primary key (user_id, sync_seq)
);
create index sync_tombstones_deleted_at_idx on public.sync_tombstones (deleted_at);

-- ───────────────────── signal endpoints ─────────────────────
-- One row per app installation. A null token marks a device without Google
-- Play services: it listens for the Realtime broadcast while on screen.
create table public.sync_push_tokens (
  installation_id text primary key check (char_length(installation_id) between 8 and 128),
  user_id uuid not null references auth.users(id) on delete cascade,
  token text check (token is null or char_length(token) between 16 and 4096),
  platform text not null default 'android' check (platform in ('android', 'android-tv')),
  updated_at timestamptz not null default now()
);
create index sync_push_tokens_user_idx on public.sync_push_tokens (user_id);

-- Firebase Cloud Messaging access, kept fresh by the refresh-push-access
-- Edge Function (an hour-long OAuth token minted from the service account).
create table public.sync_push_config (
  id boolean primary key default true check (id),
  fcm_project_id text,
  access_token text,
  expires_at timestamptz,
  functions_url text,
  updated_at timestamptz not null default now()
);

alter table public.sync_clock enable row level security;
alter table public.sync_tombstones enable row level security;
alter table public.sync_push_tokens enable row level security;
alter table public.sync_push_config enable row level security;
-- sync_clock / sync_tombstones / sync_push_config: no policies (deny-all);
-- only the security-definer functions below read or write them.
revoke all on public.sync_clock, public.sync_tombstones, public.sync_push_config
  from anon, authenticated;

create policy owners_view_push_tokens on public.sync_push_tokens
  for select to authenticated
  using (user_id = (select auth.uid()) and (select public.caller_user_exists()));
create policy owners_remove_push_tokens on public.sync_push_tokens
  for delete to authenticated
  using (user_id = (select auth.uid()));
revoke insert, update on public.sync_push_tokens from anon, authenticated;

-- ───────────────────── signal sending ─────────────────────
-- Never raises: a failed signal must not fail the write that caused it.
create or replace function public.sync_send_signal(p_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_origin text;
  v_project text;
  v_access text;
  v_expires timestamptz;
  v_endpoint record;
begin
  -- The writing device sends its installation id on every request; it
  -- already has the change and needs no signal.
  v_origin := nullif(
    coalesce(nullif(current_setting('request.headers', true), ''), '{}')::json
      ->> 'x-lamphaus-installation',
    ''
  );

  select fcm_project_id, access_token, expires_at into v_project, v_access, v_expires
    from public.sync_push_config where id;

  if v_access is not null
     and v_project is not null
     and v_expires > now() + interval '1 minute' then
    for v_endpoint in
      select token from public.sync_push_tokens
       where user_id = p_user
         and token is not null
         and installation_id is distinct from v_origin
    loop
      perform net.http_post(
        url := 'https://fcm.googleapis.com/v1/projects/' || v_project || '/messages:send',
        body := jsonb_build_object(
          'message', jsonb_build_object(
            'token', v_endpoint.token,
            'data', jsonb_build_object('t', 'sync'),
            'android', jsonb_build_object(
              'priority', 'normal',
              'collapse_key', 'sync',
              'ttl', '3600s'
            )
          )
        ),
        headers := jsonb_build_object(
          'Content-Type', 'application/json',
          'Authorization', 'Bearer ' || v_access
        ),
        timeout_milliseconds := 5000
      );
    end loop;
  end if;

  if exists (
    select 1 from public.sync_push_tokens
     where user_id = p_user
       and token is null
       and installation_id is distinct from v_origin
  ) then
    perform realtime.send(
      jsonb_build_object('t', 'sync'),
      'sync',
      'sync:' || p_user::text,
      true
    );
  end if;
exception when others then
  raise warning 'sync signal skipped: %', sqlerrm;
end;
$$;

-- Takes the account's next change number.
create or replace function public.sync_next_seq(p_user uuid)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_seq bigint;
begin
  insert into public.sync_clock as c (user_id, seq)
  values (p_user, 1)
  on conflict (user_id) do update set seq = c.seq + 1
  returning seq into v_seq;
  return v_seq;
end;
$$;

-- Queues the account's signal once per transaction. It runs from AFTER
-- triggers, so an upsert that changed nothing never signals; pg_net and
-- Realtime deliver only after the transaction commits.
create or replace function public.sync_signal_once(p_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_xact bigint := txid_current();
begin
  update public.sync_clock
     set signalled_xact = v_xact
   where user_id = p_user
     and signalled_xact is distinct from v_xact;
  if found then
    perform public.sync_send_signal(p_user);
  end if;
end;
$$;

-- ───────────────────── stamping and deletion markers ─────────────────────
create or replace function public.sync_stamp_row()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- An upsert that changes nothing is not a change.
  if tg_op = 'UPDATE' and new is not distinct from old then
    return new;
  end if;
  new.sync_seq := public.sync_next_seq(new.user_id);
  return new;
end;
$$;

-- Artwork overrides predate user_id; their owner comes from the profile.
create or replace function public.sync_stamp_artwork_override()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'UPDATE' and new is not distinct from old then
    return new;
  end if;
  select p.user_id into new.user_id from public.profiles p where p.id = new.profile_id;
  if new.user_id is null then
    raise exception 'PROFILE_NOT_FOUND';
  end if;
  new.sync_seq := public.sync_next_seq(new.user_id);
  return new;
end;
$$;

create or replace function public.sync_signal_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform public.sync_signal_once((to_jsonb(new) ->> 'user_id')::uuid);
  return null;
end;
$$;

-- TG_ARGV[0] = collection name, TG_ARGV[1] = the column holding the item key.
create or replace function public.sync_record_deletion()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := (to_jsonb(old) ->> 'user_id')::uuid;
begin
  if v_user is null then
    return old;
  end if;
  insert into public.sync_tombstones (user_id, sync_seq, collection, profile_id, item_key)
  values (
    v_user,
    public.sync_next_seq(v_user),
    tg_argv[0],
    old.profile_id,
    to_jsonb(old) ->> tg_argv[1]
  );
  perform public.sync_signal_once(v_user);
  return old;
end;
$$;

revoke execute on function public.sync_send_signal(uuid) from anon, authenticated, public;
revoke execute on function public.sync_next_seq(uuid) from anon, authenticated, public;
revoke execute on function public.sync_signal_once(uuid) from anon, authenticated, public;
revoke execute on function public.sync_signal_change() from anon, authenticated, public;
revoke execute on function public.sync_stamp_row() from anon, authenticated, public;
revoke execute on function public.sync_stamp_artwork_override() from anon, authenticated, public;
revoke execute on function public.sync_record_deletion() from anon, authenticated, public;

-- ───────────────────── synced tables ─────────────────────
alter table public.media_artwork_overrides add column if not exists user_id uuid;
update public.media_artwork_overrides o
   set user_id = p.user_id
  from public.profiles p
 where p.id = o.profile_id and o.user_id is null;

-- Existing rows start at 0 and existing accounts' clocks at 1, so the first
-- pull (always the full snapshot) leaves a cursor that later pulls move past.
insert into public.sync_clock (user_id, seq)
select id, 1 from auth.users
on conflict (user_id) do nothing;

alter table public.profiles add column sync_seq bigint not null default 0;
alter table public.library_entries add column sync_seq bigint not null default 0;
alter table public.watch_progress add column sync_seq bigint not null default 0;
alter table public.user_settings add column sync_seq bigint not null default 0;
alter table public.media_artwork_overrides add column sync_seq bigint not null default 0;

create index profiles_user_sync_idx on public.profiles (user_id, sync_seq);
create index library_entries_user_sync_idx on public.library_entries (user_id, sync_seq);
create index watch_progress_user_sync_idx on public.watch_progress (user_id, sync_seq);
create index media_artwork_overrides_user_sync_idx on public.media_artwork_overrides (user_id, sync_seq);

create trigger profiles_sync_stamp
  before insert or update on public.profiles
  for each row execute function public.sync_stamp_row();
create trigger library_entries_sync_stamp
  before insert or update on public.library_entries
  for each row execute function public.sync_stamp_row();
create trigger watch_progress_sync_stamp
  before insert or update on public.watch_progress
  for each row execute function public.sync_stamp_row();
create trigger user_settings_sync_stamp
  before insert or update on public.user_settings
  for each row execute function public.sync_stamp_row();
create trigger media_artwork_overrides_sync_stamp
  before insert or update on public.media_artwork_overrides
  for each row execute function public.sync_stamp_artwork_override();

-- An upsert first fires the insert trigger for its proposed row, so the
-- signal waits for what actually happened: an insert, or an update that
-- changed the row.
create trigger profiles_sync_signal_insert
  after insert on public.profiles
  for each row execute function public.sync_signal_change();
create trigger profiles_sync_signal_update
  after update on public.profiles
  for each row when (old.* is distinct from new.*) execute function public.sync_signal_change();
create trigger library_entries_sync_signal_insert
  after insert on public.library_entries
  for each row execute function public.sync_signal_change();
create trigger library_entries_sync_signal_update
  after update on public.library_entries
  for each row when (old.* is distinct from new.*) execute function public.sync_signal_change();
create trigger watch_progress_sync_signal_insert
  after insert on public.watch_progress
  for each row execute function public.sync_signal_change();
create trigger watch_progress_sync_signal_update
  after update on public.watch_progress
  for each row when (old.* is distinct from new.*) execute function public.sync_signal_change();
create trigger user_settings_sync_signal_insert
  after insert on public.user_settings
  for each row execute function public.sync_signal_change();
create trigger user_settings_sync_signal_update
  after update on public.user_settings
  for each row when (old.* is distinct from new.*) execute function public.sync_signal_change();
create trigger media_artwork_overrides_sync_signal_insert
  after insert on public.media_artwork_overrides
  for each row execute function public.sync_signal_change();
create trigger media_artwork_overrides_sync_signal_update
  after update on public.media_artwork_overrides
  for each row when (old.* is distinct from new.*) execute function public.sync_signal_change();

create trigger library_entries_sync_deletion
  after delete on public.library_entries
  for each row execute function public.sync_record_deletion('library', 'media_key');
create trigger watch_progress_sync_deletion
  after delete on public.watch_progress
  for each row execute function public.sync_record_deletion('progress', 'video_id');
create trigger media_artwork_overrides_sync_deletion
  after delete on public.media_artwork_overrides
  for each row execute function public.sync_record_deletion('artwork_override', 'media_key');

-- ───────────────────── the pull ─────────────────────
-- Everything the caller's account changed after p_since, in one consistent
-- snapshot. p_since = 0, a purged-marker gap, or a cursor ahead of the
-- server returns the full snapshot ("full": true) without markers; the
-- device then reconciles absences itself. p_all_overrides returns every
-- artwork override even in an incremental pull (devices keep them in memory).
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
    select coalesce(c.seq, 0) as seq, coalesce(c.tombstone_floor, 0) as floor
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

-- An installation belongs to whoever signed in on it last; taking it over
-- replaces the previous account's endpoint.
create or replace function public.register_sync_endpoint(
  p_installation_id text,
  p_token text,
  p_platform text default 'android'
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
begin
  if v_user is null or not (select public.caller_user_exists()) then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  insert into public.sync_push_tokens (installation_id, user_id, token, platform, updated_at)
  values (p_installation_id, v_user, nullif(p_token, ''), p_platform, now())
  on conflict (installation_id) do update
    set user_id = excluded.user_id,
        token = excluded.token,
        platform = excluded.platform,
        updated_at = now();
end;
$$;

revoke execute on function public.register_sync_endpoint(text, text, text) from anon, public;
grant execute on function public.register_sync_endpoint(text, text, text) to authenticated;

-- ───────────────────── Realtime fallback ─────────────────────
-- A signed-in device may listen only to its own account's private topic.
create policy sync_signal_receive on realtime.messages
  for select to authenticated
  using (
    realtime.messages.extension = 'broadcast'
    and (select realtime.topic()) = 'sync:' || (select auth.uid())::text
  );

-- ───────────────────── housekeeping ─────────────────────
-- Markers live 90 days; a device away longer downloads the full snapshot.
-- Endpoints not refreshed in 60 days belong to uninstalled apps.
create or replace function public.sync_housekeeping()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  with purged as (
    delete from public.sync_tombstones
     where deleted_at < now() - interval '90 days'
     returning user_id, sync_seq
  ), floors as (
    select user_id, max(sync_seq) as floor from purged group by user_id
  )
  update public.sync_clock c
     set tombstone_floor = greatest(c.tombstone_floor, f.floor)
    from floors f
   where c.user_id = f.user_id;

  delete from public.sync_tombstones t
   where not exists (select 1 from auth.users u where u.id = t.user_id);
  delete from public.sync_clock c
   where not exists (select 1 from auth.users u where u.id = c.user_id);
  delete from public.sync_push_tokens where updated_at < now() - interval '60 days';
end;
$$;

revoke execute on function public.sync_housekeeping() from anon, authenticated, public;

do $$
begin
  if exists (select 1 from cron.job where jobname = 'sync-housekeeping') then
    perform cron.unschedule('sync-housekeeping');
  end if;
  if exists (select 1 from cron.job where jobname = 'refresh-push-access') then
    perform cron.unschedule('refresh-push-access');
  end if;
end $$;

select cron.schedule('sync-housekeeping', '41 3 * * *', $$select public.sync_housekeeping()$$);

-- Keeps the FCM access token fresh. Does nothing until the refresh-push-access
-- function has run once and recorded where it lives.
select cron.schedule(
  'refresh-push-access',
  '*/30 * * * *',
  $$select net.http_post(
      url := c.functions_url || '/refresh-push-access',
      body := '{}'::jsonb,
      headers := '{"Content-Type": "application/json"}'::jsonb,
      timeout_milliseconds := 10000
    )
    from public.sync_push_config c
   where c.id and c.functions_url is not null$$
);
