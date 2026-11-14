# Backend trip generation (#67)

Android **GO** sends the confirmed WGS84 origin, time budget, allowed modes and
per-mode transit zones and walking pace to a Kotlin/Ktor service. The backend evaluates network-linked stops and geographic destinations, validates routes
through OTP, and returns the valid itinerary closest to the selected duration. The app decodes that itinerary once and reuses its geometry
in Preview, Active Trip and saved state. Android does not fall back to invented routes
when the backend is unavailable. Desktop/Web previews retain their existing sample
flow unless supplied a generation endpoint.

## Responsibilities and limits

| Component | Responsibility |
| --- | --- |
| Generation backend (Kotlin/JVM 21, Ktor 3.4.3) | Candidate selection, request validation, OTP calls, time/line/zone checks, cancellation and concurrency cap |
| OTP 2.10 / Java 25 | Streets from OSM, scheduled STM/exo trains, routing and realtime updates |
| Existing realtime bridge | Credentials, source freshness, scoped alerts and scheduled fallback |
| Supabase | Account authentication; local prototype trips continue using the existing saved-state store |
| Android | Exact pin/device origin, loading/cancel/retry, preview, Start Trip, stored trip geometry |

Search uses a deadline of `20 + floor(selectedMinutes / 60)` seconds: 30 minutes
gets 20 seconds, 60 gets 21, 120 gets 22, and 360 gets 26. This search deadline is
separate from the hard maximum trip duration. The search retains its best valid
result and returns it at the deadline or exhaustion; reaching 95% of the selected
duration permits an early exit. Equal-duration ties retain the first accepted option.
A maximum of 128 routing attempts bounds work even if OTP responds immediately.

For transit, candidates favour stops served by allowed routes in selected zones.
The startup catalogue requests stop coordinates; older schemas can fall back to the
route-only catalogue and geographic sampling. Walk/bike alternatives also retain
geographic candidates, including park interiors. With no transit selected, stop
anchors provide known street-access locations without enabling transit legs. An over-budget itinerary can supply
a linked intermediate geometry point for a shorter retry. Previously attempted
points within 50 metres are skipped; previous destinations within 100 metres remain
excluded. Geographic candidates are normally at least 250 metres from the origin
(less for short trips) and 100 metres apart; stop anchors are over 100 metres away.
The app excludes its last ten accepted destinations. The service area matches the Android picker: Montréal centre
45.5019, -73.5673 to the recorded farthest CMM point, plus 5 km. Transit shapes are
checked against ARTM A/B/C/D polygons, including intermediate samples at most 100 m
apart. Missing zone data prevents startup; legacy numeric exo zones are not guessed.

The budget includes final arrival minus the backend's requested departure time,
including the wait before the first leg, **plus one minute per routed kilometre**
for orientation and slowing down. Distance sums every leg, including transit and
cycling. The reserve is rounded up to whole seconds; 25 minutes of travel/waiting
over 5 km consumes a 30-minute budget. `searchWindow` is not a trip-duration limit. The saved
trip records the accepted elapsed time separately from the planner budget and from
the existing 10-second **simulated** playback clock. Live GPS navigation is not added
by this issue. Schedules and estimates describe a proposal and can change afterwards.

Enabled modes are alternatives, not a required leg sequence. Walking-only results
require Walk to be selected: Bus alone must include a bus leg; Walk + Bus may use either. Walking access, egress
and transfers are permitted for transit even if walking-only trips are disabled.
Supported feeds currently cover walking, cycling, STM bus/métro and exo trains.
REM and additional agencies/exo bus feeds require their own graph integration; they
are not silently mapped to another mode. STM métro times are scheduled, not live
arrival predictions. Attraction intensity remains stored but does not rank destinations.
Destinations are random coordinates, not an attraction/POI recommendation catalogue.
Geographic fallback does not require a stop or POI catalogue. OTP must find a connected route to each
candidate; unreachable points are skipped without inventing a connecting path.

The backend binds `walkingPreference` to these speeds; client-supplied numeric speeds
cannot override them. This pace applies to walking access and transfers as well.

| Walking preference | km/h | OTP metres/second |
| --- | --- | --- |
| Amble | 3 | 0.833333... |
| Walk | 4.8 | 1.333333... |
| Dash | 6.8 | 1.888888... |

