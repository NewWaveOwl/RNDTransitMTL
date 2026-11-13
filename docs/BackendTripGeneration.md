# Backend trip generation (#67)

Android **GO** sends the confirmed WGS84 origin, time budget, allowed modes and
per-mode transit zones to a Node.js service. The backend chooses destinations from
the public stops in the serving OTP graph, validates routes through OTP, and returns
one accepted itinerary. The app decodes that itinerary once and reuses its geometry
in Preview, Active Trip and saved state. Android does not fall back to invented routes
when the backend is unavailable. Desktop/Web previews retain their existing sample
flow unless supplied a generation endpoint.

## Responsibilities and limits

| Component | Responsibility |
| --- | --- |
| Generation backend (Node.js 22+) | Candidate selection, request validation, OTP calls, time/line/zone checks, cancellation and concurrency cap |
| OTP 2.10 / Java 25 | Streets from OSM, scheduled STM/exo trains, routing and realtime updates |
| Existing realtime bridge | Credentials, source freshness, scoped alerts and scheduled fallback |
| Supabase | Account authentication; local prototype trips continue using the existing saved-state store |
| Android | Exact pin/device origin, loading/cancel/retry, preview, Start Trip, stored trip geometry |

Search uses at most 16 candidates and a 15-second deadline. Candidate destinations are
at least 250 metres from the origin and 100 metres apart; the app excludes its last ten
accepted destinations. The service area matches the Android picker: Montréal centre
45.5019, -73.5673 to the recorded farthest CMM point, plus 5 km. Transit shapes are
checked against ARTM A/B/C/D polygons, including intermediate samples at most 100 m
apart. Missing zone data prevents startup; legacy numeric exo zones are not guessed.

The budget is final arrival minus the backend's requested departure time, including
the wait before the first leg. `searchWindow` is not a trip-duration limit. The saved
trip records the accepted elapsed time separately from the planner budget and from
the existing 10-second **simulated** playback clock. Live GPS navigation is not added
by this issue. Schedules and estimates describe a proposal and can change afterwards.

Enabled modes are alternatives, not a required leg sequence. Walking access, egress
and transfers are permitted for transit even if walking-only trips are disabled.
Supported feeds currently cover walking, cycling, STM bus/métro and exo trains.
REM and additional agencies/exo bus feeds require their own graph integration; they
are not silently mapped to another mode. STM métro times are scheduled, not live
arrival predictions. Attraction intensity remains stored but does not rank destinations.
Destinations are public stop locations, not an attraction/POI recommendation catalogue.

## Start locally on Windows

Use the combined STM/exo graph and local credentials prepared for #21. See
[RunTripSimulation.md](RunTripSimulation.md) for the existing realtime stack.
From the repository root, start it in one PowerShell window:

```powershell
.\tool\otp\stm-scheduled\Start-StmRealtimeStack.ps1
```

In a second PowerShell window, start the generation service and leave it running:

```powershell
.\tool\otp\Start-TripBackend.ps1
```

This reads the actual OTP/bridge ports from `stm-stack-processes.json`. Metadata
queries go directly to the same OTP graph; planning uses the freshness-aware bridge.
The backend downloads and caches the official ARTM zone polygons on first start.
An offline installation needs that file prepared beforehand (`FARE_ZONES_FILE`).

If the existing combined stack lives in another checkout, reuse it without copying
the graph or credentials:

```powershell
.\tool\otp\Start-TripBackend.ps1 -StackDirectory 'K:\CLASSES 26 FALL\APP_DEV\PRJ\RNDTransitMTL\tool\otp\stm-scheduled'
```

Explicit endpoints are also supported:

```powershell
.\tool\otp\Start-TripBackend.ps1 -OtpEndpoint 'http://127.0.0.1:18086/otp/gtfs/v1' -CatalogueEndpoint 'http://127.0.0.1:18085/otp/gtfs/v1'
```

In a third window, test two generated walking trips, then install Android:

```powershell
.\tool\otp\Test-GeneratedTrips.ps1
.\tool\otp\Install-GeneratedTripAndroid.ps1 -Emulator
```

Use `-Phone` for an authorized USB phone. Installation uses `adb reverse` and embeds
`http://127.0.0.1:18088/v1/trips/generate` in the debug manifest. Re-run it after an ADB
reconnection. Android Studio's ordinary debug build defaults to the emulator host
`http://10.0.2.2:18088/v1/trips/generate`.

The smoke test accepts `-Mode bus`, `-Mode metro`, `-Mode train` or `-Mode bike`.
Transit results depend on the current timetable and service hours; increasing
`-Minutes` can help for sparse train service. The scripts report failure when no
itinerary fits rather than claiming a passing live test.

## Android review

1. Confirm a map pin or an in-area device origin. Select Walk and 30 minutes, then GO.
2. Verify that Preview starts at that exact origin and follows a routed street shape.
3. Select Generate another: the origin stays fixed and the destination changes.
4. Select Start Trip: the same itinerary appears in Active Trip and survives navigation
   and saved-state restoration. Playback remains simulated.
