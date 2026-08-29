-- Server-driven catalog for single-key artwork providers.
-- Provider IDs are immutable because they namespace encrypted artwork.<id> configs.
create table public.artwork_providers (
  id text primary key,
  display_name text not null,
  purpose text not null,
  help_text text not null,
  key_page_url text not null,
  sort_order integer not null default 0,
  enabled boolean not null default true,
  constraint artwork_providers_id_format
    check (id ~ '^[a-z][a-z0-9_-]{0,63}$'),
  constraint artwork_providers_display_name_length
    check (char_length(display_name) <= 500 and btrim(display_name) <> ''),
  constraint artwork_providers_purpose_length
    check (char_length(purpose) <= 500 and btrim(purpose) <> ''),
  constraint artwork_providers_help_text_length
    check (char_length(help_text) <= 500 and btrim(help_text) <> ''),
  constraint artwork_providers_key_page_url_length
    check (char_length(key_page_url) <= 500 and btrim(key_page_url) <> ''),
  constraint artwork_providers_key_page_url_https
    check (key_page_url like 'https://%')
);

alter table public.artwork_providers enable row level security;

insert into public.artwork_providers
  (id, display_name, purpose, help_text, key_page_url, sort_order)
values
  (
    'tmdb',
    'TMDB',
    'Best source for matching titles and core artwork. Required to match Fanart.tv artwork for TV series.',
    'Go to the TMDB website, copy the v3 API key - not the Read Access Token - and paste it here.',
    'https://www.themoviedb.org/settings/api',
    10
  ),
  (
    'fanart',
    'Fanart.tv',
    'Extra posters, backgrounds, and transparent logos.',
    'Go to the Fanart.tv website, copy your personal API key, and paste it here.',
    'https://fanart.tv/get-an-api-key/',
    20
  );

alter table public.media_artwork_overrides
  drop constraint media_artwork_overrides_poster_provider_check,
  drop constraint media_artwork_overrides_backdrop_provider_check,
  drop constraint media_artwork_overrides_logo_provider_check;

alter table public.media_artwork_overrides
  add constraint media_artwork_overrides_poster_provider_fkey
    foreign key (poster_provider) references public.artwork_providers(id),
  add constraint media_artwork_overrides_backdrop_provider_fkey
    foreign key (backdrop_provider) references public.artwork_providers(id),
  add constraint media_artwork_overrides_logo_provider_fkey
    foreign key (logo_provider) references public.artwork_providers(id);

comment on table public.artwork_providers is
  'Server-controlled catalog of opaque, single-key artwork providers. IDs are immutable encryption namespaces.';