OTP defines walking speed as the maximum on flat ground; crossings and terrain may
reduce actual average speed. The candidate radius accounts for both the selected
pace and orientation reserve, and the routed itinerary receives the final budget check.

## Run with the Windows launcher

The current setup runs the routing stack on a separate Windows PC using
**RNDTransitServer.exe**. Import the prepared combined STM/exo graph and feed
configuration once, press **Start server**, then wait for Bridge, OTP and Ktor to
show Ready. For remote clients, enable Cloudflare Tunnel and use the launcher's
Public HTTPS address as Android's `generationEndpoint`. The launcher starts and
supervises all services; Android Studio only builds/runs the phone app.

See the [Android development guide](../docs/AndroidDevelopment.md) for Android Studio configuration
and the [Windows server guide](launcher/SERVER_GUIDE.md) for imports,
data paths, authentication and tunnel settings. The following manual workflow is
an alternative for developing services directly on the same PC.

## Manual startup on Windows

Use the combined STM/exo graph and local credentials prepared for #21. See
[RunTripSimulation.md](../docs/RunTripSimulation.md) for the existing realtime stack.
From the repository root, start it in one PowerShell window:

```powershell
.\backend\otp\stm-scheduled\Start-StmRealtimeStack.ps1
```

In a second PowerShell window, start the generation service and leave it running:

```powershell
.\backend\scripts\Start-TripBackend.ps1
```

This reads the actual OTP/bridge ports from `stm-stack-processes.json`. Metadata
queries go directly to the same OTP graph; planning uses the freshness-aware bridge.
The backend downloads and caches the official ARTM zone polygons on first start.
An offline installation needs that file prepared beforehand (`FARE_ZONES_FILE`).

Restart the stack after a PC reboot or after its processes stop. Its
`stm-stack-processes.json` file persists and may still contain old ports; it is not a
readiness check. If backend startup reports an unavailable OTP route catalogue,
start the stack again, wait for the startup script to finish successfully, then
restart the backend so it reads the newly recorded ports. Startup errors identify
OTP catalogue failures separately from ARTM fare-zone loading failures and print
their underlying network error.

If the existing combined stack lives in another checkout, reuse it without copying
the graph or credentials:

```powershell
.\backend\scripts\Start-TripBackend.ps1 -StackDirectory 'K:\CLASSES 26 FALL\APP_DEV\PRJ\RNDTransitMTL\tool\otp\stm-scheduled'
```

Explicit endpoints are also supported:

```powershell
.\backend\scripts\Start-TripBackend.ps1 -OtpEndpoint 'http://127.0.0.1:18086/otp/gtfs/v1' -CatalogueEndpoint 'http://127.0.0.1:18085/otp/gtfs/v1'
```

In a third window, test two generated walking trips, then install Android:

```powershell
.\backend\scripts\Test-GeneratedTrips.ps1
.\backend\scripts\Install-GeneratedTripAndroid.ps1 -Emulator
```

Use `-Phone` for an authorized USB phone. Installation uses `adb reverse` and embeds
`http://127.0.0.1:18088/v1/trips/generate` in the debug manifest. Re-run it after an ADB
reconnection. Without a configured `generationEndpoint`, Android Studio's ordinary
debug build defaults to `http://10.0.2.2:18088/v1/trips/generate` on the emulator's
host PC. A server on another PC requires its public HTTPS endpoint instead; the
local installer is not the remote-server workflow.

The installer uses `ANDROID_HOME`, or the default Windows SDK at
`%LOCALAPPDATA%\Android\Sdk`, and passes that location to Gradle for the build.
For a custom SDK installation, set `ANDROID_HOME` before running it.

The smoke test accepts `-Mode bus`, `-Mode metro`, `-Mode train` or `-Mode bike`, and
`-WalkingPreference Amble`, `Walk` or `Dash` (default `Amble`).
Transit results depend on the current timetable and service hours; increasing
`-Minutes` can help for sparse train service. The scripts report failure when no
itinerary fits rather than claiming a passing live test.

## Loading and timeout behavior

