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

## Realtime feeds and credentials

The main window's **Realtime feeds** panel reports STM and exo trains separately.
Predictions and alerts have independent status and last-success times; one may
work while the other is unavailable. **Bridge: Ready** only means the local
bridge responds. It does not confirm upstream API access or freshness.

- **Live**: the bridge has fresh, accepted data for that feed.
- **Stale** / **Unavailable** / **Auth failed**: predictions fall back to scheduled
  routing. A failed alerts feed does not by itself disable fresh predictions.
- **Waiting**: no first successful download yet. **Not configured**: optional exo
  realtime has no saved configuration. **Stopped**: the launcher is not running
  the bridge. **Bridge unavailable**: its status could not be read.
- **Last predictions** / **Last alerts** show the bridge's last successful
  download in the server PC's local time, never the current status-check time.
  Failure retains the previous successful time; **Never** means none is known in
  this run. Restarting the bridge resets its in-memory download history.
- **Credentials: Saved** means a key is stored, not that it is valid. Watch the
  actual prediction/alert statuses after starting.

The launcher checks the private bridge status periodically (about every five
seconds), including while OTP loads. **Refresh** rechecks that status; it does not
make additional STM/exo requests. No private bridge/status endpoint is published.

To replace credentials, **Stop server**, click **Feed credentials...**, enter a
replacement STM API key and/or exo train token, and **Save**. Blank fields keep
existing keys. You may also change exo's polling interval (30-3600 seconds).
Then press **Start server** to load and verify the new values through normal feed
polling. Key fields are masked and existing secrets are never prefilled.

Settings update only `stm-upstream.json` and `exo-realtime.local.json` inside the
imported data copy. They do not modify the original stack, server.properties or
the generated private router endpoints. Import the matching exo train GTFS before
adding its token. Keys stay local and are excluded from packages/Git.

The Chrono portal's `agency` parameter supports selecting API feeds, but this
build still connects only STM and exo trains. exo bus sectors require their own
schedule/graph integration; entering a token here does not enable them.

## General settings

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
| tunnelMode | off, quick, named or microsoft |
| cloudflared | Absolute path to separately installed cloudflared.exe |
| devtunnel | Absolute path to separately installed devtunnel.exe; blank searches PATH and winget links |
| devTunnelId | Persistent Microsoft tunnel ID; defaults to rndtransit; regional IDs such as rndtransit.use work |
| tunnelTokenFile | Path to a private named-tunnel token file, never the token itself |
| publicAddress | Named tunnel's HTTPS URL |
| supabaseUrl | Your project's HTTPS URL |
| supabaseKey | Its publishable key, never a service-role key |

Leave enough RAM for Windows and the other services beyond OTP's configured heap.
There are no automatic large downloads or graph rebuilds in the launcher. Prepare
and refresh schedules/graph/index using the existing backend/otp tools in the
development checkout, then import a new copy. An expired schedule needs a refreshed
graph. Missing credentials, runtimes, data and occupied ports produce setup errors.

## Microsoft Dev Tunnels (Azure)

This shares the server running on your PC; it does not deploy OTP/Ktor to an Azure
VM or consume an Azure VM allocation. Install Microsoft's CLI once from
https://learn.microsoft.com/en-us/azure/developer/dev-tunnels/get-started
(`winget install Microsoft.devtunnel`). The CLI is not bundled with this EXE.

1. Stop the server and close any manually running `devtunnel host` (Ctrl+C in its
   PowerShell window). The launcher must be the only host for this tunnel.
2. Open **Tunnel settings**, select **Microsoft Dev Tunnels (Azure)** and keep
   the same Supabase project URL and publishable key used by Android. Leave
   **Microsoft CLI** blank for automatic detection, or Browse to devtunnel.exe.
3. Already created your tunnel in PowerShell? Enter its ID, such as
   `rndtransit.use`, and **Save**. Existing CLI sign-in is reused for the same
   Windows user. Do not click Create for an existing tunnel.
4. For a new account/tunnel, click **Microsoft sign-in**, complete the browser
   sign-in, reopen Tunnel settings, then click **Create new Microsoft tunnel**.
   This creates a persistent tunnel with public relay access and adds only the
   configured API port. The backend still requires a valid app session token.
   Setup status and CLI output appear in the main launcher. Stop server cancels
   setup. If creation succeeds but port setup fails, add the port once with
   `devtunnel port create YOUR-ID -p 18088 --protocol http` rather than repeating
   Create. Replace 18088 if you changed apiPort.
5. Press **Start server**. After Ktor is ready, the launcher hosts the saved
   tunnel, extracts its HTTPS API address and checks public `/health`. It shows
   **Tunnel: Connected - Microsoft Dev Tunnels** only when that check returns
   the backend's ready response. Copy **Public HTTPS** for the Android endpoint.
   Sign-in redirects, inspection URLs and HTML warning pages are not readiness.

Stop, Restart and Exit manage the launcher-owned tunnel with the other services;
no separate PowerShell window is needed. A lost public connection is shown as
Reconnecting while the CLI attempts to restore it. If the CLI exits, the normal
managed-service failure handling stops the stack; check logs and sign in again
through Tunnel settings if the account session expired.

The equivalent saved settings are:

```properties
tunnelMode=microsoft
devtunnel=
devTunnelId=rndtransit.use
```

The launcher discovers the public address automatically; leave publicAddress
blank (it is only used by Cloudflare named mode). Old off/quick/named configs
continue to work. A reused Microsoft tunnel normally keeps its URL; unused
tunnels expire after 30 days without activity by default. Dev Tunnels is a
development/testing preview, without a production SLA. For a reused tunnel,
review its configured ports and allow only the API port, never OTP or the bridge.
Changing apiPort also requires updating the persistent tunnel's port configuration.
Account tokens remain in the CLI's OS credential store, outside server.properties.

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
- https://learn.microsoft.com/en-us/azure/developer/dev-tunnels/cli-commands
- https://learn.microsoft.com/en-us/azure/developer/dev-tunnels/faq
- https://docs.opentripplanner.org/en/latest/Configuration/

## Verification limits

See the source repository's launcher README for the checks actually run by its
builder. Windows 10 compatibility, live STM/exo feed access and public tunnel
connectivity must also be checked on the target machine; a packaged EXE alone
does not prove those external services or that specific OS work.
