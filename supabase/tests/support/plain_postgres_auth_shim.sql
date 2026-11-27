-- Only for a disposable plain-Postgres container (not Supabase). Supabase already
-- provides these roles, auth.users and auth.uid(). Run this once before the migrations
-- so the SQL tests can run without the Supabase CLI.
create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;

create schema auth;
create table auth.users (id uuid primary key);

-- Same lookup order as Supabase's auth.uid().
create function auth.uid() returns uuid
language sql stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')
    )::uuid;
$$;

grant usage on schema auth to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;
grant usage on schema public to anon, authenticated;