Generation shows a non-interactive 64 dp rounded progress bar using the Bike Speed
selector palette and gradient. Its sweep remains indeterminate throughout the
synchronous backend search. Crossfading messages say "Looking around...", "Trying a
few routes...", and "Finding a good fit..."; these are ongoing-search messages,
not completed stages or percentages. Leaving loading disposes its animation/message
state; retries start fresh. Cancel and navigation invalidate the proposal token and
cancel the client request, preventing stale responses from replacing a newer proposal.

The API allows 40 seconds overall (including authentication/body reading); Android
allows 45 seconds. Backend-to-bridge HTTP requests remain bounded at 30 seconds,
and the bridge keeps 15 seconds per OTP call (including a possible scheduled retry).
The generation deadline cancels any outstanding backend HTTP call at 20-26 seconds.
Only the internal deadline may return the saved best result. Parent cancellation
propagates and must not publish that result. Geometry, navigation, timestamps and
realtime extensions are copied from the winning itinerary without reconstruction.

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
  "walkingPreference": "Walk",
  "modes": ["bus", "metro"],
  "zonesByTransport": {"bus": ["A"], "metro": ["A"]},
  "selectedRouteIds": [],
  "excludedDestinations": []
}
```

Responses distinguish `success`, `no_trip`, `invalid_input`, `unauthorized` and
`unavailable`. Success contains the unchanged origin, accepted destination,
`elapsedSeconds` (travel/waiting plus reserve), `travelTimeSeconds`,
`orientationBufferSeconds`, the named `walkingPreference` and derived `walkingSpeedKmh`,
departure timestamp and the OTP response restricted to the accepted
itinerary. The Kotlin decoder validates geometry and trip invariants before accepting
it. Client disconnection aborts the search and the in-flight fetch. `GET /health`
indicates the server loaded its catalogues; it is not a live upstream-health guarantee.

## Hosting

Use the [Windows server launcher](launcher/README.md) to run OTP, the bridge and
the backend as separate processes.
Keep OTP and the credential-bearing bridge private. Terminate HTTPS at a reverse
proxy in front of the generation API. Measure the graph's memory and concurrent
search load before choosing production machine capacity.

| Environment variable | Meaning |
| --- | --- |
| `OTP_URL` | Planning endpoint, normally the realtime bridge |
| `OTP_CATALOGUE_URL` | Optional direct OTP endpoint for the route catalogue |
| `PORT` | Generation API port; default 18088 |
| `HOST` | Bind address; default 127.0.0.1 |
| `PUBLIC_API` | Set to `true` for a loopback service published through a tunnel; requires Supabase authentication |
| `FARE_ZONES_FILE` | Cached official ARTM GeoJSON path |
| `SUPABASE_URL` | Existing project's HTTPS URL for token verification |
| `SUPABASE_PUBLISHABLE_KEY` | Public Supabase API key; never a service-role key |

Non-loopback binding or `PUBLIC_API=true` requires Supabase configuration. Every
generation request then needs a valid Supabase bearer token; Android reads its
current session token at request time. Loopback development without authentication
permits the app's Guest flow. Hosted guest access is not implemented. Configure
remote debug/release builds with
`-PgenerationEndpoint=https://YOUR-HOST/v1/trips/generate`. The launcher sets
`PUBLIC_API=true` when either tunnel mode is enabled, even though Ktor stays bound
to loopback.

## Automated checks

```powershell
.\gradlew.bat :backend:test :backend:launcher:test
.\gradlew.bat :shared:jvmTest :androidApp:assembleDebug :androidApp:lintDebug
```

Tests cover seeded candidate selection, repeated destinations, no-route candidates,
initial waiting/budget limits, provider failure, deadline/cancellation, mode and line
restrictions, intermediate zones, HTTP validation/authentication/concurrency, exact
origin preservation, accepted geometry and timing/DTO restoration.

## API and data references

