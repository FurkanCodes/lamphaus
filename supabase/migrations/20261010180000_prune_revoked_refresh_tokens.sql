-- Auth keeps every refresh token it rotates out. With rotation on, each
-- launch and each hour a device is in use revokes one, about 0.45 KB with its
-- indexes, and none is ever deleted: the largest per-account growth on the
-- free plan's 500 MB.
--
-- A revoked token matters again only when a client refreshed but could not
-- store the answer and presents the old token: Auth then returns the
-- session's active token, which needs that token's direct parent. Within the
-- reuse interval (seconds) any revoked token still counts. So every revoked
-- token older than a day that is not the parent of an active token is dead
-- weight; deleting it never signs anyone out.

create or replace function public.prune_revoked_refresh_tokens()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from auth.refresh_tokens r
   where r.revoked
     and r.updated_at < now() - interval '1 day'
     and not exists (
       select 1 from auth.refresh_tokens c
        where c.parent = r.token
          and not c.revoked
     );
$$;

revoke execute on function public.prune_revoked_refresh_tokens() from anon, authenticated, public;

do $$
begin
  if exists (select 1 from cron.job where jobname = 'prune-refresh-tokens') then
    perform cron.unschedule('prune-refresh-tokens');
  end if;
end $$;

select cron.schedule('prune-refresh-tokens', '53 3 * * *', $$select public.prune_revoked_refresh_tokens()$$);
