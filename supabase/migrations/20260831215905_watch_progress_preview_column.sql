-- Continue Watching hydration: each progress row carries a MediaPreview
-- snapshot so entries render even when the title is absent from every
-- loaded home catalog section (mirrors the local Room previewJson column).
alter table public.watch_progress add column if not exists preview jsonb;
