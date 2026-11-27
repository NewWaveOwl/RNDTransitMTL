-- Run against a disposable/local database after all migrations (#88 live groups).
-- Requires the administrator role. Fixtures and writes roll back.
-- Each failed expectation raises an exception; run with stop-on-error enabled.
begin;
select set_config('rnd.host', gen_random_uuid()::text, true);
select set_config('rnd.member', gen_random_uuid()::text, true);
select set_config('rnd.leaver', gen_random_uuid()::text, true);
select set_config('rnd.outsider', gen_random_uuid()::text, true);
insert into auth.users(id) values
    (current_setting('rnd.host')::uuid), (current_setting('rnd.member')::uuid),
    (current_setting('rnd.leaver')::uuid), (current_setting('rnd.outsider')::uuid);
-- The member has a blank username, so the server falls back to "Traveller".
insert into public.users(user_id, username) values
    (current_setting('rnd.host')::uuid, 'Host Ana'), (current_setting('rnd.member')::uuid, '  '),
    (current_setting('rnd.leaver')::uuid, 'Lee'), (current_setting('rnd.outsider')::uuid, 'Olu');

set local role authenticated;

-- 1. The host creates a group with a valid code and is its first member.
select set_config('request.jwt.claim.sub', current_setting('rnd.host'), true);
select set_config('rnd.group', (public.create_group()).id::text, true);
do $$
declare created public.groups;
begin
    select * into created from public.groups where id = current_setting('rnd.group')::uuid;
    if created.join_code !~ '^[A-HJ-NP-Z2-9]{6}$' then
        raise exception 'Join code outside the shared alphabet: %', created.join_code;
    end if;
    perform set_config('rnd.code', created.join_code, true);
    if (select display_name from public.group_members
        where group_id = created.id and user_id = current_setting('rnd.host')::uuid) is distinct from 'Host Ana' then
        raise exception 'Host membership or server-side display name missing';
    end if;
    begin
        perform public.create_group();
        raise exception 'A user in an open group created a second one';
    exception when raise_exception then
        if sqlerrm <> 'GROUP_ALREADY_JOINED' then raise; end if;
    end;
end $$;

-- 2. A member joins with a lower-case, padded code; retrying is idempotent.
select set_config('request.jwt.claim.sub', current_setting('rnd.member'), true);
do $$
declare target_group uuid := current_setting('rnd.group')::uuid;
begin
    perform public.join_group(' ' || lower(current_setting('rnd.code')) || ' ');
    perform public.join_group(current_setting('rnd.code'));
    if (select count(*) from public.group_members as m where m.group_id = target_group) <> 2 then
        raise exception 'Members are not visible to each other, or the retry duplicated a membership';
    end if;
    if (select display_name from public.group_members as m
        where m.group_id = target_group and m.user_id = current_setting('rnd.member')::uuid) <> 'Traveller' then
        raise exception 'Blank username did not fall back to Traveller';
    end if;

    -- Unauthorized updates: tables are read-only for app users.
    begin
        insert into public.group_members(group_id, user_id, display_name)
        values (target_group, current_setting('rnd.outsider')::uuid, 'Spoofed');
        raise exception 'Direct membership insert was allowed';
    exception when insufficient_privilege then null; end;
    begin
        update public.groups set host_id = current_setting('rnd.member')::uuid where id = target_group;
        raise exception 'Direct host change was allowed';
    exception when insufficient_privilege then null; end;
    begin
        delete from public.group_members as m where m.group_id = target_group;
        raise exception 'Direct membership delete was allowed';
    exception when insufficient_privilege then null; end;
    begin
        perform public.share_group_trip(target_group, '{}'::jsonb);
        raise exception 'A member who is not the host shared a trip';
    exception when raise_exception then
        if sqlerrm <> 'GROUP_NOT_HOST' then raise; end if;
    end;
end $$;

-- 3. An outsider can't join with a wrong code and sees nothing of the group.
select set_config('request.jwt.claim.sub', current_setting('rnd.outsider'), true);
do $$
begin
    begin
        -- 0 is never in the alphabet, so this code can't match by chance.
        perform public.join_group('000000');
        raise exception 'An invalid code joined a group';
    exception when raise_exception then
        if sqlerrm <> 'GROUP_CODE_INVALID' then raise; end if;
    end;
    if exists (select 1 from public.groups where id = current_setting('rnd.group')::uuid)
        or exists (select 1 from public.group_members where group_id = current_setting('rnd.group')::uuid) then
        raise exception 'An outsider can read the group';
    end if;
end $$;

-- 4. A member who leaves removes only their own membership.
select set_config('request.jwt.claim.sub', current_setting('rnd.leaver'), true);
select public.join_group(current_setting('rnd.code'));
select public.leave_group(current_setting('rnd.group')::uuid);
select set_config('request.jwt.claim.sub', current_setting('rnd.member'), true);
do $$
begin
    if (select count(*) from public.group_members where group_id = current_setting('rnd.group')::uuid) <> 2 then
        raise exception 'A member leaving changed other memberships';
    end if;
end $$;

-- 5. The host shares a trip; the member starts it and saves History pointing to it.
select set_config('request.jwt.claim.sub', current_setting('rnd.host'), true);
select set_config('rnd.trip', (public.share_group_trip(current_setting('rnd.group')::uuid, jsonb_build_object(
    'title', 'Old Port walk', 'description', 'Shared group route', 'origin', '{}'::jsonb,
    'destination', '{}'::jsonb, 'legs', '[{}]'::jsonb, 'planned_minutes', 30,
    'transports', '[{}]'::jsonb, 'routes', '[]'::jsonb, 'attraction_intensity', 50,
    'group_id', gen_random_uuid(), 'created_by', gen_random_uuid()))).id::text, true);
do $$
declare shared public.group_trips;
begin
    select * into shared from public.group_trips where id = current_setting('rnd.trip')::uuid;
    if shared.group_id <> current_setting('rnd.group')::uuid
        or shared.created_by <> current_setting('rnd.host')::uuid then
        raise exception 'Shared trip accepted a client-supplied group or creator';
    end if;
    if (select current_group_trip_id from public.groups where id = shared.group_id) <> shared.id then
        raise exception 'Shared trip did not become the current group trip';
    end if;
end $$;

select set_config('request.jwt.claim.sub', current_setting('rnd.member'), true);
do $$
declare trip_id uuid := current_setting('rnd.trip')::uuid;
begin
    perform public.start_group_trip(trip_id);
    perform public.start_group_trip(trip_id);
    if (select count(*) from public.group_trip_participants as p where p.group_trip_id = trip_id) <> 2 then
        raise exception 'Participants are wrong after start and retry';
    end if;

    insert into public.trips(id, owner_id, status, title, description, completed_at, group_trip_id)
    values (gen_random_uuid(), current_setting('rnd.member')::uuid, 'completed',
        'My copy', 'Renamed by me', now(), trip_id);

    begin
        insert into public.trips(id, owner_id, status, title, description, origin, destination,
            legs, planned_minutes, transports, routes, attraction_intensity, completed_at, group_trip_id)
        values (gen_random_uuid(), current_setting('rnd.member')::uuid, 'completed', 'Copy',
            'Copied route', '{}', '{}', '[{}]', 30, '[{}]', '[]', 0, now(), trip_id);
        raise exception 'A group History row stored its own route';
    exception when check_violation then null; end;
    begin
        insert into public.trips(id, owner_id, status, title, description, completed_at)
        values (gen_random_uuid(), current_setting('rnd.member')::uuid, 'completed',
            'Solo', 'Missing route', now());
        raise exception 'A solo History row was saved without a route';
    exception when check_violation then null; end;
    begin
        update public.trips set group_trip_id = null
        where owner_id = current_setting('rnd.member')::uuid;
        raise exception 'group_trip_id became editable';
    exception when insufficient_privilege then null; end;
end $$;

-- 6. An outsider can't start the trip, read it, or save History pointing to it.
select set_config('request.jwt.claim.sub', current_setting('rnd.outsider'), true);
do $$
declare trip_id uuid := current_setting('rnd.trip')::uuid;
begin
    begin
        perform public.start_group_trip(trip_id);
        raise exception 'An outsider started the group trip';
    exception when raise_exception then
        if sqlerrm <> 'GROUP_NOT_MEMBER' then raise; end if;
    end;
    if exists (select 1 from public.group_trips where id = trip_id)
        or exists (select 1 from public.group_trip_participants where group_trip_id = trip_id) then
        raise exception 'An outsider can read the group trip';
    end if;
    begin
        insert into public.trips(id, owner_id, status, title, description, completed_at, group_trip_id)
        values (gen_random_uuid(), current_setting('rnd.outsider')::uuid, 'completed',
            'Stolen', 'Not a participant', now(), trip_id);
        raise exception 'A non-participant saved History for the group trip';
    exception when insufficient_privilege then null; end;
end $$;

-- 7. The host leaves: the group closes, memberships end and the code expires.
select set_config('request.jwt.claim.sub', current_setting('rnd.host'), true);
select public.leave_group(current_setting('rnd.group')::uuid);
select set_config('request.jwt.claim.sub', current_setting('rnd.member'), true);
do $$
begin
    if exists (select 1 from public.groups where id = current_setting('rnd.group')::uuid) then
        raise exception 'The member still reads the closed group';
    end if;
    begin
        perform public.join_group(current_setting('rnd.code'));
        raise exception 'An expired code joined a group';
    exception when raise_exception then
        if sqlerrm <> 'GROUP_CODE_INVALID' then raise; end if;
    end;
    -- Participation outlives membership, so History keeps the shared route and team.
    if not exists (select 1 from public.group_trips where id = current_setting('rnd.trip')::uuid) then
        raise exception 'A participant lost the shared route after the group closed';
    end if;
    if (select count(*) from public.group_trip_participants
        where group_trip_id = current_setting('rnd.trip')::uuid) <> 2 then
        raise exception 'A participant lost the team list after the group closed';
    end if;
    begin
        perform public.start_group_trip(current_setting('rnd.trip')::uuid);
        raise exception 'A closed group trip was started';
    exception when raise_exception then
        if sqlerrm <> 'GROUP_NOT_MEMBER' then raise; end if;
    end;
    -- The member is free to host a new group.
    perform public.create_group();
end $$;

-- 8. The expired code can be reserved again by a new open group.
reset role;
do $$
begin
    if (select closed_at from public.groups where id = current_setting('rnd.group')::uuid) is null then
        raise exception 'Host leaving did not close the group';
    end if;
    insert into public.groups(host_id, join_code)
    values (current_setting('rnd.outsider')::uuid, current_setting('rnd.code'));
end $$;
rollback;
