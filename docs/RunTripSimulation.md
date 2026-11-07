# Run the Montréal routing simulation

The Android debug samples fetch real route geometry and timetable data from a local OpenTripPlanner (OTP) stack. After the route loads, the app simulates progress along it in about 10 seconds. The regular **GO** flow uses the app's built-in sample routing and can run without the server.

## 1. Start the local routing stack

Use PowerShell on the Windows PC running Android Studio. The scripts live in `tool/otp` at the repository root. A fresh checkout also needs the local runtime files. Place Java 25 under `tool/otp/java25`, the OTP 2.10.0 JAR at `tool/otp/otp.jar`, and the combined Montréal graph, GTFS feeds, and local STM configuration in `tool/otp/stm-scheduled`. The exo realtime token is needed only for live exo updates.

Run this from the `RNDTransitMTL` repository root:

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

**Emulator.** Select the `androidApp` run configuration and the emulator in Android Studio, then click **Run**. The debug build reads the bridge port from the local stack state and connects to the host PC at `10.0.2.2`. It defaults to port `18086` when there is no stack state. If a restart picks a new port, click **Run** again to rebuild and reinstall the app.

**Emulator over ADB.** If the app times out connecting to `10.0.2.2`, leave one emulator running and run the following in the same PowerShell session. The installer forwards the active bridge port through ADB, installs a debug build using `127.0.0.1`, and opens the app. Use this command for subsequent builds while using ADB forwarding.

```powershell
& (Join-Path $stackDir 'Install-StmAndroid.ps1') -Emulator
```

**USB phone.** Enable USB debugging, connect and authorize one phone, then run this in the same PowerShell session. The script sets up `adb reverse`, installs a debug build with the phone's loopback endpoint, and opens the app.

```powershell
& (Join-Path $stackDir 'Install-StmAndroid.ps1')
```

## 3. Run a sample trip

1. Open the planner on the **Map** screen and expand its sheet if needed.
2. Tap **Load STM test trip** for the bus sample from Saint-Urbain / du Mont-Royal to Saint-Urbain / Sherbrooke, or **Load STM + exo test trip** for the metro, walk, and train sample from Lionel-Groulx to Montréal-Ouest via Vendôme.
3. In Trip Preview, check the route and tap **Start Trip**. Watch the progress on the map until **Adventure complete** appears.

The samples use the current Montréal departure time. Run the mixed sample during exo service hours. If transit is unavailable, the app can try a direct walking or cycling route between the same endpoints when that mode is enabled in the planner.

## Troubleshooting

| What you see | What to check |
| --- | --- |
| **Routing service unavailable** | Check the bridge status on the PC. On the emulator, open `http://10.0.2.2:PORT/status` in its browser, replacing `PORT` with the bridge port printed by the script. If the browser gets JSON but the app still fails after a port change, rebuild and reinstall the debug app. For the exception behind a failed routing request, filter Android Studio Logcat by `tag:RndTransitOtp`. On a USB phone, rerun the install script after reconnecting to restore `adb reverse`. |
| Manually selecting an emulator endpoint | From the repository root, run `.\gradlew.bat :androidApp:installDebug "-PstmOtpEndpoint=http://10.0.2.2:PORT/otp/gtfs/v1"`, replacing `PORT`. This property overrides the port from the stack state for that build. |
| Sample buttons are missing | Check that you're running the Android debug build. Finish or dismiss any active trip or pending review, then return to the planner. |
| No transit route appears | Check the current service hours and that the graph's scheduled GTFS feeds cover today's date. |

See the [routing ADR](milestones/3/Artiom/ADR-Real%20Montr%C3%A9al%20routes%20with%20simulated%20trip%20progress.md) for the graph setup and verification evidence.
