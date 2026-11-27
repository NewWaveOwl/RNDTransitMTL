begin;

-- Live groups (#88). Clients only read these tables through row policies; every
-- write goes through the security-definer functions below, which check
-- membership and host rights. Group trips hold the shared route once, and each
-- participant's own History row points to it through trips.group_trip_id.

-- Helpers live outside the API-exposed schemas so PostgREST cannot call them.
create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create table public.groups (
    id uuid primary key default gen_random_uuid(),
    host_id uuid not null references auth.users (id),
    -- Six characters without look-alikes (no O/0, I/1). Kept in sync with GroupJoinCode.
    join_code text not null check (join_code ~ '^[A-HJ-NP-Z2-9]{6}$'),
    created_at timestamptz not null default now(),
    -- Set when the host leaves. A closed group's code no longer joins anything.
    closed_at timestamptz,
    current_group_trip_id uuid,
    constraint groups_closed_after_creation check (closed_at >= created_at)
);

-- Only open groups reserve their code, so codes can be reused after they expire.
create unique index groups_open_join_code_key on public.groups (join_code)
    where closed_at is null;

create table public.group_members (
    group_id uuid not null references public.groups (id) on delete cascade,
    user_id uuid not null references auth.users (id),
    -- Copied from users.username by the server when joining.
    display_name text not null check (display_name ~ '[^[:space:]]'),
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

create index group_members_user_id_idx on public.group_members (user_id);

-- The shared route. Columns and rules mirror public.trips.
create table public.group_trips (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null references public.groups (id),
    created_by uuid not null references auth.users (id),

    title text not null check (title ~ '[^[:space:]]'),
    description text not null check (description ~ '[^[:space:]]'),
    image_url text not null default '',

    origin jsonb not null check (jsonb_typeof(origin) = 'object'),
    destination jsonb not null check (jsonb_typeof(destination) = 'object'),
    legs jsonb not null
        check (jsonb_typeof(legs) = 'array' and legs <> '[]'::jsonb),
    planned_minutes integer not null
        check (planned_minutes between 5 and 360 and planned_minutes % 5 = 0),
    transports jsonb not null
        check (jsonb_typeof(transports) = 'array' and transports <> '[]'::jsonb),
    routes jsonb not null check (jsonb_typeof(routes) = 'array'),
    attraction_intensity real not null
        check (attraction_intensity between 0 and 100),
    zone_ids jsonb not null default '[]'::jsonb
        check (jsonb_typeof(zone_ids) = 'array'),
    zones_by_transport jsonb not null default '{}'::jsonb
        check (jsonb_typeof(zones_by_transport) = 'object'),

    routed_duration_seconds bigint check (routed_duration_seconds > 0),
    waiting_time_seconds bigint check (waiting_time_seconds >= 0),
    walking_preference text check (walking_preference in ('Amble', 'Walk', 'Dash')),
    orientation_buffer_seconds bigint not null default 0
        check (orientation_buffer_seconds >= 0),
    requested_transport_ids jsonb
        check (jsonb_typeof(requested_transport_ids) = 'array'),
    -- A return journey points to one member's own outbound trip, so only a
    -- round trip can be shared.
    journey jsonb constraint group_trips_valid_journey check (
        journey is null or coalesce(
            jsonb_typeof(journey) = 'object'
            and journey ->> 'kind' = 'round_trip'
            and jsonb_typeof(journey -> 'turnaround') = 'object'
            and jsonb_typeof(journey -> 'turnaround' -> 'latitude') = 'number'
            and jsonb_typeof(journey -> 'turnaround' -> 'longitude') = 'number',
            false)
    ),

    created_at timestamptz not null default now(),

    constraint group_trips_orientation_within_duration
        check (routed_duration_seconds is null or orientation_buffer_seconds <= routed_duration_seconds),
    constraint group_trips_waiting_within_duration
        check (routed_duration_seconds is null or waiting_time_seconds is null
            or waiting_time_seconds <= routed_duration_seconds)
);

create index group_trips_group_id_idx on public.group_trips (group_id);

alter table public.groups
    add constraint groups_current_group_trip_fk
        foreign key (current_group_trip_id) references public.group_trips (id);

-- Who started a group trip. Survives leaving the group, so History can still
-- show the participants and read the shared route.
create table public.group_trip_participants (
    group_trip_id uuid not null references public.group_trips (id) on delete cascade,
    user_id uuid not null references auth.users (id),
    display_name text not null check (display_name ~ '[^[:space:]]'),
    started_at timestamptz not null default now(),
    primary key (group_trip_id, user_id)
);

create index group_trip_participants_user_id_idx on public.group_trip_participants (user_id);

-- A member's History row either owns its route (solo) or points to a group trip.
alter table public.trips
    add column group_trip_id uuid references public.group_trips (id),
    alter column origin drop not null,
    alter column destination drop not null,
    alter column legs drop not null,
    alter column planned_minutes drop not null,
    alter column transports drop not null,
    alter column routes drop not null,
    alter column attraction_intensity drop not null,
    add constraint trips_route_owner check (
        (group_trip_id is null) = (
            origin is not null and destination is not null and legs is not null
            and planned_minutes is not null and transports is not null
            and routes is not null and attraction_intensity is not null
        )
    ),
    add constraint trips_group_journey_shared
        check (group_trip_id is null or journey is null);

create index trips_group_trip_id_idx on public.trips (group_trip_id);

-- Helpers. Security definer avoids recursive row policies on group_members.
create function private.is_group_member(p_group_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.group_members as member
        where member.group_id = p_group_id
          and member.user_id = (select auth.uid())
    );
$$;

create function private.is_group_trip_participant(p_group_trip_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.group_trip_participants as participant
        where participant.group_trip_id = p_group_trip_id
          and participant.user_id = (select auth.uid())
    );
$$;

create function private.display_name()
returns text
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(
        (select nullif(btrim(account.username), '') from public.users as account
         where account.user_id = (select auth.uid())),
        'Traveller'
    );
$$;

-- The caller's open group membership, if any.
create function private.open_group_of_caller()
returns uuid
language sql
stable
security definer
set search_path = ''
as $$
    select member.group_id
    from public.group_members as member
    join public.groups as grp on grp.id = member.group_id
    where member.user_id = (select auth.uid()) and grp.closed_at is null
    limit 1;
$$;

revoke all on function private.is_group_member(uuid), private.is_group_trip_participant(uuid),
    private.display_name(), private.open_group_of_caller() from public, anon;
grant execute on function private.is_group_member(uuid), private.is_group_trip_participant(uuid),
    private.display_name(), private.open_group_of_caller() to authenticated;

-- Read-only access for app users; writes only through the functions below.
alter table public.groups enable row level security;
alter table public.group_members enable row level security;
alter table public.group_trips enable row level security;
alter table public.group_trip_participants enable row level security;

revoke all privileges on table public.groups, public.group_members,
    public.group_trips, public.group_trip_participants
    from public, anon, authenticated;

grant select on table public.groups, public.group_members,
    public.group_trips, public.group_trip_participants
    to authenticated;

create policy groups_member_read
    on public.groups for select to authenticated
    using (private.is_group_member(id));

create policy group_members_member_read
    on public.group_members for select to authenticated
    using (private.is_group_member(group_id));

create policy group_trips_member_or_participant_read
    on public.group_trips for select to authenticated
    using (private.is_group_member(group_id) or private.is_group_trip_participant(id));

create policy group_trip_participants_participant_read
    on public.group_trip_participants for select to authenticated
    using (private.is_group_trip_participant(group_trip_id));

-- Only participants can save a History row that points to a group trip.
-- group_trip_id is left out of the trips update grant, so it stays fixed.
create policy trips_group_participant_insert
    on public.trips as restrictive for insert to authenticated
    with check (group_trip_id is null or private.is_group_trip_participant(group_trip_id));

-- Creates an open group hosted by the caller and returns it.
-- Errors: GROUP_NOT_SIGNED_IN, GROUP_ALREADY_JOINED, GROUP_CODE_GENERATION_FAILED.
create function public.create_group()
returns public.groups
language plpgsql
security definer
set search_path = ''
as $$
declare
    alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    caller uuid := (select auth.uid());
    random_bytes bytea;
    code text;
    created public.groups;
begin
    if caller is null then
        raise exception 'GROUP_NOT_SIGNED_IN';
    end if;
    if private.open_group_of_caller() is not null then
        raise exception 'GROUP_ALREADY_JOINED';
    end if;

    for attempt_number in 1..10 loop
        -- 32 symbols divide 256 evenly, so each byte maps without bias.
        random_bytes := uuid_send(gen_random_uuid());
        code := '';
        for byte_index in 0..5 loop
            code := code || substr(alphabet, get_byte(random_bytes, byte_index) % 32 + 1, 1);
        end loop;
        begin
            insert into public.groups (host_id, join_code)
            values (caller, code)
            returning * into created;
            exit;
        exception when unique_violation then
            created := null;
        end;
    end loop;

    if created.id is null then
        raise exception 'GROUP_CODE_GENERATION_FAILED';
    end if;

    insert into public.group_members (group_id, user_id, display_name)
    values (created.id, caller, private.display_name());
    return created;
end;
$$;

-- Joins the open group with this code. Joining the same group again returns it
-- unchanged, so a client can safely retry after losing its connection.
-- Errors: GROUP_NOT_SIGNED_IN, GROUP_CODE_INVALID, GROUP_ALREADY_JOINED.
create function public.join_group(p_code text)
returns public.groups
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := (select auth.uid());
    target public.groups;
    current_group uuid;
begin
    if caller is null then
        raise exception 'GROUP_NOT_SIGNED_IN';
    end if;

    select * into target from public.groups
    where join_code = upper(btrim(coalesce(p_code, ''))) and closed_at is null;
    if not found then
        raise exception 'GROUP_CODE_INVALID';
    end if;

    current_group := private.open_group_of_caller();
    if current_group = target.id then
        return target;
    end if;
    if current_group is not null then
        raise exception 'GROUP_ALREADY_JOINED';
    end if;

    insert into public.group_members (group_id, user_id, display_name)
    values (target.id, caller, private.display_name());
    return target;
end;
$$;

-- Leaves a group. When the host leaves, the group closes: its code expires and
-- every membership ends. Members' own trips and History are unaffected.
create function public.leave_group(p_group_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := (select auth.uid());
    target public.groups;
begin
    select * into target from public.groups where id = p_group_id for update;
    if not found or target.closed_at is not null then
        return;
    end if;

    if target.host_id = caller then
        update public.groups set closed_at = now() where id = p_group_id;
        delete from public.group_members where group_id = p_group_id;
    else
        delete from public.group_members where group_id = p_group_id and user_id = caller;
    end if;
end;
$$;

-- The host shares a planned route with the group. It becomes the group's
-- current trip, and the host is its first participant.
-- Errors: GROUP_NOT_HOST.
create function public.share_group_trip(p_group_id uuid, p_trip jsonb)
returns public.group_trips
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := (select auth.uid());
    shared public.group_trips;
begin
    if not exists (
        select 1 from public.groups
        where id = p_group_id and host_id = caller and closed_at is null
    ) then
        raise exception 'GROUP_NOT_HOST';
    end if;

    insert into public.group_trips (
        group_id, created_by, title, description, image_url, origin, destination,
        legs, planned_minutes, transports, routes, attraction_intensity, zone_ids,
        zones_by_transport, routed_duration_seconds, waiting_time_seconds,
        walking_preference, orientation_buffer_seconds, requested_transport_ids, journey
    )
    select
        p_group_id, caller, input.title, input.description, coalesce(input.image_url, ''),
        input.origin, input.destination, input.legs, input.planned_minutes, input.transports,
        input.routes, input.attraction_intensity, coalesce(input.zone_ids, '[]'::jsonb),
        coalesce(input.zones_by_transport, '{}'::jsonb), input.routed_duration_seconds,
        input.waiting_time_seconds, input.walking_preference,
        coalesce(input.orientation_buffer_seconds, 0), input.requested_transport_ids, input.journey
    from jsonb_populate_record(null::public.group_trips, p_trip) as input
    returning * into shared;

    update public.groups set current_group_trip_id = shared.id where id = p_group_id;

    insert into public.group_trip_participants (group_trip_id, user_id, display_name)
    values (shared.id, caller, private.display_name());
    return shared;
end;
$$;

-- A member starts the group's current trip; they are recorded as a participant.
-- Errors: GROUP_TRIP_INVALID, GROUP_NOT_MEMBER.
create function public.start_group_trip(p_group_trip_id uuid)
returns public.group_trips
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := (select auth.uid());
    shared public.group_trips;
begin
    select * into shared from public.group_trips where id = p_group_trip_id;
    if not found then
        raise exception 'GROUP_TRIP_INVALID';
    end if;
    if not private.is_group_member(shared.group_id) then
        raise exception 'GROUP_NOT_MEMBER';
    end if;
    if not exists (
        select 1 from public.groups
        where id = shared.group_id and closed_at is null and current_group_trip_id = shared.id
    ) then
        raise exception 'GROUP_TRIP_INVALID';
    end if;

    insert into public.group_trip_participants (group_trip_id, user_id, display_name)
    values (shared.id, caller, private.display_name())
    on conflict do nothing;
    return shared;
end;
$$;

revoke execute on function public.create_group(), public.join_group(text),
    public.leave_group(uuid), public.share_group_trip(uuid, jsonb),
    public.start_group_trip(uuid) from public, anon;
grant execute on function public.create_group(), public.join_group(text),
    public.leave_group(uuid), public.share_group_trip(uuid, jsonb),
    public.start_group_trip(uuid) to authenticated;

-- Live membership updates. The publication only exists on Supabase.
do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        alter publication supabase_realtime add table public.groups, public.group_members;
    end if;
end $$;

notify pgrst, 'reload schema';
commit;
