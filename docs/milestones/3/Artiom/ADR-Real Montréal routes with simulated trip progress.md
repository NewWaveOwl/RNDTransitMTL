# ADR - Real Montréal routes with simulated trip progress

**Status** — Accepted. Implementation complete for the local Android debug prototype.

**Verification date** — November 7, 2026, Montréal time.

**Related issue** — [#21 - Test Montréal routing and walking/cycling alternatives](https://github.com/NewWaveOwl/RNDTransitMTL/issues/21)

## Context

`FakeRoutingService` supplies straight-line geometry and a synthetic duration for the regular planner. Issue #21 calls for testing real Montréal routes and checking whether the app can use them in its existing trip flow. Fixed test journeys make the provider response, route mapping, and trip completion repeatable.

## Decision

Use OpenTripPlanner (OTP) 2.10.0 on Java 25. Build one local graph from `Montreal.osm.pbf`, scheduled STM GTFS under `STM`, and scheduled exo train GTFS under `EXO_TRAINS`. Use `America/Toronto` for the graph and Android departure queries. Chrono uses `TRAINS` as the exo realtime agency code. OTP applies those updates to `EXO_TRAINS`. [OTP setup guide](https://docs.opentripplanner.org/en/v2.10.0/Basic-Tutorial/) · [OTP GTFS Realtime configuration](https://docs.opentripplanner.org/en/v2.10.0/GTFS-RT-Config/)

| Component | Responsibility |
| --- | --- |
| OTP | Return ordered legs, modes, line IDs, geometry, distances, and scheduled or estimated times through `planConnection`. |
| Local realtime bridge | Fetch and validate agency feeds, track predictions and alerts separately for each feed, and expose freshness to Android. Keep credentials in server-local configuration. |
| Android debug loader | Request a fixed itinerary through `OtpRoutingService` at `POST /otp/gtfs/v1`, decode geometry, and create shared `Trip` and `RouteLeg` values. |
| Trip Preview and Active Trip | Reuse the accepted route and `ActiveTripSimulation`. Preview shows provider travel time and distance, including transfer time for the mixed itinerary. Playback uses the existing 10-second clock. |

The debug loader maps `BUS`, `SUBWAY`, `WALK`, and `RAIL` for the tested journeys. It uses the first and last geometry points as the trip endpoints, joins short platform offsets through the walking leg, and rejects missing or disconnected geometry. The debug actions load an STM bus or an STM-to-exo itinerary. The regular **GO** flow uses `FakeRoutingService`. [OTP planning query](https://docs.opentripplanner.org/api/dev-2.x/graphql-gtfs/queries/planConnection)

The bridge polls STM every 60 seconds and exo every 120 seconds. The exo portal showed no numeric quota during setup. A later HTTP 429 triggered backoff, and the feeds recovered. A stale alert feed hides its alerts independently of prediction freshness. If an itinerary requires stale or unavailable prediction data, the bridge falls back to the timetable. Preview retains scheduled times and displays estimates when OTP applies them. [STM open data](https://www.stm.info/a-propos/documents-donnees/donnees-ouvertes) · [exo open data](https://exo.quebec/fr/a-propos/donnees-ouvertes) · [Chrono SAEIV](https://portail-developpeur.chrono-saeiv.com/apis)

## Verification results

Scheduled times below are local to Montréal. Distances are from OTP.

| Check | Observed result |
| --- | --- |
| Old Montréal walking and cycling | OTP returned separate `WALK` and `BICYCLE` routes from Notre-Dame Basilica to Bonsecours Market. Walking was 750.43 m in 9 min 54 sec, with 72 geometry points. Cycling was 1,066.52 m in 5 min 48 sec, with 42 points. Both shapes were decoded and saved as GeoJSON. |
| Scheduled STM metro | On November 6, Orange Line 2 departed Lionel-Groulx at 10:02 and arrived at Berri-UQAM at 10:11. OTP returned 4,265 m, 13 geometry points, and zero itinerary wait time. The original Android debug loader targeted this fixed trip. |
| STM realtime | A live southbound bus 55 alert appeared in Trip Preview on a physical Android phone. The northbound control had no matching alert. Stale data and a controlled outage switched the preview to scheduled times and hid the alert. The alert returned after recovery, and OTP applied a fresh zero-delay prediction on bus 29. |
| Scheduled exo train | On November 7, line 11 Vaudreuil–Hudson departed Vendôme at 09:35 and arrived at Montréal-Ouest at 09:40:15. OTP returned 3,750 m and 30 geometry points. |
| STM-to-exo transfer and Android playback | Orange Line 2 ran from Lionel-Groulx at 09:20 to Vendôme at 09:23. The walk reached the exo platform at 09:28:30, leaving 6 min 30 sec before the line 11 departure. The Pixel 9 emulator reached **Adventure complete** about 12 seconds after tapping **Start Trip**, including the screen transition. |
| exo realtime | Both Chrono endpoints authenticated. OTP imported 22 live alerts and attached the Saint-Jérôme line 12 speed restriction to an affected itinerary. A controlled outage returned HTTP 503 for both exo feeds, routing fell back to the timetable, and both feeds recovered. OTP applied a simulated two-minute prediction, and the fixture verified stale-alert isolation and prediction recovery. |

The Android debug build and shared JVM tests passed. The adapter fixtures also passed identifier matching, independent feed freshness, and scheduled fallback checks. [Physical-phone STM alert screenshot](https://chatgpt.com/library?file_id=libfile_d10d942af92481918bd3914ef298ebc3&account_id=personal)

The exo prediction integration is complete for the prototype. Chrono documents estimated stop arrivals in its TripUpdate feed. The authenticated feed was reachable, and the fixture confirmed that OTP can apply a prediction to a matching train trip. The sampled live feed contained no stop-time estimates, so applying a live prediction remains a field check. [ARTM feed catalogue](https://donnees.artm.quebec/catalogue/dataset/offre-de-service-en-temps-reel-de-transport-en-commun-gtfs-realtime-des-trains-et-a) · [OTP GTFS Realtime configuration](https://docs.opentripplanner.org/en/v2.10.0/GTFS-RT-Config/)

## Local setup and evidence

The graph, bridge scripts, and sanitized evidence live in `tools/otp-montreal` under the App_Dev workspace. Start `stm-scheduled/Start-StmRealtimeStack.ps1` with the local feeds and credentials configured. The verified setup used OTP on `127.0.0.1:18085` and the bridge on port `18086`. The emulator defaults to `http://10.0.2.2:18086/otp/gtfs/v1`. For a USB phone, `stm-scheduled/Install-StmAndroid.ps1` sets up `adb reverse` and installs the debug build with a loopback endpoint. Then use **Load STM test trip** or **Load STM + exo test trip**.

| Evidence | Files relative to `tools/otp-montreal/` |
| --- | --- |
| Walking and cycling | `combined-query.graphql`, `combined-response.json`, `old-montreal-routes.geojson` |
| Scheduled STM | `stm-scheduled/stm-metro-query.graphql`, `stm-scheduled/stm-metro-response.json`, `stm-scheduled/stm-feed-source.json` |
| STM realtime and phone checks | `stm-scheduled/realtime-live-verification-20261107T052830Z/test-summary.json` |
| exo feed version and service calendar | `stm-scheduled/exo-trains-feed-source.json`, `stm-scheduled/exo-trains-calendar-evidence.json` |
| Scheduled exo, mixed trip, realtime, and emulator checks | `stm-scheduled/exo-scheduled-verification-20261107/verification-summary.json`, with queries, responses, GeoJSON, and test logs in the same directory |

## Remaining acceptance checks and scope

- During exo service, confirm OTP applies a live Chrono stop-time estimate to a matching train trip and compare it with the scheduled itinerary. A live zero-delay estimate satisfies this check.
- Record a device test of marker position along the route, pause and resume, and final arrival. The emulator completion test covered the screen flow and completion timing.
- Verify invalid input, no itinerary, and provider timeout in the app, including preservation of planner selections. Walking and cycling routes were verified in OTP. Connecting those alternatives to the planner's fallback flow remains open.

This ADR records the local prototype. Random destination selection within the requested time and transport modes remains tracked in [#38](https://github.com/NewWaveOwl/RNDTransitMTL/issues/38). The exo integration covers trains. Bus feeds would require a separate access and routing check.
