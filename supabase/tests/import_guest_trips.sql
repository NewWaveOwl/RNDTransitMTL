begin;

insert into auth.users (id) values
    ('10000000-0000-4000-8000-000000000001'),
    ('10000000-0000-4000-8000-000000000002');

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-4000-8000-000000000001', true);

do $$
declare
    owner_id uuid := auth.uid();
    transfer_id constant uuid := '20000000-0000-4000-8000-000000000001';
    first_id constant uuid := '30000000-0000-4000-8000-000000000001';
    return_id constant uuid := '30000000-0000-4000-8000-000000000002';
    first_trip jsonb := jsonb_build_object(
        'id', first_id, 'status', 'completed', 'title', 'Guest route',
        'description', 'Guest history', 'image_url', '', 'origin', '{}'::jsonb,
        'destination', '{}'::jsonb, 'legs', '[{}, {"second": true}]'::jsonb,
        'planned_minutes', 10, 'transports', '[{}]'::jsonb, 'routes', '[]'::jsonb,
        'attraction_intensity', 50, 'zone_ids', '[]'::jsonb, 'zones_by_transport', '{}'::jsonb,
        'orientation_buffer_seconds', 0, 'created_at', '2026-11-01T10:00:00Z',
        'completed_at', '2026-11-01T10:10:00Z',
        'review', '{"overall":5,"comment":"Guest review","image_url":""}'::jsonb
    );
    return_trip jsonb := (first_trip - 'review' - 'completed_at') || jsonb_build_object(
        'id', return_id, 'status', 'cancelled', 'created_at', '2026-11-01T10:12:00Z',
        'cancelled_at', '2026-11-01T10:14:00Z',
        'journey', jsonb_build_object('kind', 'return', 'outbound_trip_id', first_id)
    );
    payload jsonb := jsonb_build_array(first_trip, return_trip);
begin
    begin
        perform public.import_guest_trips(owner_id, transfer_id, jsonb_build_array(
            first_trip, return_trip || '{"title":""}'::jsonb
        ));
        raise exception 'Expected invalid trip to abort the import';
    exception when check_violation then
        if exists (select 1 from public.trips) or exists (select 1 from public.reviews)
            or exists (select 1 from public.guest_trip_imports) then
            raise exception 'A failed import left partial data';
        end if;
    end;

    perform public.import_guest_trips(owner_id, transfer_id, payload);
    if (select count(*) from public.trips) <> 2
        or not exists (select 1 from public.reviews where trip_id = first_id and comment = 'Guest review')
        or not exists (
            select 1 from public.trips
            where id = return_id and journey ->> 'outbound_trip_id' = first_id::text
              and cancelled_at = '2026-11-01T10:14:00Z'::timestamptz
        )
        or (select legs -> 1 ->> 'second' from public.trips where id = first_id) is distinct from 'true'
    then
        raise exception 'Import lost guest records, chronology or relationships';
    end if;

    update public.trips set title = 'Online title' where id = first_id;
    update public.reviews set comment = 'Online review' where trip_id = first_id;
    perform public.import_guest_trips(owner_id, '20000000-0000-4000-8000-000000000002', payload);
    if (select count(*) from public.trips) <> 2
        or (select title from public.trips where id = first_id) is distinct from 'Online title'
        or (select comment from public.reviews where trip_id = first_id) is distinct from 'Online review'
    then
        raise exception 'Deduplication overwrote online data';
    end if;
    delete from public.trips where id = return_id;
    perform public.import_guest_trips(owner_id, transfer_id, payload);
    if exists (select 1 from public.trips where id = return_id) then
        raise exception 'Retry restored a trip that was already imported and deleted';
    end if;

    perform set_config('request.jwt.claim.sub', '10000000-0000-4000-8000-000000000002', true);
    if exists (select 1 from public.trips) or exists (select 1 from public.reviews)
        or exists (select 1 from public.guest_trip_imports) then
        raise exception 'Another account can see the transferred data';
    end if;
    begin
        perform public.import_guest_trips(owner_id, transfer_id, payload);
        raise exception 'A stale request was accepted under a different account';
    exception when insufficient_privilege then
        null;
    end;
    begin
        perform public.import_guest_trips(auth.uid(), transfer_id, payload);
        raise exception 'A conflicting trip ID was accepted for a different owner';
    exception when unique_violation then
        null;
    end;
end;
$$;

rollback;
