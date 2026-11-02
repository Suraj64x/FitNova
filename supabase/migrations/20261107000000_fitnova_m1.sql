-- Fit Nova M1: workouts, leaderboard display names, and the global leaderboard.
-- Additive only: the old app's tables (entries, v2_records, ...) and edge functions are untouched,
-- and accounts keep coming from the existing nova-auth function (auth.users).

create table if not exists public.fn_profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 40),
  updated_at timestamptz not null default now()
);

alter table public.fn_profiles enable row level security;

drop policy if exists "fn_profiles readable by members" on public.fn_profiles;
create policy "fn_profiles readable by members" on public.fn_profiles
  for select to authenticated using (true);

drop policy if exists "fn_profiles insert own" on public.fn_profiles;
create policy "fn_profiles insert own" on public.fn_profiles
  for insert to authenticated with check (id = (select auth.uid()));

drop policy if exists "fn_profiles update own" on public.fn_profiles;
create policy "fn_profiles update own" on public.fn_profiles
  for update to authenticated using (id = (select auth.uid())) with check (id = (select auth.uid()));

create table if not exists public.fn_workouts (
  id uuid primary key,
  owner uuid not null default auth.uid() references auth.users (id) on delete cascade,
  exercise text not null check (exercise ~ '^[a-z_]{2,32}$'), -- ids from Exercise.kt; new moves need no migration
  reps integer not null default 0 check (reps between 0 and 1000),
  seconds integer not null default 0 check (seconds between 0 and 7200),
  form_score smallint not null default 0 check (form_score between 0 and 100),
  tracked boolean not null default false,
  started_at timestamptz not null check (started_at <= now() + interval '1 day'),
  created_at timestamptz not null default now()
);

create index if not exists fn_workouts_owner_started on public.fn_workouts (owner, started_at desc);
create index if not exists fn_workouts_started on public.fn_workouts (started_at);

alter table public.fn_workouts enable row level security;

drop policy if exists "fn_workouts own rows" on public.fn_workouts;
create policy "fn_workouts own rows" on public.fn_workouts
  for all to authenticated
  using (owner = (select auth.uid()))
  with check (owner = (select auth.uid()));

-- Same formula as points() in app/src/main/java/app/fitnova/domain/Workout.kt:
-- reps scored by form, or 1 point per 2 seconds for holds (stored with reps = 0); manual counts half.
create or replace function public.fn_points(reps integer, seconds integer, form_score integer, tracked boolean)
returns integer
language sql
immutable
set search_path = ''
as $$
  select round(
    (case when reps = 0 then seconds / 2 else reps end)
    * (case when tracked then form_score / 100.0 else 0.5 end)
  )::integer
$$;

-- Global leaderboard. security definer so members can rank everyone without reading each other's rows.
create or replace function public.fn_leaderboard(period text default 'week', tz text default 'UTC')
returns table (user_id uuid, display_name text, points integer, workouts integer, rank integer)
language sql
stable
security definer
set search_path = ''
as $$
  with scored as (
    select
      w.owner,
      sum(public.fn_points(w.reps, w.seconds, w.form_score, w.tracked))::integer as points,
      count(*)::integer as workouts
    from public.fn_workouts w
    where w.started_at >= case period
        when 'week' then date_trunc('week', now(), tz)
        when 'month' then date_trunc('month', now(), tz)
        else '-infinity'::timestamptz
      end
    group by w.owner
  )
  select s.owner, coalesce(p.display_name, 'Athlete'), s.points, s.workouts,
         (rank() over (order by s.points desc))::integer
  from scored s
  left join public.fn_profiles p on p.id = s.owner
  order by s.points desc
  limit 100
$$;

revoke all on function public.fn_leaderboard(text, text) from public, anon;
grant execute on function public.fn_leaderboard(text, text) to authenticated;