- [OTP 2.10 GTFS GraphQL endpoint](https://docs.opentripplanner.org/en/v2.10.0/apis/GTFS-GraphQL-API/)
- [OTP planning arguments](https://docs.opentripplanner.org/api/dev-2.x/graphql-gtfs/queries/planConnection)
- [OTP walking speed preference](https://docs.opentripplanner.org/api/dev-2.x/graphql-gtfs/types/WalkPreferencesInput)
- [OTP Speed scalar (metres/second)](https://docs.opentripplanner.org/api/dev-2.x/graphql-gtfs/types/Speed)
- [OTP 2.10 realtime configuration](https://docs.opentripplanner.org/en/v2.10.0/GTFS-RT-Config/)
- [STM open data](https://www.stm.info/a-propos/documents-donnees/donnees-ouvertes)
- [exo open data](https://exo.quebec/fr/a-propos/donnees-ouvertes)
- [Official ARTM 2026 zone polygons and attribution](data/README.md)

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

### Follow-up: arbitrary coordinates, walking pace and orientation allowance

After the user's algorithm correction, 25 backend tests and 181 shared JVM tests
passed. Android debug assembly and lint passed. The updated smoke script parsed,
and `git diff --check` passed. Coverage includes park-interior sampling without a
stop catalogue, named pace conversion, short-budget search bounds, the exact budget
boundary, mixed-leg distance reserves and pace/reserve DTO restoration. Live-provider,
physical-device and deployment checks remain pending as described above.

## Kotlin/Ktor migration

The generation service now runs as the Gradle `:backend` JVM application. It keeps
the existing HTTP contract, bounded searches, walking paces, orientation allowance,
ARTM zone/line checks and realtime-bridge requests. Node.js sources and their tests
are removed; regression coverage lives in `backend/src/test/kotlin`. The Node counts
above record historical verification, not the current suite. Use JDK 21 for Ktor;
OTP retains its own Java runtime. Configure `PUBLIC_API=true` and Supabase credentials
when publishing a loopback backend through a tunnel.

## Consolidated server layout

All server sources, OTP/feed tools and launch scripts now live under the root
`backend/` folder. See [backend/README.md](README.md). The standalone
`gradlew -p backend` build does not configure Android or require its SDK. Existing
ignored runtime data in another checkout can still be used with `-StackDirectory`;
no credentials, running graph or downloaded runtime files were moved from that checkout.

After the migration, 15 Kotlin backend regression tests and 176 shared JVM tests
passed; Android debug assembly and lint passed. The standalone backend test-class
compilation and installable distribution build passed without configuring Android.
Relocated STM/exo Java bridge regression fixtures and PowerShell syntax checks
also passed. The removed tests exercised the retired
fixed STM/exo demos. Previous Node test results above are historical.

## Duration-matching verification (2026-11-14)

- 22 backend regression tests passed via cached Kotlin compilation and direct JUnit.
  Coverage includes alternatives, explicit Walk requirements, walking connections,
  later/closer candidates, deterministic ties, exact maximum elapsed time, retained
  best at deadline, parent cancellation after finding a valid route, whole-hour
  budget boundaries, stop filtering and geometry-guided retry.
- Changed shared UI/client sources and the loading UI test compiled against the
  cached JVM build. 182 cached shared JVM tests passed with those fresh classes first on the classpath, including existing proposal
  cancellation/supersession, generator/DTO/timing/navigation tests, and a rendered
  loading test verifying indeterminate semantics, moving frames and Cancel actions.
- Fresh Java bridge compilation passed; STM/exo train fallback/recovery fixtures
  and all 25 exo bus fixtures passed without live API requests.
- Loading frames at 412 x 720 were rendered and visually inspected.
- Full Gradle tests/Android assembly could not run: the default wrapper cache is
  protected, distribution downloads cannot resolve DNS, and the installed Gradle
  with a writable cache cannot connect to its daemon in this environment. No new
  APK is claimed. Direct compilation checks do not replace Android assembly/lint.
- A cumulative server update ZIP is generated under ignored
  `backend/launcher/build/release-duration-matching/`. It preserves the prior
  launcher/bridge update and replaces the generation backend; no graph or keys.

On the server PC, apply the update with the launcher closed, restart, confirm the
OTP stop catalogue and current realtime fallback, and run the generated-trip smoke
checks during service hours. On Android, build `:androidApp:assembleDebug` and
`:androidApp:lintDebug`, install using the existing endpoint, inspect loading at
small/large sizes, cancel/back/retry repeatedly, and verify Walk + Bus versus Bus
alone and short/long duration matching. Live routing quality and device behavior
remain target-environment checks.
