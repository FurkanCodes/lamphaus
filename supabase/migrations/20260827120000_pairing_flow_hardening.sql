-- Pairing-flow hardening (2026-08-27 audit: P0-2, P2).
--
-- 1. consume_pairing_slot: the old SELECT-then-write shape let two racers
--    both pass at count = limit - 1. One guarded upsert decides everything —
--    the conflict-action WHERE is the limit guard, so an exhausted bucket
--    updates nothing and RETURNING yields no row (callers see NULL → 429).
-- 2. register_device_session: re-pairing must not orphan the device's
--    PREVIOUS GoTrue session (audit P0-2). Capture the old auth_session_id
--    and destroy its refresh tokens + session row (same deletion as
--    revoke_device) before binding the new one. One invariant, one owner:
--    a devices row has at most one live session — the newest.

create or replace function public.consume_pairing_slot(
  p_ip_hash text,
  p_limit integer,
  p_window_minutes integer
)
returns boolean
language sql
security definer
set search_path = public
as $$
  insert into public.pairing_rate_limits as r (ip_hash, count, reset_at)
  values (p_ip_hash, 1, now() + make_interval(mins => p_window_minutes))
  on conflict (ip_hash) do update
    set count = case when r.reset_at < now() then 1 else r.count + 1 end,
        reset_at = case when r.reset_at < now()
                        then now() + make_interval(mins => p_window_minutes)
                        else r.reset_at end
    where r.reset_at < now() or r.count < p_limit
  returning true;
$$;

revoke execute on function public.consume_pairing_slot(text, integer, integer)
  from anon, authenticated, public;
grant execute on function public.consume_pairing_slot(text, integer, integer)
  to service_role;

create or replace function public.register_device_session(p_device_id text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_session_id uuid;
  v_previous uuid;
begin
  v_session_id := nullif(
    current_setting('request.jwt.claims', true)::json ->> 'session_id',
    ''
  )::uuid;

  if v_session_id is null then
    raise exception 'JWT carries no session_id claim';
  end if;

  select auth_session_id into v_previous
    from public.devices
   where id = p_device_id
     and user_id = (select auth.uid())
     and revoked = false;

  if not found then
    raise exception 'DEVICE_NOT_FOUND_OR_FORBIDDEN';
  end if;

  -- Re-pair swap: the previous bound session must not survive as an orphan
  -- with a live refresh token (audit P0-2). Same deletion as revoke_device.
  if v_previous is not null and v_previous <> v_session_id then
    delete from auth.refresh_tokens where session_id = v_previous;
    delete from auth.sessions       where id = v_previous;
  end if;

  update public.devices
     set auth_session_id = v_session_id,
         last_seen_at_epoch_millis = (extract(epoch from now()) * 1000)::bigint
   where id = p_device_id
     and user_id = (select auth.uid())
     and revoked = false;
end;
$$;

revoke execute on function public.register_device_session(text)
  from anon, public;
grant execute on function public.register_device_session(text) to authenticated;
