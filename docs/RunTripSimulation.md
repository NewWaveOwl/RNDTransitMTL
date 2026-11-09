# Run the Montréal routing simulation

The Android debug samples fetch real route geometry and timetable data from a local OpenTripPlanner (OTP) stack. After the route loads, the app simulates progress along it in about 10 seconds. The regular **GO** flow uses the app's built-in sample routing and can run without the server.

## Quick start for the STM bus sample

On Windows, clone the repository and open PowerShell in its root. For Android, install the project's Android SDK and Platform-Tools in Android Studio and start one emulator. Then run:

```powershell
.\tool\otp\stm-scheduled\Run-StmTestTrip.ps1 -Emulator
```

Use `-Phone` instead for an authorized USB device. Omit the device flag to run only the backend smoke test. Set `ANDROID_HOME` for a custom Android SDK location and `JAVA_HOME` if Gradle needs a different JDK.

The first run downloads any missing Java 25, OTP 2.10.0, Montreal map data, and STM timetable, then builds the local graph. It starts OTP on `127.0.0.1:18087`, checks that the STM sample returns a bus route with geometry, and prints `PASS`. With a device flag, it then installs and opens the debug build. In the app, open **Map**, expand the planner, and tap **Load STM test trip**. The smoke test uses noon in Montreal, while the app uses the current departure time.

The quick start uses scheduled STM service and requires no API key. Its graph and logs are in `tool/otp/stm-demo`. Use `-RefreshTimetable` to download the latest STM schedule and rebuild the graph if the cached timetable expires. If port `18087` is busy, pass `-Port` with a free port.

## 1. Start the STM realtime and exo stack

Use PowerShell on the Windows PC running Android Studio. The scripts live in `tool/otp` at the repository root. A fresh checkout also needs the local runtime files. Place Java 25 under `tool/otp/java25`, the OTP 2.10.0 JAR at `tool/otp/otp.jar`, and the combined Montréal graph, GTFS feeds, and local STM configuration in `tool/otp/stm-scheduled`. The exo realtime token is needed only for live exo updates.

Open PowerShell in the `RNDTransitMTL` repository root. Use the same PowerShell window for startup and installation so `$stackDir` is available.

```powershell
$stackDir = (Resolve-Path '.\tool\otp\stm-scheduled').Path
& (Join-Path $stackDir 'Start-StmRealtimeStack.ps1')
```

Wait for the command to finish. It runs a fixture check and starts OTP on port `18085` and the Android routing bridge on port `18086` by default. Check the bridge from the PC:

```powershell
$stack = Get-Content -Raw (Join-Path $stackDir 'stm-stack-processes.json') | ConvertFrom-Json
Invoke-RestMethod "http://127.0.0.1:$($stack.adapterPort)/status"
```

## 2. Launch the Android debug build

**Emulator.** Start one Android emulator and leave the routing stack running. Run the installer in PowerShell. It reads the active bridge port, sets up `adb reverse`, and builds, installs, and opens the debug app with a `127.0.0.1` endpoint.

```powershell
& (Join-Path $stackDir 'Install-StmAndroid.ps1') -Emulator
```

**USB phone.** Enable USB debugging, connect and authorize one phone, then run this in the same PowerShell session. The script sets up `adb reverse`, installs a debug build with the phone's loopback endpoint, and opens the app.

```powershell
& (Join-Path $stackDir 'Install-StmAndroid.ps1')
```

Use the installer for subsequent builds over ADB. Rerun it if the device reconnects or the bridge port changes. The installer passes `stmOtpEndpoint` to Gradle, which puts the endpoint in the debug Android manifest.

**Direct Android Studio run.** If your emulator can reach the host through `10.0.2.2`, you can also select `androidApp` and click **Run**. That build uses the port recorded by the local stack and defaults to `18086` when no port is recorded. To use ADB forwarding after an Android Studio run, rerun the installer with `-Emulator`.

## 3. Run a sample trip

1. Open the planner on the **Map** screen and expand its sheet if needed.
2. Tap **Load STM test trip** for the bus sample from Saint-Urbain / du Mont-Royal to Saint-Urbain / Sherbrooke, or **Load STM + exo test trip** for the metro, walk, and train sample from Lionel-Groulx to Montréal-Ouest via Vendôme.
3. In Trip Preview, check the route and tap **Start Trip**. Watch the progress on the map until **Adventure complete** appears.

The samples use the current Montréal departure time. Run the mixed sample during exo service hours. If transit is unavailable, the app can try a direct walking or cycling route between the same endpoints when that mode is enabled in the planner.

## Troubleshooting

| What you see | What to check |
| --- | --- |
| **Routing service unavailable** | Check the bridge status on the PC using the command in step 1. If it responds, rerun the installer for your device to refresh the endpoint and ADB forwarding. Filter Android Studio Logcat by `tag:RndTransitOtp` to see the routing exception. |
| `SocketTimeoutException` connecting to `10.0.2.2` | Use `Install-StmAndroid.ps1 -Emulator` to reach the bridge through ADB at `127.0.0.1`. |
| Sample buttons are missing | Check that you're running the Android debug build. Finish or dismiss any active trip or pending review, then return to the planner. |
| No transit route appears | Check the current service hours and that the graph's scheduled GTFS feeds cover today's date. |

See the [routing ADR](milestones/3/Artiom/ADR-Real%20Montr%C3%A9al%20routes%20with%20simulated%20trip%20progress.md) for the graph setup and verification evidence.
