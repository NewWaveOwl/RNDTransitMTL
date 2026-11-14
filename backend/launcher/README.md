# Windows server launcher

Independent Kotlin/JVM + Swing application under `backend/launcher`. It manages
the packaged Ktor API, OTP and production STM/exo bridge, with optional
Cloudflare Tunnel or Microsoft Dev Tunnels (Azure). Tunnel settings provides
provider selection, Microsoft browser sign-in and persistent tunnel creation.
Start/Stop/Restart own the tunnel process and show its public HTTPS URL alongside
service status. Microsoft connection status requires a successful public health
probe, not just a printed CLI address. It does not depend on the Android app or
share its UI state.

The main window also polls the bridge's private `/status` for separate STM/exo
prediction and alert freshness, last successful downloads, and stored-credential
presence. A masked credential editor updates only the imported private feed
settings while stopped; blank fields preserve secrets. Imported exo bus sectors
have separate settings and status, with graph/schedule hash verification and a
packaged one-time graph preparation helper. Bridge/process readiness remains
separate from realtime freshness.

Start with [SERVER_GUIDE.md](SERVER_GUIDE.md) for second-PC setup, imports, ports,
authentication, tunnel settings, Android endpoint configuration and packaging.
Run `gradlew -p backend :launcher:test :launcher:installDist` to build independently
of Android. The main class is `transit.launcher.LauncherKt`. Development runs may
set `-Drnd.server.home=<state-directory>` and `-Drnd.server.bundle=<app-image>`;
production resolves its bundle relative to the jpackage executable.

## Ownership and failure behavior

- Per-user exclusive file lock prevents duplicate launchers.
- Processes start via argument vectors, without shells or token arguments.
- The launcher stops only its directly created `Process` instances. After a crash,
  recovery requires both saved process creation time and canonical executable.
- The bridge is ready before OTP starts; OTP's GraphQL catalogue is ready before
  Ktor; Ktor's `/health` is ready before a tunnel. No saved stack PID file is trusted.
- A service process crash stops the remaining stack. Retry is explicit through
  Start or Restart; no endless crash/restart loop runs unattended.
- All services bind to loopback. Publishing a tunnel sets `PUBLIC_API=true` and
  requires existing Supabase session authentication. A named tunnel's remote
  ingress must be configured separately to publish only Ktor.
- Import copies only known graph/feed files into a new directory. The original
  stack, downloaded runtimes, credentials and running processes are not modified.

## Verification performed (2026-11-13)

- Standalone backend + launcher compilation and installDist: passed.
- 23 launcher unit tests and 15 existing backend tests: **38 passed** via direct
  JUnit invocation of Gradle-compiled classes. These include actual child Java
  process ownership, duplicate launch locks, shutdown, verified crash recovery,
  PID mismatch rejection, occupied port detection, import/config validation,
  readiness result/cancellation/deadline handling, secret redaction and log rotation.
- Windows startup follow-up: fixed `InvalidPathException` from resolving
  `services/backend/lib/*` as a Windows filesystem path. The launcher now builds
  the backend classpath from concrete JAR filenames, before starting any service.
  Regression checks cover spaces, ignored non-JAR files and missing/empty library
  directories. Actual packaged backend JVM/main-class validation with `--dry-run`
  passed; this check loads the entry point without starting networking.
- Windows x64 jpackage app-image, bundled Java 21 and separate Java 25 runtime,
  production bridge compilation: passed using the existing OTP 2.10.0 JAR.
- Packaged EXE `--self-check <absolute-PNG-path>`: exit 0, GUI rendered and all
  expected runtime/service paths present; generated image visually inspected.
  Self-check uses isolated state beside its output and starts no service.
- Root-project `:backend:launcher:classes` module wiring: passed without changing
  Android application sources.
- Gradle test-worker execution and real HTTP loopback integration test could not
  run in this sandbox (Windows socket connect permission denied). Unit readiness
  checks use an injected transport; `LauncherHttpIntegrationTest` remains enabled
  for normal Gradle execution/CI on an unrestricted host.
