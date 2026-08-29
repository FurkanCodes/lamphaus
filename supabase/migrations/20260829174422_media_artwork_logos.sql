-- Store an optional TMDB logo override alongside poster and backdrop paths.
alter table public.media_artwork_overrides
  add column if not exists logo_path text;
