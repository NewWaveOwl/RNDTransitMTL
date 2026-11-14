# Trip generation backend

Kotlin/JVM 21 + Ktor 3.4.3. The Android app calls this service; OTP and the
STM/exo bridge stay private.

## Layout

- `src/main/kotlin/transit/`: request validation, arbitrary-point sampling, route
  search, ARTM/line policy, HTTP API and Supabase session verification.
- `src/main/resources/plan.graphql`: coordinate-based OTP planning query.
- `src/test/kotlin/`: generation and API regression tests.
- `otp/`: OTP build configuration and the production STM/exo feed bridge.
- `scripts/`: service startup, Android installation and generated-trip smoke check.
- `launcher/`: independent Windows server GUI and portable EXE packaging; see
  [the server guide](launcher/SERVER_GUIDE.md).
- `data/`: ARTM polygon cache and attribution. Downloaded data is ignored by Git.

## Run the packaged Windows server

For the current second-PC setup, use **RNDTransitServer.exe** from the portable
package. The launcher bundles Java 21, the separate OTP Java 25 runtime, Ktor, OTP
and the production bridge. No Android Studio, Node.js or system Java is required
on that PC. Import the prepared combined STM/exo data into an empty directory once,
then press **Start server** and wait for Bridge, OTP and Ktor to show Ready.

For remote phones/emulators, enable the launcher's Cloudflare Tunnel and configure
the Android app with its Public HTTPS address. Public mode requires the app's
Supabase project and a signed-in user. A Quick Tunnel URL is temporary; changing it
requires rebuilding/reinstalling the phone app. Keep OTP and the bridge private.

See the [Windows server guide](launcher/SERVER_GUIDE.md) for imports, data/config
locations, tunnel settings and package builds, and the
[root README](../README.md#build-and-launch-android-in-android-studio) for Android
Studio's `generationEndpoint` setting. Generated packages and private routing data
are not included when cloning the repository.

## Manual development startup

Use the following alternative when working on the services directly. JDK 21 is
required for the Gradle/Ktor process; the Android SDK is needed only when building
the phone app. OTP requires its separate Java 25 runtime, prepared graph and local
feed configuration. Run commands from the repository root:

```powershell
.\backend\otp\stm-scheduled\Start-StmRealtimeStack.ps1
# Separate window; leave running:
.\backend\scripts\Start-TripBackend.ps1
# Third window; emulator must be running:
.\backend\scripts\Install-GeneratedTripAndroid.ps1 -Emulator
```

The stack writes its current ports into `stm-stack-processes.json`. After rebooting,
start the stack again before Ktor. Do not run this workflow alongside the launcher
on the same ports.

If the existing graph/credentials are in another checkout, keep using them:

```powershell
.\backend\scripts\Start-TripBackend.ps1 -StackDirectory 'K:\CLASSES 26 FALL\APP_DEV\PRJ\RNDTransitMTL\tool\otp\stm-scheduled'
```

For manual development in this checkout, prepare the runtime under `backend/otp`:
`otp.jar`, `java25/`, and the `stm-scheduled/` graph, GTFS archives, adapter index,
router configuration and local feed credentials. Do not commit these runtime files.
`Start-StmOtp.ps1 -Rebuild` refreshes the graph with the prepared input feeds;
`Set-ExoRealtimeToken.ps1` and `Connect-StmRealtime.ps1` configure live credentials.
The bridge needs the combined STM + exo train graph to provide both agencies.

## Independent server build

No Android SDK is required for these commands:

```powershell
.\gradlew.bat -p backend test installDist
.\gradlew.bat -p backend run
```

Alternatively use `:backend:test` or `:backend:run` from the full Gradle project.
The application distribution is `backend/build/install/backend/`. Its launcher can
run independently with Java 21. Keep the `lib` directory beside the `bin` directory.

`OTP_URL` points to the realtime bridge; `OTP_CATALOGUE_URL` points directly to OTP.
Defaults are port 18086 for the bridge and port 18088 for this API. Set
`FARE_ZONES_FILE` to an absolute cache path when running the distribution outside
this folder. The first start otherwise downloads the official ARTM polygons.

## API and rules

`POST /v1/trips/generate` accepts the existing Android JSON contract: origin,
plannedMinutes, modes, walkingPreference, zonesByTransport, selectedRouteIds and
excludedDestinations. Responses retain `success`, `no_trip`, `invalid_input`,
`unauthorized` and `unavailable`, plus the accepted OTP itinerary on success.
`GET /health` means the initial catalogues loaded; it does not continuously test OTP.

Candidates favour selected-line transit stops in allowed fare zones, with geographic
sampling for direct modes and older catalogue schemas. OTP must find a connected
route; the backend never invents an off-network path. Up to 128 attempts fit within
`20 + floor(selectedMinutes / 60)` seconds (20-26 seconds for supported durations),
with four concurrent API requests. The closest valid duration wins; equal durations
keep the first result, and a 95% match allows early exit. The best valid result
survives an internal deadline; caller cancellation still propagates.

The walking speeds are Amble 3, Walk 4.8 and Dash 6.8 km/h. Total elapsed time includes
initial waiting, transfers, walking and one minute per routed kilometre, and cannot
exceed the selected trip duration. Selected modes are alternatives. Walking-only
requires Walk; transit-only selections may use walking connections. Line, intermediate
ARTM zone and prior-destination exclusions remain enforced. API and Android timeouts
are 40/45 seconds; see [generation details](../docs/BackendTripGeneration.md) for
loading behavior, regression coverage and remaining device/server checks.

## Hosting

```powershell
docker build -f backend/Dockerfile -t rnd-generation .
```

The image contains Ktor only. Run OTP and the STM/exo bridge alongside it on a private
network; configure endpoint URLs rather than using container localhost. Mount durable
storage at `/var/lib/rnd` for the polygon cache. Supply `SUPABASE_URL` and
`SUPABASE_PUBLISHABLE_KEY` for public binding. Set `PUBLIC_API=true` when exposing a
loopback service through Cloudflare Tunnel so it also requires authentication.
Hosted guest access is not implemented. The phone uses a configured HTTPS
`generationEndpoint`; OTP and feed keys are never exposed in the app.

See [the integration guide](../docs/BackendTripGeneration.md) for Android checks,
protocol details and official API/data references. Live device, cloud and Docker
runtime checks must be performed on the target host.