- Target-PC follow-up: the user confirmed startup; the launcher screenshot shows
  Bridge, OTP and Ktor Ready. Uploaded logs show the combined graph loading and
  STM/exo realtime updates being applied, with some individual updates rejected.
  The public tunnel's `/health` response was independently checked and returned
  `{"status":"ready"}`. An Android APK was built with that endpoint and its
  manifest setting and signature were checked.
- End-to-end Android trip generation over the tunnel and tray interaction still
  need device testing. The initial EXE GUI check ran on development Windows build
  26300; the target PC's exact OS build was not independently inspected. No
  cloudflared binary, feed credentials or graph was included in the distributable.

Portable outputs and QA renders live under ignored `launcher/build/`. The source,
package script, guides and tests are committed; generated runtimes/ZIPs are not.

## Microsoft tunnel follow-up verification (2026-11-13)

- Compiled launcher and test sources with the cached Kotlin 2.4.20 compiler and
  Java 21; all 28 launcher unit tests passed via JUnit. New checks cover Microsoft
  config round trips, migration from old properties, auth requirements, CLI lookup
  after a winget PATH change, regional IDs, safe forwarding URL extraction, and
  setup command exit/cancellation with owned child processes.
- Normal Gradle execution is blocked in this environment by daemon socket access;
  in-process offline Gradle also reports an unavailable cached
  error_prone_annotations 2.27.0 dependency. The existing HTTP integration test was
  extended to check API Accept headers and reject redirects. It compiles but its
  execution is blocked by the sandbox's loopback connect restriction.
- Microsoft sign-in, creation and public relay probing still require a check on
  the target PC with its installed CLI/account. No tunnel was created, account
  signed in or server published from the development machine during this change.
- Rebuilt the Windows x64 portable package with the verified launcher JAR.
  Packaged EXE self-check exited 0 with no missing service/runtime files. Both
  the main window (including Tunnel settings) and Microsoft settings dialog were
  rendered and visually inspected without starting services.

## Realtime feeds follow-up verification (2026-11-13)

- All 37 launcher unit tests passed with the cached Kotlin compiler/direct JUnit
  workflow above. The nine new feed tests cover independent predictions/alerts,
  stale/unconfigured/auth failures, last-success retention, JSON status failures,
  credential replacement/preservation, validation before writes, optional exo,
  imported-data restrictions, and secret-free malformed-JSON diagnostics.
- The main window was rendered with mixed feed fixture states (not live API
  observations); the feed credential dialog was also rendered. Both were visually
  inspected. Existing secrets are never supplied to the dialog's password fields.
- No upstream API request was made during these checks. Live bridge status and
  replacement credentials need target-PC verification. Gradle and actual HTTP
  integration execution retain the environment limitations described above.

## exo bus implementation and verification

- Each configured Chrono agency gets its own `EXO_BUS_<agency>` namespace,
  schedule identifier index, API requests, polling interval, retry state and feed
  health. Only explicitly enabled sectors poll upstream. Credentials stay in
  `exo-bus-realtime.local.json`; generated OTP updaters contain only loopback URLs.
- Prepare-ExoBusGraph.ps1 builds a separate combined graph using selected bus GTFS
  archives and OSM, preserving the old source. Its public manifest binds the graph
  and each bus archive by SHA-256. The launcher/bridge reject changed inputs.
- Production generation requests evaluate freshness against actual itineraries.
  Affected journeys get a scheduled retry; healthy realtime alternatives remain.
  Older explicit-scope debug requests retain their previous behavior.
- Compiled launcher and backend with cached Kotlin 2.4.20/Java 21. **44 launcher
  tests and 16 backend tests passed** via direct JUnit. Compiled bridge with Java
  25 against OTP 2.10.0; existing STM/train fixtures and **25 new bus fixture
  checks passed**. No upstream requests are made by these fixtures.
- The Windows PowerShell 5 graph-helper preparation test passed: paths with spaces,
  exact namespaces, safe output paths, original-graph preservation, refusal to
  overwrite a destination, and no import-ready marker for unbuilt inputs.
- Main/feed-sector dialogs were rendered with fixture states and inspected at
  960x720. Live API connectivity, full OTP graph building with real bus archives,
  and real HTTP integration execution still need target-PC verification. This
  environment could not resolve exo.quebec for a live GTFS download; the earlier
  Gradle/loopback restrictions also remain.
