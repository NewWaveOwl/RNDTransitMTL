begin;

-- The "user" table was first made in the dashboard. Record it here so a fresh
-- database gets it too; on the live database this is a no-op.
create table if not exists public."user" (
    user_id uuid not null default auth.uid(),
    created_at timestamp with time zone not null default now(),
    preferences json null,
    username text null default ''::text,
    constraint user_pkey primary key (user_id),
    constraint user_user_id_fkey foreign key (user_id) references auth.users (id)
);

-- "user" is a reserved word in Postgres; the app reads "users".
alter table public."user" rename to users;
alter table public.users rename constraint user_pkey to users_pkey;
alter table public.users rename constraint user_user_id_fkey to users_user_id_fkey;

-- Store preferences as a queryable object instead of JSON text.
alter table public.users
    alter column preferences type jsonb using preferences::jsonb;
alter table public.users
    add constraint users_preferences_is_object
    check (preferences is null or jsonb_typeof(preferences) = 'object');

-- The app models username as non-null.
update public.users set username = '' where username is null;
alter table public.users alter column username set not null;

alter table public.users enable row level security;

revoke all privileges on table public.users from public, anon, authenticated;

grant select, insert, delete on table public.users to authenticated;

-- Keep user IDs and creation times stable for app users.
grant update (username, preferences) on table public.users to authenticated;

drop policy if exists users_owner_crud on public.users;
create policy users_owner_crud
    on public.users
    for all to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));

commit;
