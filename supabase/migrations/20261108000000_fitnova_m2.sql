-- Fit Nova M2: private groups (invite code), public communities, and a leaderboard for each.
-- Additive. Run after 20261107000000_fitnova_m1.sql. All writes go through the security-definer RPCs below.

create table if not exists public.fn_groups (
  id uuid primary key default gen_random_uuid(),
  kind text not null check (kind in ('group', 'community')),
  name text not null check (char_length(btrim(name)) between 2 and 40),
  emoji text not null default '💪' check (char_length(emoji) between 1 and 8),
  owner uuid references auth.users (id) on delete set null,
  invite_code text unique check (invite_code ~ '^[A-Z2-9]{6}$'),
  created_at timestamptz not null default now(),
  check ((kind = 'group') = (invite_code is not null))
);

create table if not exists public.fn_members (
  group_id uuid not null references public.fn_groups (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null default 'member' check (role in ('owner', 'member')),
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);
create index if not exists fn_members_user on public.fn_members (user_id);

-- Membership check used by policies; security definer avoids recursive RLS on fn_members.
create or replace function public.fn_is_member(p_group uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.fn_members m where m.group_id = p_group and m.user_id = (select auth.uid()))
$$;

alter table public.fn_groups enable row level security;
alter table public.fn_members enable row level security;

drop policy if exists "fn_groups visible" on public.fn_groups;
create policy "fn_groups visible" on public.fn_groups for select to authenticated
  using (kind = 'community' or public.fn_is_member(id));

drop policy if exists "fn_groups owner edits" on public.fn_groups;
create policy "fn_groups owner edits" on public.fn_groups for update to authenticated
  using (owner = (select auth.uid())) with check (owner = (select auth.uid()));

drop policy if exists "fn_members visible" on public.fn_members;
create policy "fn_members visible" on public.fn_members for select to authenticated
  using (public.fn_is_member(group_id));

-- Leave a group yourself, or remove someone from a group you own.
drop policy if exists "fn_members leave or remove" on public.fn_members;
create policy "fn_members leave or remove" on public.fn_members for delete to authenticated
  using (
    user_id = (select auth.uid())
    or exists (select 1 from public.fn_groups g where g.id = group_id and g.owner = (select auth.uid()))
  );

create or replace function public.fn_new_invite_code()
returns text language plpgsql volatile set search_path = '' as $$
declare
  code text;
begin
  loop
    select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + floor(random() * 32)::int, 1), '')
      into code from generate_series(1, 6);
    exit when not exists (select 1 from public.fn_groups where invite_code = code);
  end loop;
  return code;
end
$$;

create or replace function public.fn_create_group(p_name text, p_kind text, p_emoji text default '💪')
returns public.fn_groups language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  g public.fn_groups;
begin
  if me is null then raise exception 'Sign in first'; end if;
  if (select count(*) from public.fn_groups where owner = me) >= 10 then
    raise exception 'You can own up to 10 groups';
  end if;
  insert into public.fn_groups (kind, name, emoji, owner, invite_code)
  values (p_kind, btrim(p_name), coalesce(nullif(btrim(p_emoji), ''), '💪'), me,
          case when p_kind = 'group' then public.fn_new_invite_code() end)
  returning * into g;
  insert into public.fn_members (group_id, user_id, role) values (g.id, me, 'owner');
  return g;
end
$$;

create or replace function public.fn_join_group(p_code text)
returns public.fn_groups language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  g public.fn_groups;
begin
  if me is null then raise exception 'Sign in first'; end if;
  select * into g from public.fn_groups where kind = 'group' and invite_code = upper(btrim(p_code));
  if g.id is null then raise exception 'No group with that code'; end if;
  if (select count(*) from public.fn_members where group_id = g.id) >= 50 then
    raise exception 'This group is full';
  end if;
  insert into public.fn_members (group_id, user_id) values (g.id, me) on conflict do nothing;
  return g;
end
$$;

