-- Run against a disposable/local Supabase database AFTER applying all migrations.
-- Requires the database administrator role. All fixtures and writes roll back.
begin;

select set_config('rnd.test_owner', gen_random_uuid()::text, true);
select set_config('rnd.test_trip', gen_random_uuid()::text, true);
insert into auth.users (id) values (current_setting('rnd.test_owner')::uuid);

set local role authenticated;
select set_config('request.jwt.claim.sub', current_setting('rnd.test_owner'), true);

-- Omitted metadata models a legacy client: nullable fields and zero reserve.
insert into public.trips (
    id, owner_id, status, title, description, origin, destination, legs,
    planned_minutes, transports, routes, attraction_intensity, completed_at
) values (
    current_setting('rnd.test_trip')::uuid, current_setting('rnd.test_owner')::uuid,
    'completed', 'Migration fixture', 'Rolled back after verification', '{}', '{}',
    '[{}]', 30, '[{}]', '[]', 0, now()
);

do $$
begin
    if not exists (select 1 from public.trips where id = current_setting('rnd.test_trip')::uuid
        and routed_duration_seconds is null and waiting_time_seconds is null
        and walking_preference is null and orientation_buffer_seconds = 0
        and requested_transport_ids is null) then
        raise exception 'Legacy metadata defaults or owner readback failed';
    end if;
end;
$$;

-- Explicit nullable metadata matches the default-encoding DTO write payload.
insert into public.trips (
    id, owner_id, status, title, description, origin, destination, legs,
    planned_minutes, transports, routes, attraction_intensity, cancelled_at,
    routed_duration_seconds, waiting_time_seconds, walking_preference,
    orientation_buffer_seconds, requested_transport_ids
) values (
    gen_random_uuid(), current_setting('rnd.test_owner')::uuid,
    'cancelled', 'Nullable metadata fixture', 'Rolled back', '{}', '{}',
    '[{}]', 30, '[{}]', '[]', 0, now(), null, null, null, 0, null
);

-- Exercises all five new column-level UPDATE grants under the actual owner role.
update public.trips set routed_duration_seconds = 1200, waiting_time_seconds = 180,
    walking_preference = 'Walk', orientation_buffer_seconds = 60,
    requested_transport_ids = '["bus"]'
where id = current_setting('rnd.test_trip')::uuid;

do $$
begin
    if not exists (select 1 from public.trips where id = current_setting('rnd.test_trip')::uuid
        and routed_duration_seconds = 1200 and waiting_time_seconds = 180
        and walking_preference = 'Walk' and orientation_buffer_seconds = 60
        and requested_transport_ids = '["bus"]'::jsonb) then
        raise exception 'Metadata update/readback failed';
    end if;
    begin
        update public.trips set orientation_buffer_seconds = -1
        where id = current_setting('rnd.test_trip')::uuid;
        raise exception 'Negative reserve was accepted';
    exception when check_violation then null;
    end;
    begin
        update public.trips set requested_transport_ids = '{}'
        where id = current_setting('rnd.test_trip')::uuid;
        raise exception 'Non-array requested transports were accepted';
    exception when check_violation then null;
    end;
    begin
        update public.trips set owner_id = gen_random_uuid()
        where id = current_setting('rnd.test_trip')::uuid;
        raise exception 'Owner column unexpectedly became editable';
    exception when insufficient_privilege then null;
    end;
end;
$$;

-- A different identity must not read the fixture, despite the new column grants.
select set_config('request.jwt.claim.sub', gen_random_uuid()::text, true);
do $$
begin
    if exists (select 1 from public.trips where id = current_setting('rnd.test_trip')::uuid) then
        raise exception 'Owner RLS isolation failed';
    end if;
end;
$$;

reset role;
rollback;
