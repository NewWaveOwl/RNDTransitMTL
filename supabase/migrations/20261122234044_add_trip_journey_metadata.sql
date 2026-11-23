begin;

-- One-way and legacy records retain a null value. The original Trip row is never
-- overwritten when a new return route is created; the link is saved on that new row.
alter table public.trips add column journey jsonb
    constraint trips_valid_journey check (
        journey is null or coalesce(
            jsonb_typeof(journey) = 'object'
            and journey ->> 'kind' in ('return', 'round_trip')
            and case journey ->> 'kind'
                when 'return' then jsonb_typeof(journey -> 'outbound_trip_id') = 'string'
                    and length(journey ->> 'outbound_trip_id') > 0
                    and journey ->> 'outbound_trip_id' <> id::text
                when 'round_trip' then jsonb_typeof(journey -> 'turnaround') = 'object'
                    and jsonb_typeof(journey -> 'turnaround' -> 'point') = 'object'
                else false
            end, false)
    );

-- Preserve the existing ownership policy and protected owner/id column grants.
grant update (journey) on table public.trips to authenticated;
notify pgrst, 'reload schema';
commit;
