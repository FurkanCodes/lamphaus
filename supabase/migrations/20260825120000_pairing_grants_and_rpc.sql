-- M4: TV pairing handshake support.
--
-- pairing_sessions gains a short-lived slot for the GoTrue grant minted at
-- claim time (admin.generateLink). The TV fetches it exactly once through
-- exchange-device-grant, which nulls the columns atomically with
-- exchanged=true, so the grant never survives its single use.

alter table public.pairing_sessions
  add column grant_email text,
  add column grant_otp text,
  add column granted_at timestamptz;

-- Atomic per-IP slot consumption for unauthenticated session creation.
-- service_role gets an explicit EXECUTE grant because the blanket public
-- revoke below also removes its implicit one.
create or replace function public.consume_pairing_slot(
  p_ip_hash text,
  p_limit integer,
  p_window_minutes integer
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer;
  v_reset timestamptz;
begin
  select count, reset_at into v_count, v_reset
    from public.pairing_rate_limits
   where ip_hash = p_ip_hash;

  if v_reset is null or v_reset < now() then
    insert into public.pairing_rate_limits (ip_hash, count, reset_at)
    values (p_ip_hash, 1, now() + make_interval(mins => p_window_minutes))
    on conflict (ip_hash) do update
      set count = 1, reset_at = excluded.reset_at;
    return true;
  end if;

  if v_count >= p_limit then
    return false;
  end if;

  update public.pairing_rate_limits set count = count + 1
   where ip_hash = p_ip_hash;
  return true;
end;
$$;

revoke execute on function public.consume_pairing_slot(text, integer, integer)
  from anon, authenticated, public;
grant execute on function public.consume_pairing_slot(text, integer, integer)
  to service_role;

-- The TV calls this AFTER obtaining its own GoTrue session (plan D2/D3):
-- it binds devices.auth_session_id to the exact session inside the caller's
-- JWT. Security definer because devices has owner-SELECT-only RLS; the
-- ownership guard below keeps it safe.
create or replace function public.register_device_session(p_device_id text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_session_id uuid;
begin
  v_session_id := nullif(
    current_setting('request.jwt.claims', true)::json ->> 'session_id',
    ''
  )::uuid;

  if v_session_id is null then
    raise exception 'JWT carries no session_id claim';
  end if;

  update public.devices
     set auth_session_id = v_session_id,
         last_seen_at_epoch_millis = (extract(epoch from now()) * 1000)::bigint
   where id = p_device_id
     and user_id = (select auth.uid())
     and revoked = false;

  if not found then
    raise exception 'DEVICE_NOT_FOUND_OR_FORBIDDEN';
  end if;
end;
$$;

revoke execute on function public.register_device_session(text)
  from anon, public;
grant execute on function public.register_device_session(text) to authenticated;