5. Repeat with bus/métro and exo train during service hours. Check walking connections,
   used line labels, selected transit zones and the displayed scheduled/estimated time.
6. Cancel during loading, retry after an unavailable backend, and try an impossible
   short budget. None should create an active trip or replace the confirmed origin.

## HTTP contract

`POST /v1/trips/generate`, `Content-Type: application/json`:

```json
{
  "origin": {"label": "Confirmed pin", "point": {"latitude": 45.50455, "longitude": -73.55610}},
  "plannedMinutes": 30,
  "modes": ["bus", "metro"],
  "zonesByTransport": {"bus": ["A"], "metro": ["A"]},
  "selectedRouteIds": [],
  "excludedDestinations": []
}
```

Responses distinguish `success`, `no_trip`, `invalid_input`, `unauthorized` and
`unavailable`. Success contains the unchanged origin, accepted destination,
`elapsedSeconds`, departure timestamp and the OTP response restricted to the accepted
itinerary. The Kotlin decoder validates geometry and trip invariants before accepting
it. Client disconnection aborts the search and the in-flight fetch. `GET /health`
indicates the server loaded its catalogues; it is not a live upstream-health guarantee.

## Hosting

One VM can initially run OTP, the bridge and the backend as separate processes or
containers. Build the backend container with `docker build -t rnd-generation backend`.
Keep OTP and the credential-bearing bridge private. Terminate HTTPS at a reverse
proxy in front of the generation API. Measure the graph's memory and concurrent
search load before choosing production machine capacity.

| Environment variable | Meaning |
| --- | --- |
| `OTP_URL` | Planning endpoint, normally the realtime bridge |
| `OTP_CATALOGUE_URL` | Optional direct OTP endpoint for stop/route catalogues |
| `PORT` | Generation API port; default 18088 |
| `HOST` | Bind address; default 127.0.0.1 |
| `FARE_ZONES_FILE` | Cached official ARTM GeoJSON path |
| `SUPABASE_URL` | Existing project's HTTPS URL for token verification |
| `SUPABASE_PUBLISHABLE_KEY` | Public Supabase API key; never a service-role key |

Non-loopback binding requires Supabase configuration. In that mode every generation
request needs a valid Supabase bearer token; Android reads the current session token
at request time. Local loopback development permits the app's Guest flow. Hosted
guest access is not implemented. Configure release builds with
`-PgenerationEndpoint=https://YOUR-HOST/v1/trips/generate`; no deployment or public
hostname is created by this change.

## Automated checks

```powershell
node --test backend/test/*.test.mjs
.\gradlew.bat :shared:jvmTest :androidApp:assembleDebug :androidApp:lintDebug
```

Tests cover seeded candidate selection, repeated destinations, no-route candidates,
initial waiting/budget limits, provider failure, deadline/cancellation, mode and line
restrictions, intermediate zones, HTTP validation/authentication/concurrency, exact
origin preservation, accepted geometry and timing/DTO restoration.

## API and data references

- [OTP 2.10 GTFS GraphQL endpoint](https://docs.opentripplanner.org/en/v2.10.0/apis/GTFS-GraphQL-API/)
- [OTP planning arguments](https://docs.opentripplanner.org/api/dev-2.x/graphql-gtfs/queries/planConnection)
- [OTP 2.10 realtime configuration](https://docs.opentripplanner.org/en/v2.10.0/GTFS-RT-Config/)
- [STM open data](https://www.stm.info/a-propos/documents-donnees/donnees-ouvertes)
- [exo open data](https://exo.quebec/fr/a-propos/donnees-ouvertes)
- [Official ARTM 2026 zone polygons and attribution](../backend/data/README.md)

The linked generated GraphQL reference follows OTP development. Keep the application
on the tested OTP 2.10 graph/runtime and verify schema compatibility when upgrading.

## Verification recorded November 12, 2026

- Backend: 20 Node tests passed.
- Shared application: 180 compiled JVM tests passed using direct JUnit execution,
  including GO → Generate another → Start Trip with the same exact pin, distinct
  destinations, preserved accepted geometry and saved timing/DTO round trips.
- Android debug APK assembly and Android lint passed.
- Existing STM/exo bridge Java regression fixtures passed after the catalogue-query update.
- New PowerShell scripts parsed successfully; `git diff --check` passed.

The usual Gradle test worker was blocked by this session's loopback restrictions;
compiled tests were executed directly with the resolved Gradle runtime classpath and
an explicitly writable Skiko data directory. The Android build used the same project
sources and a writable dependency cache.

Live agency requests, the initial ARTM polygon download, physical-device generation,
and hosted deployment were not verified in this restricted session. Run the smoke
script and Android review above against the active combined stack before closing #67.
