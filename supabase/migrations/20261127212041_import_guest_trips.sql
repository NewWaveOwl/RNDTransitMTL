begin;

-- Keep the receipt even if an imported trip is later deleted.
create table public.guest_trip_imports (
    owner_id uuid not null references auth.users(id) on delete cascade,
    transfer_id uuid not null,
    primary key (owner_id, transfer_id)
);

alter table public.guest_trip_imports enable row level security;
revoke all on public.guest_trip_imports from public, anon, authenticated;
grant insert, select on public.guest_trip_imports to authenticated;

create policy guest_trip_imports_owner_select on public.guest_trip_imports
    for select to authenticated using (owner_id = (select auth.uid()));
create policy guest_trip_imports_owner_insert on public.guest_trip_imports
    for insert to authenticated with check (owner_id = (select auth.uid()));

create function public.import_guest_trips(p_owner uuid, p_transfer uuid, p_trips jsonb)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
    entry jsonb;
    trip public.trips;
begin
    if auth.uid() is null or p_owner is distinct from auth.uid() then
        raise exception 'Guest transfer account does not match the session' using errcode = '42501';
    end if;
    if jsonb_typeof(p_trips) is distinct from 'array' or p_trips = '[]'::jsonb then
        raise exception 'Guest transfer requires saved trips' using errcode = '22023';
    end if;

    insert into public.guest_trip_imports (owner_id, transfer_id)
    values (p_owner, p_transfer)
    on conflict (owner_id, transfer_id) do nothing;
    if not found then
        return;
    end if;

    for entry in select value from jsonb_array_elements(p_trips)
    loop
        trip := jsonb_populate_record(null::public.trips, entry);
        if trip.status is null or trip.status not in ('completed', 'cancelled') then
            raise exception 'Guest transfer requires completed or cancelled trips' using errcode = '22023';
        end if;

        insert into public.trips (
            id, owner_id, status, title, description, image_url, origin, destination, legs,
            planned_minutes, transports, routes, attraction_intensity, zone_ids, zones_by_transport,
            created_at, completed_at, cancelled_at, routed_duration_seconds, waiting_time_seconds,
            walking_preference, orientation_buffer_seconds, requested_transport_ids, journey
        ) values (
            trip.id, p_owner, trip.status, trip.title, trip.description, trip.image_url,
            trip.origin, trip.destination, trip.legs, trip.planned_minutes, trip.transports,
            trip.routes, trip.attraction_intensity, trip.zone_ids, trip.zones_by_transport,
            trip.created_at, trip.completed_at, trip.cancelled_at, trip.routed_duration_seconds,
            trip.waiting_time_seconds, trip.walking_preference, trip.orientation_buffer_seconds,
            trip.requested_transport_ids, trip.journey
        ) on conflict (id) do nothing;

        if not found and not exists (
            select 1 from public.trips stored
            where stored.id = trip.id and stored.owner_id = p_owner
              and stored.created_at = trip.created_at and stored.status = trip.status
        ) then
            raise exception 'Guest trip ID conflicts with another record' using errcode = '23505';
        end if;

        if jsonb_typeof(entry -> 'review') = 'object' then
            insert into public.reviews (trip_id, overall, quality, interesting, fun, comment, image_url)
            select trip.id, input.overall, input.quality, input.interesting, input.fun, input.comment, input.image_url
            from jsonb_populate_record(null::public.reviews, entry -> 'review') as input
            on conflict (trip_id) do nothing;
        end if;
    end loop;
end;
$$;

revoke execute on function public.import_guest_trips(uuid, uuid, jsonb) from public, anon;
grant execute on function public.import_guest_trips(uuid, uuid, jsonb) to authenticated;
notify pgrst, 'reload schema';

commit;
