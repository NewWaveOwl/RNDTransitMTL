# RNDTransit Server for Windows

## Run on the second PC

1. Extract the entire portable ZIP into a folder you can read. Keep `app`,
   `runtime`, `services` and the EXE together. Double-click `RNDTransitServer.exe`.
   Normal operation needs no administrator rights, Android Studio, Gradle, Node,
   Python, system Java or Java compiler. Windows x64 is required.
2. Transfer your prepared `stm-scheduled` data folder privately to the second PC.
   Required: `graph.obj`, `stm-adapter-index.json`, `stm-gtfs.zip`,
   `stm-upstream.json` (or the original `router-config.json` with the STM API key).
   For exo realtime also transfer `exo-realtime.local.json` and
   `exo-trains-gtfs.zip`. Use the combined STM/exo graph, not the old STM-only graph.
   The graph must match the packaged OTP version. Do not send credentials in Git
   or a public ZIP. The EXE package deliberately excludes them.
3. Click **Setup / import**, choose that source folder, then choose a separate,
   empty folder for the launcher's copy. The source is not modified. The import
   copies a fixed list of data files, including private feed configuration.
   The destination must be new/empty; a failed partial copy can be retried in a
   new empty folder. Use Configuration to select a previously successful import.
4. Click **Start server**. The bridge starts first, OTP loads the graph, then
   Ktor loads its catalogue. OTP can take several minutes. The first Ktor start
   downloads the official ARTM fare-zone polygons (cached afterward).
5. Green Ready indicators mean the services responded, not that every agency
   feed is fresh. Agency outages/stale data preserve scheduled routing. Bridge
   logs and its private `/status` endpoint report the actual feed freshness.
6. **Stop server** stops only this launcher's processes. **Restart / retry**
   reloads configuration and restarts them. A process crash stops the remaining
   stack with an error; inspect logs and click Start after correcting the cause.

Closing the window offers **Stop services and exit**, **Keep running in tray**,
or **Cancel**. Keeping it in the tray keeps supervision active. Double-click the
tray icon to restore it. The PC and internet must stay on. Services do not start
automatically after a Windows reboot; open the launcher and press Start.

The app writes configuration, imported data, cache, ownership records and rotated
logs under `%LOCALAPPDATA%\RNDTransitServer`. Files are never written into the
application folder. **Configuration**, **Data folder**, **Full logs** and **Guide**
open their respective files/folders. Logs retain up to roughly 8 MB per component.
The per-user launcher lock prevents duplicates. On recovery, a saved PID is only
stopped if its creation time AND executable match; unverified processes require
manual attention. Port conflicts are reported without killing their owners.

## Settings

Configuration opens `server.properties` in Notepad. Save, then Restart. Java
properties paths use escaped backslashes (the generated file already does this);
forward slashes such as `C:/RND/data` are also supported. Defaults:

| Setting | Default / purpose |
|---|---|
| dataDirectory | Dedicated imported stack directory |
| apiPort | 18088; Ktor, bound only to 127.0.0.1 |
| otpPort | 18085; private OTP |
| bridgePort | 18086; private STM/exo bridge |
| otpMemoryMb | 4096; increase if the graph exceeds available heap |
| tunnelMode | off, quick or named |
| cloudflared | Absolute path to separately installed cloudflared.exe |
| tunnelTokenFile | Path to a private named-tunnel token file, never the token itself |
| publicAddress | Named tunnel's HTTPS URL |
| supabaseUrl | Your project's HTTPS URL |
| supabaseKey | Its publishable key, never a service-role key |

Leave enough RAM for Windows and the other services beyond OTP's configured heap.
There are no automatic large downloads or graph rebuilds in the launcher. Prepare
and refresh schedules/graph/index using the existing backend/otp tools in the
development checkout, then import a new copy. An expired schedule needs a refreshed
graph. Missing credentials, runtimes, data and occupied ports produce setup errors.

## Optional Cloudflare Tunnel

