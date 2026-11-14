# Trip generation backend

Kotlin/JVM 21 + Ktor 3.4.3. The Android app calls this service; OTP and the
STM/exo bridge stay private.

All commands below run from the repository root unless stated otherwise.

## How the server works

The Android app and routing server run separately. In the current setup, a second
Windows PC runs **RNDTransitServer.exe**, while Android Studio on the development
PC builds and runs the app on an emulator or phone.

| Component | Role | Default address on the server PC |
| --- | --- | --- |
| Windows launcher | Starts/stops services and displays status and logs | Desktop application |
| Kotlin/Ktor backend | Generates destinations, asks OTP for routes, checks settings and time, and returns the accepted route as JSON | `http://127.0.0.1:18088` |
| OTP | Calculates street/path routes and STM/exo journeys from the prepared graph | `http://127.0.0.1:18085` |
| STM/exo bridge | Handles realtime feeds, freshness and scheduled fallback | `http://127.0.0.1:18086` |
| Cloudflare / Microsoft Dev Tunnels (optional) | Makes Ktor reachable through public HTTPS | Shown under **Public HTTPS** in the launcher |

The phone sends its origin, time, modes, zones and walking pace to Ktor. Ktor chooses
candidate destinations, OTP calculates routes, and Ktor returns one valid itinerary.
The app draws its geometry and displays legs, distance and duration. Supabase
provides account authentication; the public generation API requires the signed-in
user's session token. OTP and the bridge stay private to the server PC.

The server PC, services, tunnel and internet connection must stay running for remote
trip generation. Android Studio does not start the server. `127.0.0.1` refers to
the device making the request; it is not the second PC's address from a phone.

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

1. Extract the complete portable package and run **RNDTransitServer.exe**. Keep its
   `app`, `runtime` and `services` folders beside the EXE. Java, Ktor, OTP and the
   bridge are bundled; Android Studio and Node.js are not required on this PC.
2. On first run, click **Setup / import**, select the prepared `stm-scheduled`
   folder, and choose a separate empty destination. Inputs include the combined
   `graph.obj`, STM/exo schedules, adapter index and local feed configuration.
   The launcher copies these inputs and preserves the source folder.
3. Click **Start server** and wait for **Bridge**, **OTP** and **Ktor** to show
   **Ready**. The imported location is saved as `dataDirectory` in Configuration.
   Check **Realtime feeds** for each agency's prediction/alert freshness and last
   successful update. **Bridge: Ready** alone does not confirm live API access.
   To replace keys, stop the server and use **Feed credentials...**, then Start.
   For exo buses, build/import a combined bus graph using the packaged
   `Prepare-ExoBusGraph.ps1`, then enable the imported sectors in **exo bus
   settings...**. See the Windows server guide for the complete setup. Bus
   predictions/alerts are separate per sector; the Android **Bus** choice uses
   the routes available in that graph.
4. For a phone/emulator on another PC, choose Cloudflare or **Microsoft Dev Tunnels (Azure)** in **Tunnel settings**
   and wait for **Tunnel: Connected**. Use its **Public HTTPS** address in the app.
   Public mode requires the same Supabase project URL and publishable key as
   Android, plus a signed-in user. Do not use a service-role key.

Use `tunnelMode=quick` for a temporary testing address, or a configured named tunnel
for a stable hostname. Quick Tunnel addresses change when the tunnel restarts:
update the app endpoint and rebuild/reinstall. See the
[Windows server guide](launcher/SERVER_GUIDE.md) for configuration, imports,
tunnel setup and packaging.

For Microsoft, install the official `devtunnel` CLI once on the server PC. In
**Tunnel settings**, reuse an existing persistent ID (for example `rndtransit.use`)
or use the sign-in and creation buttons for a new tunnel. Select
`tunnelMode=microsoft`; Start server then hosts it and shows the checked public
HTTPS address. Close any manual `devtunnel host` first. No separate PowerShell
window is needed afterward. Microsoft tunnel URLs are reusable, subject to the
service's inactivity expiration; the server PC still needs to stay on.

The portable package is generated under `backend/launcher/build/`; obtain it from
the developer or build it using the guide. Cloning Git does not download it.
Generated EXEs, APKs, ZIPs, runtimes and build folders are not committed. Graphs
and local feed credentials must be prepared/transferred separately and kept out of Git.

## Update an existing server installation

Stop services and exit the launcher before applying an update ZIP. Extract its
contents beside the existing `RNDTransitServer.exe`, preserving subfolders and
replacing the included files, then reopen the launcher and start the server.
An update ZIP requires the complete portable installation and its existing runtime
and dependency folders; it is not a standalone installation.

Keep the existing graph, schedules and credentials. Duration-matching updates do
not require rebuilding the graph. Adding routes from new exo bus sectors does
require a combined graph containing those schedules. Follow the instructions shipped
with each update and the [server guide](launcher/SERVER_GUIDE.md).

## Connect Android to the server

Use the launcher's **Public HTTPS** address as the app's `generationEndpoint`.
The endpoint is embedded when the APK is built. A changing tunnel URL requires
rebuilding/reinstalling the app; an installed APK does not read repository settings.
See [Android development and APK builds](../docs/AndroidDevelopment.md) for endpoint
configuration, debug installation, release signing and client checks.

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

Keep `backend/otp/stm-scheduled/prepare-stm-index.py`: the stack startup script
runs it when the STM feed or helper changes, or when the adapter index is missing.
It produces `stm-adapter-index.json` from the same `stm-gtfs.zip` used by OTP,
mapping route names, stop codes and verified directions for realtime alerts.
The bridge reads that index and the launcher requires it during data import.
The old `tool/otp/stm-scheduled/` source path was relocated to `backend/otp/`;
existing runtime data in another checkout can still be reused as described above.


## Independent server build

To obtain the source:

```powershell
git clone https://github.com/NewWaveOwl/RNDTransitMTL.git
cd RNDTransitMTL
```

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
are 40/45 seconds; see [generation details](TripGeneration.md) for
loading behavior, regression coverage and remaining device/server checks.

## Regression tests

```powershell
.\gradlew.bat -p backend test :launcher:test
```

Backend and launcher tests run independently of Android. Use the
[generation guide](TripGeneration.md#automated-checks) for integration checks,
realtime fallback fixtures and recorded environment limitations. Live routing,
feed access and remote phone connectivity require the configured server and graph.

## Hosting

Use the [Windows server launcher](launcher/README.md) to run and supervise Ktor,
OTP and the STM/exo bridge as separate processes. Keep OTP and feed credentials
private. Configure public HTTPS access through the launcher's tunnel settings;
public access requires Supabase authentication. Hosted guest access is not implemented.
The phone uses a configured HTTPS `generationEndpoint`.

See [the integration guide](TripGeneration.md) for Android checks,
protocol details and official API/data references. Live device, feed and tunnel
checks must be performed against the configured server.
