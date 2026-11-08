begin;

create table public.trips (
    id uuid primary key,
    owner_id uuid not null default auth.uid()
        references auth.users (id),

    status text not null
        check (status in ('generated', 'active', 'completed', 'cancelled')),
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

    created_at timestamptz not null default now()
        check (created_at >= timestamptz '1970-01-01 00:00:00+00'),
    completed_at timestamptz,
    cancelled_at timestamptz,

    constraint trips_completed_timestamp_matches_status
        check ((status = 'completed') = (completed_at is not null)),
    constraint trips_cancelled_timestamp_matches_status
        check ((status = 'cancelled') = (cancelled_at is not null)),
    constraint trips_completed_after_creation
        check (completed_at >= created_at),
    constraint trips_cancelled_after_creation
        check (cancelled_at >= created_at),

    -- Supports the review foreign key, which requires a completed trip.
    constraint trips_id_status_key unique (id, status)
);

create index trips_owner_id_idx on public.trips (owner_id);

create table public.reviews (
    trip_id uuid primary key,
    overall integer not null check (overall between 1 and 5),
    quality integer check (quality between 1 and 5),
    interesting integer check (interesting between 1 and 5),
    fun integer check (fun between 1 and 5),
    comment text not null default '',
    image_url text not null default '',

    -- Postgres supplies this value. The app writes trip_id and review fields.
    required_trip_status text generated always as ('completed'::text) stored,
    constraint reviews_completed_trip_fk
        foreign key (trip_id, required_trip_status)
        references public.trips (id, status) on delete cascade
);

alter table public.trips enable row level security;
alter table public.reviews enable row level security;

revoke all privileges on table public.trips, public.reviews
    from public, anon, authenticated;

grant select, insert, delete on table public.trips, public.reviews
    to authenticated;

-- Keep trip IDs, owners, and review links stable for app users.
grant update (
    status, title, description, image_url, origin, destination, legs,
    planned_minutes, transports, routes, attraction_intensity, zone_ids,
    zones_by_transport, completed_at, cancelled_at
) on table public.trips to authenticated;

grant update (overall, quality, interesting, fun, comment, image_url)
    on table public.reviews to authenticated;

create policy trips_owner_crud
    on public.trips
    for all to authenticated
    using (owner_id = (select auth.uid()))
    with check (owner_id = (select auth.uid()));

create policy reviews_trip_owner_crud
    on public.reviews
    for all to authenticated
    using (
        exists (
            select 1
            from public.trips as trip
            where trip.id = reviews.trip_id
              and trip.owner_id = (select auth.uid())
        )
    )
    with check (
        exists (
            select 1
            from public.trips as trip
            where trip.id = reviews.trip_id
              and trip.owner_id = (select auth.uid())
        )
    );

commit;