Install cloudflared from Cloudflare's official distribution. Local-only mode
works without Cloudflare. Set `tunnelMode=quick` for a temporary testing address,
or `tunnelMode=named` for a remotely managed named tunnel with a stable hostname.
Configure `cloudflared`, `supabaseUrl` and `supabaseKey` before enabling either.
Both modes set `PUBLIC_API=true`; signed-in users must send a valid Supabase
session token. The launcher does not provide hosted guest access.

For a named tunnel:

1. In Cloudflare, create/configure your remotely managed tunnel. Route ONE public
   hostname to `http://127.0.0.1:18088` (or your configured apiPort). Do not add
   OTP/bridge routes. Review the tunnel's remote configuration; the launcher
   cannot validate or restrict other hostnames in your Cloudflare account.
2. Save its connector token into a private text file outside the app folder.
   Protect it with Windows user permissions. Set `tunnelTokenFile` to its path
   and `publicAddress` to your HTTPS hostname. Use a cloudflared version supporting
   `TUNNEL_TOKEN_FILE`. The token goes through the file/environment mechanism,
   not command arguments or logs.
3. Start the server. The tunnel starts after Ktor readiness and uses loopback
   metrics on port 18089. Connected means cloudflared's `/ready` succeeded;
   separately check your public hostname, TLS and API response from another
   network. A configured hostname displayed in the UI is not an external test.

Quick mode displays the generated `trycloudflare.com` URL. That address changes
after restarting. A pre-existing cloudflared config.yml in your user profile can
interfere with Quick Tunnels; use named mode or resolve that configuration first.
The tunnel only forwards the generation API. OTP, feed credentials and bridge
remain loopback-only. No Windows inbound firewall opening or router port-forward
is needed for tunnel operation.

## Android public endpoint

Build/install the app from the development PC using the public URL:

```powershell
.\gradlew.bat :androidApp:assembleDebug '-PgenerationEndpoint=https://YOUR-HOST/v1/trips/generate'
# Then install androidApp/build/outputs/apk/debug/androidApp-debug.apk.
```

Use a real signed-in Supabase user on the phone. A tunnel-published local service
still requires authentication. Emulator-only localhost access can use the existing
`backend/scripts/Install-GeneratedTripAndroid.ps1 -Emulator` on the same PC as the
services. A remote phone uses HTTPS, not localhost, 10.0.2.2 or the server's private
OTP port. Quick tunnel URL changes require rebuilding the current app configuration.

Verify: pin a Montréal origin, walking, 30 minutes, GO; generate another destination;
Start Trip preserves the accepted street/path route. Then test STM/exo with zones
and enough time during service hours. The trip algorithm/Android contract are
unchanged by this launcher. Progress simulation remains as implemented in the app.

## Build the package on the development PC

Requires Windows x64, JDK 21 with jpackage/jlink, the prepared OTP JAR and its JDK 25,
and the repository's Gradle dependencies. No Android SDK is required:

```powershell
.\backend\scripts\Package-Server.ps1 `
  -OtpJar 'C:/prepared-otp/otp.jar' `
  -OtpJdk 'C:/prepared-otp/java25/jdk-25' `
  -Jdk 'C:/Program Files/Microsoft/jdk-21'
```

The script runs backend and launcher tests, packages both Java runtimes, compiles
the production bridge and creates a portable app-image plus ZIP under
`backend/launcher/build/packages/<timestamp>/`. It never includes graph data or
credentials and never deletes an existing output directory. `-SkipBuild` is for
already verified installDist outputs. Preserve `THIRD_PARTY.md`, runtime legal
notices and the package manifest with the app. The EXE is unsigned; Windows may
show its normal unknown-publisher prompt.

Official references:
- https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html
- https://developers.cloudflare.com/tunnel/reference/run-parameters/
- https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/
- https://docs.opentripplanner.org/en/latest/Configuration/

## Verification limits

See the source repository's launcher README for the checks actually run by its
builder. Windows 10 compatibility, live STM/exo feed access and public tunnel
connectivity must also be checked on the target machine; a packaged EXE alone
does not prove those external services or that specific OS work.
