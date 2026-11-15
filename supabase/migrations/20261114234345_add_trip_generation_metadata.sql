begin;

-- Nullable metadata keeps trips written before real routing readable.
alter table public.trips
    add column routed_duration_seconds bigint
        check (routed_duration_seconds > 0),
    add column waiting_time_seconds bigint
        check (waiting_time_seconds >= 0),
    add column walking_preference text
        check (walking_preference in ('Amble', 'Walk', 'Dash')),
    add column orientation_buffer_seconds bigint not null default 0
        check (orientation_buffer_seconds >= 0),
    add column requested_transport_ids jsonb
        check (jsonb_typeof(requested_transport_ids) = 'array'),
    add constraint trips_orientation_within_duration
        check (routed_duration_seconds is null or orientation_buffer_seconds <= routed_duration_seconds),
    add constraint trips_waiting_within_duration
        check (routed_duration_seconds is null or waiting_time_seconds is null
            or waiting_time_seconds <= routed_duration_seconds);

-- Extend the existing editable-column list; retain owner/id protection and RLS.
grant update (
    routed_duration_seconds, waiting_time_seconds, walking_preference,
    orientation_buffer_seconds, requested_transport_ids
) on table public.trips to authenticated;

notify pgrst, 'reload schema';
commit;
