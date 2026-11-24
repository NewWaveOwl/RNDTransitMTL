# One-way, round-trip and return journeys

Implements [#92](https://github.com/NewWaveOwl/RNDTransitMTL/issues/92) and
[#107](https://github.com/NewWaveOwl/RNDTransitMTL/issues/107), following the
[round trip Figma frame](https://www.figma.com/design/EBOrT92jIVwFv0mB8jeeVY/RndTransit_main?node-id=391-4353).

The preferences sheet offers **One way** (the default for existing drafts) and
**Round trip**. Regular trips choose a final destination and offer Fastest / Same
transport return routing after arrival. Round trips instead visit random exploration
waypoints and finish at their original start. Each selected box under **Make it your
kind** adds exactly one waypoint; zero selections still requests one. The frontend
sends `interestZoneCount` (1-6), independently of the intensity slider. These are
random points for now, not verified attractions.

For multiple waypoints the backend samples points around a polygon with the start
as one vertex, routes every segment in order, and returns `waypoints` and `segments`
with their accepted OTP responses. Round-trip searches use the existing generation
deadline (`20 + floor(selectedMinutes / 60)` seconds), rather than a fixed three
candidates. Bearings change and the radius cycles through progressively smaller
sizes while time remains. A single-turnaround outbound candidate may still use
more than half the trip budget, provided its return fits the combined budget.

Each candidate first routes home directly and retains that complete loop as a
fallback. It then tries an invisible return routing point beside the midpoint of
the last-stop-to-home line: a perpendicular offset of 18%, then 9%, of that line's
length, on both sides in seeded random order. Coordinates are projected into local
metres. Every detour section is routed and charged against the combined budget,
including waiting and orientation reserves; each routing operation is capped at
four seconds within the remaining search deadline. The invisible point adds no
exploration flag: its provider legs are merged into the final return segment.

Candidates are ranked by `0.65 * elapsed/budget + 0.35 * (1 - returnOverlap)`.
Overlap is the length-weighted share of return samples within 30 metres of outbound
geometry (samples at approximately 25-metre spacing). This encourages a useful
journey with less retracing; street connectivity may still require a direct return.
A later timeout, failed route, or provider failure cannot discard an accepted loop.
External cancellation still cancels the whole search. All routed
travel, transit waiting and orientation reserves share the selected time budget.
The next segment departs after the previous arrival and its orientation reserve.
Missing or over-budget segments never become partial successes. The app saves the
complete route and ordered waypoints, and flags each stop in proposals, navigation,
and reopened trips. Legacy single-turnaround records remain readable.

The backend accepts an optional signed 32-bit integer `generationSeed`. Otherwise
it creates a fresh seed per request. The seed controls candidate coordinates,
stop-anchor shuffling, and detour side order, and is returned with `attempts` and
logged with the request ID. Replaying a seed reproduces random choices, not live
transit data or the number of candidates evaluated before a wall-clock deadline.

After one-way arrival, the Figma completion sheet shows distance by transport and
estimated itinerary statistics. Walking steps assume 0.75 metres per step. Speed
and time describe the saved route; fastest GPS speed is not recorded. The arrival
celebration plays once, and the draggable sheet has keyboard and accessibility
actions, scrollable content, and resize-safe positions.

**Fastest way back** compares available Walk, Bike, Bus, Metro, and combined transit
alternatives by actual routed arrival time. Train and REM remain unavailable in
the planner. **Same transport** retains outbound modes and zone restrictions; it
does not reverse geometry or reuse a bus line in the wrong direction. Routing uses
a fresh, sufficiently accurate location reading, falling back to the completed
destination. The exact original origin remains the destination in both cases.

Finding, failure, retry, preview, and cancellation leave the completed outbound
trip and pending review intact. **Start return trip** explicitly accepts a new trip
record linked to the original. Back-at-start review opens the original adventure.
Arrival can be confirmed manually on a return, or detected by existing GPS tracking.
Round-trip completion goes directly to the back-at-start review/skip screen.

## Deployment

Deploy the updated generation backend before using round-trip planning. The client
rejects an older server's one-way response when a round trip was requested.
Apply `supabase/migrations/20261122234044_add_trip_journey_metadata.sql` before
syncing return/round-trip records. Its nullable JSONB column preserves existing
one-way rows and the existing ownership policies and protected column grants.
Legacy one-way writes omit null journey metadata. No hosted migration is applied
by the implementation or tests.

## Verification

Backend tests cover fresh fixed-target comparison, asymmetric round trips,
departure time, total budget, failed inbound routing, and cancellation. Shared
tests cover endpoint/type validation, immutable outbound records, retry/cancel
races, independent trip identity, JSON/DTO restoration, legacy draft defaults,
sample-provider loops, exact interest-box counts, ordered multi-stop restoration,
missing/reordered segments, and the initial GPS fix of a loop.
Compose integration tests exercise return failure/retry, preview/start, explicit
arrival, original-trip review, selector semantics, and completion sheet controls.
`supabase/tests/trip_journey_metadata.sql` validates legacy and linked records,
constraint failures, ownership isolation, and rollback using a local PostgreSQL runtime.

## Shared-server regression, 2026-11-23

The app checkout at `3a5ec92` already sent `destination`, `compareModes`, and
`roundTrip` correctly. The deployed TravelTracker runtime at
`7ca9005005fc74a2cdb9bf4ffedbf135db1171bc` used an older generation model that
ignored these fields. Logs showed 360-minute random searches for return requests.
The app correctly rejected the unrelated destination or one-way response.
Reinstalling Android alone cannot repair this server regression.

The restored backend combines the shared runtime's Server.kt and TravelTracker
services with this branch's complete generation sources. Do not replace it with
either branch's standalone JAR: one loses TravelTracker endpoints, the other loses
return and loop generation. Both sources are in NewWaveOwl/RNDTransitMTL.

To reproduce the combined build in PowerShell, set `$sharedRepo` to a checkout
containing the pinned runtime commit, `$appRepo` to this branch, and `$stage` to
a new, nonexistent build directory:

```powershell
git -C $sharedRepo worktree add --detach $stage 7ca9005005fc74a2cdb9bf4ffedbf135db1171bc
Copy-Item -LiteralPath (Join-Path $appRepo 'local.properties') -Destination $stage
Get-ChildItem (Join-Path $appRepo 'backend/src/main/kotlin/transit') -File |
    Where-Object Name -ne 'Server.kt' |
    ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $stage 'backend/src/main/kotlin/transit') }
Get-ChildItem (Join-Path $appRepo 'backend/src/test/kotlin/transit') -File |
    ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $stage 'backend/src/test/kotlin/transit') }
& (Join-Path $stage 'gradlew.bat') -p $stage :backend:test :backend:installDist --console=plain
```

`LiveJourneySmokeTest` is opt-in. For a live provider check, forward local port
18089 to the server's OTP bridge port 18086, set
`RND_LIVE_OTP_URL=http://127.0.0.1:18089/otp/gtfs/v1`, and run `:backend:test`.
This checks walking returns, fastest bus/metro/walking/cycling comparison, and repeated
30-minute loops with one and three interest stops using fixed public fixture
coordinates. It does not require or print user credentials or GPS positions.
Before replacing the stopped server's JAR, back it up and verify checksums.
After restart check both `/health` and `/health/traveltracker`.

The restored deployment JAR SHA-256 is
`AE3503A817B31A4C9D8608B8D57129F56C6C06BC7E28CFDAC92F444BD4D96545`.
The old JAR is retained as `backend.jar.before-journey-restore-20261124`.

Separately, Android's location filter could indefinitely reject a new stationary
location after an outage because both readings reported zero speed. Once the
anchor is older than 30 seconds, three consistent precise measurements over at
least four seconds can now confirm relocation. Fresh-anchor jitter protection,
stale/future rejection, and single-outlier rejection remain covered by tests.
This client recovery fix requires an updated Android installation.

For physical-device Android Studio runs, set `generationEndpoint` in the root
`local.properties` (beside `sdk.dir`). Android now reads this local value; a Gradle
`-PgenerationEndpoint=...` override takes precedence. With no configuration, debug
builds retain the emulator-only `10.0.2.2` default. The updated working folder has
the public server endpoint configured locally; this machine-specific setting is
not committed. Sync Gradle before running from Android Studio.
