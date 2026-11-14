# Windows server launcher

Independent Kotlin/JVM + Swing application under `backend/launcher`. It manages
the packaged Ktor API, OTP and production STM/exo bridge, with optional
Cloudflare Tunnel. It does not depend on the Android app or share its UI state.

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
- 21 launcher unit tests and 15 existing backend tests: **36 passed** via direct
  JUnit invocation of Gradle-compiled classes. These include actual child Java
  process ownership, duplicate launch locks, shutdown, verified crash recovery,
  PID mismatch rejection, occupied port detection, import/config validation,
  readiness result/cancellation/deadline handling, secret redaction and log rotation.
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
- Live OTP route generation, agency feeds, public tunnel connectivity, tray
  interaction and Windows 10 on the second PC: **not verified here**. The EXE GUI
  check ran on the development host, Windows build 26300. No cloudflared binary,
  feed credentials or graph was included in the distributable.

Portable outputs and QA renders live under ignored `launcher/build/`. The source,
package script, guides and tests are committed; generated runtimes/ZIPs are not.
