# Car and scheduled transit previews — M4-12 / #108

The shared TravelTracker adapter returns real OTP car, bus and rail candidates
for chosen endpoints and the requested offset-aware trip departure. It never
substitutes a straight line, a walking route labeled car, or metro labeled train.
The [verified OTP setup](RoutingVerification.md) documents the actual selected
Montreal graph, supported Java/OTP versions and reproducible graph build.

The new TravelTracker-only query is
**backend/src/main/resources/traveltracker-plan.graphql**. Android's existing
random-trip planning query and its behavior are unchanged.

## Coverage and explicit failure reasons

The response adds **coverage**, containing the same live metadata as the protected
capabilities endpoint, or null when optional metadata is unavailable. Metadata
is bounded to 1.5 seconds during a route request; a broken optional catalogue
does not prevent a working road query. Unknown mode support is not falsely
reported as unsupported.

When the live schema explicitly lacks CAR, the adapter returns unsupported with
reason **unsupported_mode**, without asking another router or fabricating a
path. Verified OTP 2.10.0 supports CAR, and actual selected-graph car samples were
observed. Specific endpoints still require a successful road query.

When loaded route metadata explicitly lacks RAIL, train preview is unsupported
with reason **mode_not_in_dataset**. Bus and subway routes do not establish rail
coverage. A present rail route likewise does not guarantee a train on a selected
weekday or at a selected time.

Known empty-routing responses have a public reason:

| Reason | Meaning |
| --- | --- |
| outside_coverage | OTP returned OUTSIDE_BOUNDS for an endpoint outside its loaded street graph. |
| outside_service_period | The router reported the requested service period unavailable. |
| location_not_found | The router could not match a selected location. |
| no_stops_in_range | No suitable transit stops were in the allowed access range. |
| no_route | No candidate matched the requested endpoints, mode and time, including NO_DIRECT_MODE_CONNECTION for disconnected street routing. |

Success has reason null. Unexpected GraphQL/routing errors, malformed geometry,
impossible stops/times, timeouts and provider outages remain sanitized HTTP 503.
Invalid caller fields remain HTTP 400. Caller cancellation propagates. These
outcomes do not write or replace a saved trip.

### Montreal to Trois-Rivieres coverage correction

On 2026-11-23, an isolated copy of the existing Montreal graph returned
`OUTSIDE_BOUNDS` for the destination of a CAR request from 45.5088,-73.5686 to
46.3432,-72.5477 in 195 ms. The adapter had omitted this normal OTP 2.10 routing
code from its no-route allowlist and incorrectly turned it into HTTP 503.
It now returns HTTP 200 with `status: no_route`, `reason: outside_coverage` and
an empty itinerary list. The live routing-error enum also confirmed
`NO_DIRECT_MODE_CONNECTION`, which now remains a normal no-route result. Unknown
errors and real provider failures still return 503; no timeout was increased.

The correction makes the cause visible; it cannot add missing roads. To generate
this journey, the server needs a graph built from OSM data covering Montreal,
Trois-Rivieres and the connecting roads. Use a wider regional extract with
[Prepare-TravelTrackerOtp.ps1](scripts/Prepare-TravelTrackerOtp.ps1) in a new
directory, retaining the existing STM/EXO GTFS feeds. Validate the resulting graph
with a CAR case for the actual endpoints, then follow the stopped-server
backup/import procedure in [SERVER_GUIDE.md](launcher/SERVER_GUIDE.md). Replacing
application JARs alone preserves the old graph and cannot extend its coverage.

The original graph and remote server were not changed for this reproduction.
Raw request/response and private OTP logs are retained in the ignored
`backend/build/routing-evidence/long-car-20261123/` directory. Five added
deterministic regression cases cover both out-of-bounds endpoints, disconnected
car routing, unknown-error preservation and the authenticated HTTP 200/503
distinction. Existing cancellation and request-capacity behavior is unchanged.
The final `gradlew.bat -p backend test installDist :launcher:installDist --console=plain`
gate passed all 137 backend and 60 launcher tests, with zero
failures, errors or skips and no compiler warnings. The five additions raise the
backend baseline from 132; four failed against the old mapper before the fix.

