-- Instant account-removal propagation (plan F6 hardening).
--
-- Access tokens are self-contained JWTs valid until expiry (~1h). After an
-- account deletion, surviving tokens kept passing signature verification, so
-- paired phones/TVs rendered normally until their next refresh. Ownership
-- checks now additionally require the LIVE auth.users row: once deletion
-- cascades it away, every client query sees nothing — immediately.

create or replace function public.caller_user_exists()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from auth.users u
     where u.id = (select auth.uid())
  );
$$;

revoke execute on function public.caller_user_exists() from anon, public;
grant execute on function public.caller_user_exists() to authenticated;

drop policy if exists owners_view_devices    on public.devices;
drop policy if exists owners_manage_library  on public.library_entries;
drop policy if exists owners_manage_profiles on public.profiles;
drop policy if exists owners_manage_settings on public.user_settings;
drop policy if exists owners_manage_progress on public.watch_progress;

create policy owners_view_devices on public.devices
  for select
  using (user_id = (select auth.uid()) and (select public.caller_user_exists()));

create policy owners_manage_library on public.library_entries
  for all
  using (user_id = (select auth.uid()) and (select public.caller_user_exists()))
  with check (user_id = (select auth.uid()) and (select public.caller_user_exists()));

create policy owners_manage_profiles on public.profiles
  for all
  using (user_id = (select auth.uid()) and (select public.caller_user_exists()))
  with check (user_id = (select auth.uid()) and (select public.caller_user_exists()));

create policy owners_manage_settings on public.user_settings
  for all
  using (user_id = (select auth.uid()) and (select public.caller_user_exists()))
  with check (user_id = (select auth.uid()) and (select public.caller_user_exists()));

create policy owners_manage_progress on public.watch_progress
  for all
  using (user_id = (select auth.uid()) and (select public.caller_user_exists()))
  with check (user_id = (select auth.uid()) and (select public.caller_user_exists()));
