# TravelTracker shared backend

The owner's 2026-11-22 decision selects Public Nominatim for low-traffic address
lookups and reuses the existing OTP/bridge for point-to-point route previews.
These app-specific endpoints require a verified TravelTracker Auth session. They
never save accounts, catalogue records or trips; those remain TravelTracker-owned.

## Configuration

Use the launcher's TravelTracker settings while stopped, or these environment values
for a source-hosted backend:

| Environment | Purpose |
| --- | --- |
| `TRAVELTRACKER_SUPABASE_URL` | TravelTracker Supabase HTTPS project origin |
| `TRAVELTRACKER_SUPABASE_KEY` | Its publishable/legacy anon key; never a secret/service-role key |
| `NOMINATIM_URL` | Defaults to `https://nominatim.openstreetmap.org`; operator-switchable Nominatim instance |
| `NOMINATIM_USER_AGENT` | Defaults to `TravelTracker/1.0 (+https://github.com/NewWaveOwl/TravelTracker)`; 10–200 printable characters identifying TravelTracker |

Blank TravelTracker Auth fields fall back to the existing RNDTransit project pair.
The backend rejects partial/privileged configuration and fails closed when no Auth
project is configured. Supabase verifies users through `/auth/v1/user`; unverified
JWT payloads are not identities. Auth outages are 503, rejected sessions 401.
Separate projects are supported without changing existing Android generation auth.

## HTTP contract

| Operation | Input | Success response |
| --- | --- | --- |
| `GET /v1/places/search` | One `q`, 3–200 characters | `status: success` or `no_match`, `candidates: [{label,lat,lon,source}]`, `attribution` |
| `GET /v1/places/reverse` | One numeric `lat` and `lon`, finite and in geographic bounds | Same candidate shape; caller keeps the original marker coordinates |
| `POST /v1/routes/plan` | `origin:{lat,lon}`, `destination:{lat,lon}`, `mode`, `departureTime` with offset | `status: success`, `no_route` or `unsupported`; `itineraries` |

Route modes: `pedestrian`, `bike`, `car`, `bus`, `train`. Requests use actual OTP
WALK/BICYCLE/CAR/BUS/RAIL support; plane is unsupported. Each itinerary contains
`id`, `durationSeconds`, `distanceMeters`, `timingBasis` and `legs`. Legs contain
`mode`, `durationSeconds`, `distanceMeters`, ISO-offset `departureTime`/`arrivalTime`,
encoded-polyline `geometry`, optional `routeName`, `realtime`. Timing basis is
`estimated` for street routes, `scheduled` for timetable-only transit and `realtime`
when actual estimated leg timestamps are present. No live road traffic is claimed.
Imported graph permissions and schedule dates determine available routes.

Invalid input is 400; rejected Auth is 401; provider errors/timeouts/capacity are
503. Known backoff includes `Retry-After` and `retryAfterSeconds`. Responses use
`no-store`. Aggregate `GET /health/traveltracker` counters contain no addresses,
coordinates, tokens or user identifiers and make no provider request. Health is
process readiness, not proof of live geocoder or transit-feed availability.

## Public Nominatim operating rules

Follow the [public usage policy](https://operations.osmfoundation.org/policies/nominatim/).
The developer explicitly chose this service. Search requires a user submission;
autocomplete and systematic/bulk scans are forbidden. Search/reverse share one
process-wide queue, at least one second between provider request starts. Do not run
multiple independent backend replicas without a shared limiter/cache. Cache matches,
identify the app, display OpenStreetMap attribution and keep personal/confidential
information out of requests. Keep manual entry and provider switching available.

The bounded memory cache holds 512 entries (positive 24 hours, no-match five minutes).
Queue capacity is four, with four-second provider and seven-second operation
timeouts. Eight operations may enter Auth concurrently; two may perform route I/O.
No automatic geocoder retries, including HTTP engine follow-ups. Provider backoff
is honored. Routine tests use local fixtures, not the public API.

## Development PC and Windows update

In TravelTracker, set `Services:Routing` to the server's **Public HTTPS base URL**:

```powershell
$env:Services__Routing = 'https://YOUR-TUNNEL-18088.YOUR-REGION.devtunnels.ms'
dotnet run --file scripts/StartSupabaseDev.cs
```

The normal .NET launcher inherits this variable. Do not append `/v1/...`, and do
not point the tunnel at OTP or the bridge. Configure the matching TravelTracker
Supabase project in the server's separate settings. No user/database password is
needed by that window. Existing public generation and TravelTracker use one tunnel.

See [SERVER_GUIDE.md](launcher/SERVER_GUIDE.md) for the binary update ZIP/helper,
complete portable package replacement, backups and rollback. Updating application
JARs does not require reimporting private data or rebuilding the OTP graph. Live
address quality, full graph/mode coverage and real authenticated tunnel journeys
must be checked on the configured target PC.

See [live OTP capabilities and reproducible mode verification](RoutingVerification.md)
and [the chosen walking/cycling preview and optional trip time-zone contract](WalkingCyclingPreviews.md).

### Signed in, but address lookup is refused

TravelTracker and RNDTransit may use different Supabase projects. In the stopped
server, open **TravelTracker window → TravelTracker settings** and set the
TravelTracker URL and publishable key to the frontend's exact project pair. On the
development PC, read only these public launcher fields:

```powershell
cd 'K:\CLASSES 26 FALL\WEB_3\ML_4\TravelTracker'
$travelTrackerSettings = Get-Content -Raw .traveltracker-launch.json | ConvertFrom-Json
$travelTrackerSettings | Select-Object SupabaseUrl, PublishableKey
```

Do not change RNDTransit's separate project fields. Restart the shared server after
saving. Missing routing configuration and a mismatched session issuer return safe
`503 authentication_unavailable`, without sending that bearer to the wrong Auth
project. Inspecting an unverified issuer can only reject; identity still requires
Supabase's verified user response. A Routing-only rejection does not sign the user
out of TravelTracker. Sign out and back in once if a previous app version already
revoked the local session after a failed lookup.
