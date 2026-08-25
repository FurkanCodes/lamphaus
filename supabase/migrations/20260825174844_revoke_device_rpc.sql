-- M6: device revocation support (plan D3/F6).
--
-- This project's GoTrue build predates the admin per-session logout route,
-- so revocation runs through a security-definer RPC instead: mark the device
-- revoked and destroy the exact GoTrue session the TV obtained at pairing
-- (refresh tokens + session row), which makes its next refresh fail and
-- drops it back to the QR screen. Ownership guard keeps it self-service safe.

create or replace function public.revoke_device(p_device_id text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_session_id uuid;
begin
  select d.auth_session_id into v_session_id
    from public.devices d
   where d.id = p_device_id
     and d.user_id = (select auth.uid());

  if not found then
    raise exception 'DEVICE_NOT_FOUND_OR_FORBIDDEN';
  end if;

  update public.devices
     set revoked = true
   where id = p_device_id;

  if v_session_id is not null then
    delete from auth.refresh_tokens where session_id = v_session_id;
    delete from auth.sessions       where id = v_session_id;
  end if;
end;
$$;

revoke execute on function public.revoke_device(text)
  from anon, public;
grant execute on function public.revoke_device(text) to authenticated;