create or replace function public.fn_join_community(p_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'Sign in first'; end if;
  if not exists (select 1 from public.fn_groups where id = p_id and kind = 'community') then
    raise exception 'Community not found';
  end if;
  insert into public.fn_members (group_id, user_id) values (p_id, auth.uid()) on conflict do nothing;
end
$$;

-- Leaving: an owner hands the group to the longest-standing member; an empty group is deleted.
create or replace function public.fn_leave_group(p_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  heir uuid;
begin
  delete from public.fn_members where group_id = p_id and user_id = me;
  if exists (select 1 from public.fn_groups where id = p_id and owner = me) then
    select user_id into heir from public.fn_members where group_id = p_id order by joined_at limit 1;
    if heir is null then
      delete from public.fn_groups where id = p_id and kind = 'group';
      update public.fn_groups set owner = null where id = p_id;
    else
      update public.fn_groups set owner = heir where id = p_id;
      update public.fn_members set role = 'owner' where group_id = p_id and user_id = heir;
    end if;
  end if;
end
$$;

create or replace function public.fn_my_groups()
returns table (id uuid, kind text, name text, emoji text, invite_code text, members integer, is_owner boolean)
language sql stable security definer set search_path = '' as $$
  select g.id, g.kind, g.name, g.emoji, g.invite_code,
         (select count(*)::integer from public.fn_members x where x.group_id = g.id),
         g.owner = auth.uid()
  from public.fn_groups g
  join public.fn_members m on m.group_id = g.id and m.user_id = auth.uid()
  order by m.joined_at
$$;

create or replace function public.fn_communities()
returns table (id uuid, name text, emoji text, members integer, joined boolean)
language sql stable security definer set search_path = '' as $$
  select g.id, g.name, g.emoji,
         (select count(*)::integer from public.fn_members x where x.group_id = g.id) as members,
         exists (select 1 from public.fn_members m where m.group_id = g.id and m.user_id = auth.uid())
  from public.fn_groups g
  where g.kind = 'community'
  order by members desc, g.created_at
  limit 100
$$;

create or replace function public.fn_group_leaderboard(p_id uuid, period text default 'week', tz text default 'UTC')
returns table (user_id uuid, display_name text, points integer, workouts integer, rank integer)
language plpgsql stable security definer set search_path = '' as $$
#variable_conflict use_column
begin
  if not (public.fn_is_member(p_id) or exists (select 1 from public.fn_groups where id = p_id and kind = 'community')) then
    raise exception 'Join this group to see its leaderboard';
  end if;
  return query
  with scored as (
    select m.user_id as owner,
           coalesce(sum(public.fn_points(w.reps, w.seconds, w.form_score, w.tracked)), 0)::integer as points,
           count(w.id)::integer as workouts
    from public.fn_members m
    left join public.fn_workouts w on w.owner = m.user_id and w.started_at >= case period
        when 'week' then date_trunc('week', now(), tz)
        when 'month' then date_trunc('month', now(), tz)
        else '-infinity'::timestamptz end
    where m.group_id = p_id
    group by m.user_id
  )
  select s.owner, coalesce(p.display_name, 'Athlete'), s.points, s.workouts,
         (rank() over (order by s.points desc))::integer
  from scored s left join public.fn_profiles p on p.id = s.owner
  order by s.points desc
  limit 100;
end
$$;

revoke all on function public.fn_new_invite_code() from public, anon, authenticated;
revoke all on function public.fn_create_group(text, text, text) from public, anon;
revoke all on function public.fn_join_group(text) from public, anon;
revoke all on function public.fn_join_community(uuid) from public, anon;
revoke all on function public.fn_leave_group(uuid) from public, anon;
revoke all on function public.fn_my_groups() from public, anon;
revoke all on function public.fn_communities() from public, anon;
revoke all on function public.fn_group_leaderboard(uuid, text, text) from public, anon;
grant execute on function public.fn_create_group(text, text, text), public.fn_join_group(text), public.fn_join_community(uuid),
  public.fn_leave_group(uuid), public.fn_my_groups(), public.fn_communities(), public.fn_group_leaderboard(uuid, text, text)
  to authenticated;

-- Starter communities (no owner).
insert into public.fn_groups (kind, name, emoji)
select 'community', v.name, v.emoji
from (values ('Push-up Club', '💪'), ('Beginners', '🌱'), ('Core Crew', '🧘')) as v(name, emoji)
where not exists (select 1 from public.fn_groups g where g.kind = 'community' and g.name = v.name);
