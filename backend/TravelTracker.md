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
An endpoint outside the loaded street graph returns HTTP 200 with
`status: no_route` and `reason: outside_coverage`; it is not a service outage.
Disconnected direct-mode streets likewise return `no_route`. A wider OSM graph
is required to extend car routing beyond the imported Montreal area; changing
request timeouts or updating application JARs does not add missing roads. See
[the reproduced Montreal to Trois-Rivieres case](CarTransitPreviews.md#montreal-to-trois-rivieres-coverage-correction).

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
The [car/transit preview contract](CarTransitPreviews.md) documents explicit
coverage reasons, boarding/alighting stops, transfers and initial waiting.

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

## Verified M4 update package

The current update is
`backend/launcher/build/updates/TravelTracker-Coverage-20261123/RNDTransitServer-TravelTracker-Update.zip`
(15,228,721 bytes). It contains 34 application/dependency JARs plus updater,
manifest and instructions, built from runtime source commit
`7ca9005005fc74a2cdb9bf4ffedbf135db1171bc`.

SHA-256: `21FA1C2FA10D47B195E585D16AC0FFE4B87825C80CF231C24B2A82ABE7E1C998`.

This supersedes `TravelTracker-Routing-M4-20261122` (runtime `58d68ff`) and the
earlier Nominatim/PowerShell-fix ZIPs. It corrects out-of-map and disconnected
direct-mode responses that previously appeared as HTTP 503. Both installDist
builds passed without warnings; 137 backend/60 launcher tests, eight isolated
updater checks and packaged CheckOnly against the genuine prior portable JARs
passed. All 34 payload JAR hashes/sizes and the current compiled coverage mapping
were verified from the extracted archive. The source commit is inside the ZIP's
README and `SOURCE-COMMIT.txt`; `SOURCE-COMMIT.txt` and `package-evidence.json`
also sit beside the ZIP.

This application update preserves the imported graph. It reports an uncovered
Montreal-to-Trois-Rivieres destination clearly but does not add its missing roads;
that journey requires a separately prepared wider graph. These checks did not
modify the actual Windows server or test live authenticated tunnel requests.
Apply to the stopped existing portable app using the
[update/rollback commands](launcher/SERVER_GUIDE.md), with an explicit
`-UpdateDirectory` pointing to the extracted files.

## Verified regional map package

The wider Montreal-to-Trois-Rivieres map is supplied separately:
`backend/launcher/build/updates/TravelTracker-RegionalMap-20261123/TravelTracker-Montreal-TroisRivieres-Map.zip`.
Size: 276,912,512 bytes. SHA-256:
`49240965425E623F4C231C824566023BA5D7E5AEE512CE3C4A080E5646E2E9C5`.
Its 355,888,004-byte graph has SHA-256
`7C6D4D4B9CE65C50E1005DF545CB4129D753614B19AEE2455A62A002B156AF19`.
All 34 payload files were verified; the archive includes the exact regional OSM
source, ODbL attribution/terms, extraction recipe, build manifest and route proof.

Private OTP checks generated a real outbound car route of 137,371.89 metres /
5,462 seconds and a return route of 136,972.46 metres / 5,413 seconds. All five
mode samples and 12 total candidates passed production-adapter replay. Actual
car geometry passed frontend validation through simulated HTTP and an isolated
test session. The latest verifier gate passed 143 backend and 60 launcher tests,
with no failures, errors, skips or compiler warnings. The runtime ZIP remains
the separately validated `7ca9005` build; the six new numeric-evidence tests and
verifier correction in `7ca4693` do not change its runtime JARs.

Read [the map provenance, verification and installation limits](CarTransitPreviews.md#verified-montreal-to-trois-rivieres-regional-map)
and the archive's `README.md` before importing. It retains the original STM and
EXO_TRAINS schedules; verify their hashes against the server and rebuild first
if additional feeds are installed. Use a new staging copy and a new empty import
destination while stopped, preserving the old data folder for rollback. The
actual target server and authenticated tunnel were not updated or tested.
