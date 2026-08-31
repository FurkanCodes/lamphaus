-- Continue Watching cards name the episode on series entries.
alter table public.watch_progress add column if not exists episode_label text;
