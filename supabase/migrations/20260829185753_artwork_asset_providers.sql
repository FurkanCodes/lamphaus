-- Persist artwork provider identity beside the existing reference columns.
-- Existing references are TMDB paths; Fanart.tv references are HTTPS URLs.
alter table public.media_artwork_overrides
  add column poster_provider text not null default 'tmdb',
  add column backdrop_provider text not null default 'tmdb',
  add column logo_provider text not null default 'tmdb';

alter table public.media_artwork_overrides
  add constraint media_artwork_overrides_poster_provider_check
    check (poster_provider in ('tmdb', 'fanart')),
  add constraint media_artwork_overrides_backdrop_provider_check
    check (backdrop_provider in ('tmdb', 'fanart')),
  add constraint media_artwork_overrides_logo_provider_check
    check (logo_provider in ('tmdb', 'fanart'));

comment on table public.media_artwork_overrides is
  'Profile-scoped artwork overrides. Each reference is qualified by its provider: TMDB paths or Fanart.tv HTTPS URLs.';
comment on column public.media_artwork_overrides.poster_path is
  'Artwork reference for the poster slot: a TMDB path or Fanart.tv HTTPS URL.';
comment on column public.media_artwork_overrides.backdrop_path is
  'Artwork reference for the backdrop slot: a TMDB path or Fanart.tv HTTPS URL.';
comment on column public.media_artwork_overrides.logo_path is
  'Artwork reference for the logo slot: a TMDB path or Fanart.tv HTTPS URL.';
comment on column public.media_artwork_overrides.poster_provider is
  'Provider for poster_path: tmdb or fanart.';
comment on column public.media_artwork_overrides.backdrop_provider is
  'Provider for backdrop_path: tmdb or fanart.';
comment on column public.media_artwork_overrides.logo_provider is
  'Provider for logo_path: tmdb or fanart.';
