-- Run against a disposable/local Supabase database after all migrations.
-- Requires the administrator role. Fixtures and writes roll back.
begin;
select set_config('rnd.journey_owner', gen_random_uuid()::text, true);
select set_config('rnd.journey_outbound', gen_random_uuid()::text, true);
select set_config('rnd.journey_return', gen_random_uuid()::text, true);
insert into auth.users(id) values (current_setting('rnd.journey_owner')::uuid);

set local role authenticated;
select set_config('request.jwt.claim.sub', current_setting('rnd.journey_owner'), true);
insert into public.trips(id, owner_id, status, title, description, origin, destination,
    legs, planned_minutes, transports, routes, attraction_intensity, completed_at)
values(current_setting('rnd.journey_outbound')::uuid, current_setting('rnd.journey_owner')::uuid,
    'completed', 'Outbound fixture', 'Original remains unchanged', '{}', '{}',
    '[{}]', 30, '[{}]', '[]', 0, now());
insert into public.trips(id, owner_id, status, title, description, origin, destination,
    legs, planned_minutes, transports, routes, attraction_intensity, completed_at, journey)
values(current_setting('rnd.journey_return')::uuid, current_setting('rnd.journey_owner')::uuid,
    'completed', 'Return fixture', 'New routed record', '{}', '{}',
    '[{}]', 30, '[{}]', '[]', 0, now(), jsonb_build_object('kind', 'return',
        'outbound_trip_id', current_setting('rnd.journey_outbound')));

-- The existing owner can read and update metadata, while legacy rows stay nullable.
update public.trips set journey = journey where id = current_setting('rnd.journey_return')::uuid;
do $$ begin
    if not exists(select 1 from public.trips where id = current_setting('rnd.journey_outbound')::uuid
        and journey is null and title = 'Outbound fixture') then
        raise exception 'Original trip or legacy journey default changed';
    end if;
    if not exists(select 1 from public.trips where id = current_setting('rnd.journey_return')::uuid
        and journey ->> 'outbound_trip_id' = current_setting('rnd.journey_outbound')) then
        raise exception 'Return relationship did not round-trip';
    end if;
    begin
        update public.trips set journey = '{}' where id = current_setting('rnd.journey_return')::uuid;
        raise exception 'Missing journey fields were accepted';
    exception when check_violation then null; end;
    begin
        update public.trips set journey = jsonb_build_object('kind', 'return', 'outbound_trip_id', id::text)
            where id = current_setting('rnd.journey_return')::uuid;
        raise exception 'A trip was allowed to return to itself';
    exception when check_violation then null; end;
    begin
        update public.trips set owner_id = gen_random_uuid() where id = current_setting('rnd.journey_return')::uuid;
        raise exception 'Protected ownership column became editable';
    exception when insufficient_privilege then null; end;
end $$;

update public.trips set journey = '{"kind":"round_trip","turnaround":{"label":"Waypoint","latitude":45.51,"longitude":-73.6}}'
    where id = current_setting('rnd.journey_return')::uuid;
do $$ begin
    if not exists(select 1 from public.trips where id = current_setting('rnd.journey_return')::uuid
        and journey ->> 'kind' = 'round_trip') then raise exception 'Round-trip metadata was not readable'; end if;
end $$;
rollback;
