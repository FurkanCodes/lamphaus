-- M1 hardening pass, addressing security & performance advisors:

-- 1. Perf: wrap auth.uid() in a scalar subquery so the JWT claim is read once
--    per statement (init plan) instead of per row.
drop policy "owners_manage_profiles" on public.profiles;
create policy "owners_manage_profiles" on public.profiles
  for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy "owners_manage_library" on public.library_entries;
create policy "owners_manage_library" on public.library_entries
  for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy "owners_manage_progress" on public.watch_progress
;
create policy "owners_manage_progress" on public.watch_progress
  for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy "owners_view_devices" on public.devices;
create policy "owners_view_devices" on public.devices
  for select using (user_id = (select auth.uid()));

-- 2. Perf: cover the claimed_by foreign key (used by grant-exchange lookups).
create index idx_pairing_sessions_claimed_by on public.pairing_sessions(claimed_by);

-- 3. Security: rls_auto_enable() is an event-trigger helper owned by postgres;
--    clients never need to call it directly. Event trigger behavior is unaffected
--    because DDL runs as the table owner.
revoke execute on function public.rls_auto_enable() from anon, authenticated, public;
