-- Integration credentials (MDBList today; Trakt in a later phase) live in a
-- dedicated deny-all table, deliberately separate from provider_configs: the
-- generic list-provider-configs contract returns decrypted configs, and
-- integration secrets must never surface that way (SHR-PROD-06).
--
-- Reads and writes happen only through Edge Functions with the service role;
-- RLS is enabled with no policies, so no client can select these rows.
create table public.integration_credentials (
  user_id uuid not null references auth.users(id) on delete cascade,
  integration text not null,
  encrypted_credential text not null default '',
  -- Only source ids the user explicitly sees in settings; the edge function
  -- filters aggregate ratings against this list.
  enabled_sources jsonb not null
    default '["imdb","tmdb","trakt","tomatoes","popcorn","metacritic","letterboxd"]'::jsonb,
  updated_at_epoch_millis bigint not null default 0,
  primary key (user_id, integration)
);
alter table public.integration_credentials enable row level security;

-- No policies: deny-all by design, mirroring provider_configs.
