-- Seekr allowances an account has used up for the day (PLY-SEEK-01).
--
-- One Seekr key serves every device on the account, and Seekr may revoke a
-- key that keeps hitting its daily caps (20 distinct movies, 70 distinct
-- episodes, 5,000 lookups; all reset at midnight UTC). When a lookup is
-- refused, resolve-seek-previews records the cap here so no device asks
-- Seekr again for that kind of title until it lifts; list-integrations reads
-- it to tell the viewer in Settings.
--
-- Reads and writes happen only through Edge Functions with the service role;
-- RLS is enabled with no policies, mirroring integration_credentials.
create table public.seekr_quota_blocks (
  user_id uuid not null references auth.users(id) on delete cascade,
  scope text not null check (scope in ('movie', 'episode', 'all')),
  blocked_until timestamptz not null,
  primary key (user_id, scope)
);
alter table public.seekr_quota_blocks enable row level security;

-- No policies: deny-all by design.