The global loaded service-date union is reported with inclusive calendar dates
and raw exclusive timestamp bounds. It is **not** used as a blanket road validity
period or a per-feed/weekday guarantee. Unavailable dates are classified from the
actual router response, preserving legitimate cross-midnight journeys.

## Candidate and leg details

Each itinerary retains id, durationSeconds, distanceMeters, timingBasis and legs,
and adds:

- departureTime and arrivalTime: first actual leg departure and final actual
  arrival, using the same scheduled/realtime selection as the legs.
- transferCount: the provider's validated numberOfTransfers; legacy responses
  without it use an interline-aware count of vehicle boardings.
- initialWaitSeconds: request time through first actual departure.
- elapsedFromRequestedDepartureSeconds: request time through final arrival.

The established durationSeconds means first actual departure through arrival,
including inter-leg waiting. It excludes initial waiting. A UI retaining the
user's requested departure should use elapsedFromRequestedDepartureSeconds when
offering total planning minutes. Any application of estimates still requires
explicit user confirmation.

Each leg retains mode, geometry, distance/duration, effective departure/arrival,
routeName and realtime, and adds:

- from/to: name, latitude/longitude as lat/lon and optional public GTFS stopId.
  Provider place coordinates are retained; old responses without coordinates
  may use the verified route geometry endpoints. Selected itinerary points are
  never moved or saved by this mapping.
- intermediateStops: the same normalized location shape in provider order;
  null means not supplied, distinct from an explicitly empty list.
- routeId and headsign: optional reported transit identifiers/direction.
- timingBasis: estimated for street/walking legs, scheduled for timetable
  vehicles, realtime only when both estimated vehicle endpoints were reported.
- scheduledDepartureTime/scheduledArrivalTime: the original timetable timestamps,
  retained beside the effective values even when predictions are used.

Walking access, egress and transfer legs remain explicit. A realtime-marked
vehicle leg with a missing estimated endpoint is rejected; it is not silently
shown as scheduled. No live road-traffic support is claimed.

Stops require finite geographic coordinates and must agree with a leg's verified
geometry endpoints within the existing network snapping tolerance. Intermediate
metadata is bounded to 1000 stops per leg and 2000 per itinerary. Invalid provider
metadata is a dependency failure rather than an error blamed on valid user input.

## Real data and deterministic checks

The revised TT query was exercised in a new isolated OTP 2.10.0 run against the
existing graph on 2026-11-22. Walking, cycling, CAR, BUS and RAIL samples all
returned real geometry. Transit endpoints had actual named public stop IDs.
Bus alternatives reported 17 or 15 intermediate stops; the selected rail
alternative reported an explicitly empty list. The samples had zero transfers;
deterministic fixtures separately cover multi-vehicle and interlined journeys.

To capture new raw evidence, the verification script now defaults to the TT
resource. **-QueryFile** can select another explicit query; historical #106
artifacts remain tied to their original query rather than being overwritten.
The default isolated run stops only its own process and uses no realtime feed
credentials, public geocoder or target-server installation.

An opt-in offline replay checks the current adapter against all captured real
alternatives:

~~~powershell
.\gradlew.bat -p backend verifyRoutingEvidence "-ProutingEvidenceDirectory=D:\OTP\evidence-new"
~~~

This checks the selected points, modes, departure and current query, then
normalizes the recorded responses through the production planner. Captures are
scheduled-only; replay proves that path, not live prediction availability. The
task is separate from ordinary deterministic CI tests and does not silently skip
missing local evidence. A changed query requires fresh captured responses.

Live authenticated Dev Tunnel requests, external provider availability and the
actual remote Windows server remain separate checks. See
[Windows update and configuration](launcher/SERVER_GUIDE.md) for the same shared
API base URL, preserving existing graph/settings and applying a reviewed update.
