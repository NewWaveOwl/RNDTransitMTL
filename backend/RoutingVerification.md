# Verified OTP setup and mode coverage — M4-10 / #106

This integration uses **OTP 2.10.0**, serialization version **277**, with **Java 25**.
The inspected binary reports commit 521cd5d8acaae628878f43bf5946f4942c118548.
The local runtime used for verification was Microsoft OpenJDK 25.0.5.0.2.
The Kotlin backend and Windows launcher continue to use Java 21.

OTP and its graph remain private. TravelTracker receives public HTTP metadata and
route previews through the shared authenticated backend; provider models do not
enter TravelTracker's pure Domain projects. Routing never saves a trip.

## Reproduce a separate scheduled-only graph

Obtain permitted regional OpenStreetMap and STM GTFS inputs; add exo train GTFS
only when train coverage is wanted. No script downloads a world dataset, requests
feed credentials, or modifies an existing server. Retain OpenStreetMap attribution
and the transit data publishers' licenses.

From the RNDTransitMTL repository root:

~~~powershell
$graphArgs = @{
  Java = 'C:\Java25\bin\java.exe'
  OtpJar = 'C:\OTP\otp-shaded-2.10.0-shaded.jar'
  OsmFile = 'D:\public-data\Montreal.osm.pbf'
  StmGtfs = 'D:\public-data\stm-gtfs.zip'
  ExoGtfs = 'D:\public-data\exo-trains-gtfs.zip'
  Destination = 'D:\OTP\traveltracker-new-graph'
  MemoryMb = 4096
}
.\backend\scripts\Prepare-TravelTrackerOtp.ps1 @graphArgs
~~~

The destination must be new. The script copies the selected public inputs, writes
an America/Toronto build configuration with STM and optional EXO_TRAINS feed IDs,
then runs OTP's **--build --save**. It writes a manifest tying the copied inputs
and router JAR hashes to the resulting graph hash. Its router configuration has
no realtime updaters or private credentials. Memory is a configurable heap limit,
not a promise that the process fits every machine.

A complete graph rebuild was **not rerun in this session**. The session loaded
the existing Montreal graph into an isolated router and verified its actual
metadata and journeys. Neighboring OSM/GTFS file hashes are useful available input
references; without the original build manifest they do not prove those files
were the exact inputs used to build the existing graph.

## Reproduce actual route samples

~~~powershell
$checkArgs = @{
  Java = 'C:\Java25\bin\java.exe'
  OtpJar = 'C:\OTP\otp-shaded-2.10.0-shaded.jar'
  DataDirectory = 'D:\OTP\traveltracker-new-graph'
  OsmFile = 'D:\public-data\Montreal.osm.pbf'
  DepartureTime = '2026-11-23T08:00:00-05:00'
  Destination = 'D:\OTP\evidence-new'
}
.\backend\scripts\Test-TravelTrackerRouting.ps1 @checkArgs
~~~

The default starts one temporary loopback-only OTP on port 21885 with a separate
graph copy/hard link and empty updater configuration, and stops only its own
process in finally. It does not start the RNDTransit launcher, realtime feeds,
Nominatim or a tunnel. A busy port fails without killing its owner. Choose
another port if necessary. The observed 281 MB serialized Montreal graph loaded
with a 1536 MiB heap; that is a measurement for this graph, not a general minimum.

The default samples use Montreal coordinates. **-CasesFile** can supply an array
of unique modes with origin/destination objects containing lat and lon for another
region. Each supported mode may occur once, preserving every request/response
artifact. **-ExistingOtpUrl** may query an explicitly selected loopback router
without starting or stopping it; its supplied graph/JAR references are marked
**unverified as loaded**, because a URL does not establish process/graph identity.

Evidence contains the actual router version/time zone, feed IDs/modes, global
service range, request bodies, original OTP responses, sample durations/distances,
actual leg modes and file SHA-256 values. A schema enum alone is not a successful
route. Empty transit feeds still allow direct-mode checks; missing transit modes
are explicit unsupported results. Unknown GraphQL/routing errors fail verification.

## Observed local samples on 2026-11-22

Scheduled-only departure: **2026-11-23 08:00 America/Toronto**
(2026-11-23T13:00:00Z). Public Montreal sample coordinates:

| Requested mode | Endpoints | First actual itinerary | Seconds |
| --- | --- | --- | ---: |
| Walking | 45.5088,-73.5686 → 45.5312,-73.6150 | WALK | 3742 |
| Cycling | Same chosen endpoints | BICYCLE | 1258 |
| Car | Same chosen endpoints | CAR | 513 |
| Bus | Same chosen endpoints | WALK → BUS → WALK | 1849 |
| Train | 45.4735,-73.6035 → 45.4953,-73.5705 | WALK → RAIL → WALK | 530 |

All five responses had nonempty OTP leg geometry and no GraphQL or routing errors.
Car used OTP's actual direct CAR mode; no road-path approximation or alternate
router was substituted. Cycling used BICYCLE routing, independently of the older
Android random-trip generator's simplified cycling behavior.

The running graph reported STM bus/subway routes and five EXO_TRAINS rail routes.
Its global service range was **2026-08-24T04:00:00Z inclusive** through
**2027-01-04T05:00:00Z exclusive**. With the router's America/Toronto time zone,
the displayed inclusive final date is **2027-01-03**. This union does not prove
every feed, route or weekday has service throughout that interval. These samples
prove only the recorded endpoints, departure and graph; outside-region paths,
another date, another mode or a changed graph require their own query.

## Public capability contract

**GET /v1/routes/capabilities** requires the same verified TravelTracker session
as route planning and accepts no query parameters. It returns:

- router: name and reported version; unknown version is null.
- coverage: reported timeZone, inclusive startDate/endDate, raw serviceStart and
  serviceEndExclusive, serviceRangeBasis=global_union, feeds and nullable bounds.
- feeds: id, declared modes, publisher/version and null per-feed startDate/endDate,
  because this OTP metadata does not expose individual feed calendar coverage.
- modes: code, status (available/unsupported/unknown), basis and optional reason.
  Direct modes use router_schema; transit uses loaded_routes. Neither guarantees
  a specific journey. Missing metadata stays unknown.
- attribution and an explanation of these limits.

Metadata is cached across users for 30 seconds. Optional Transmodel server info
has a one-second timeout; its failure leaves version/time zone unknown rather than
hiding valid GTFS metadata. Required GTFS metadata failure is a sanitized 503.
Cancellation propagates. Responses are no-store; addresses and tokens are absent.

Route-preview enrichment and per-request failure reasons are separate #107/#108
slices. Manual plans remain available when OTP is unavailable. Existing estimates
never become saved catalogue or trip data automatically.

References: [OTP releases](https://github.com/opentripplanner/OpenTripPlanner/releases/tag/v2.10.0),
[getting OTP](https://docs.opentripplanner.org/en/latest/Getting-OTP/),
[routing modes](https://docs.opentripplanner.org/en/latest/RoutingModes/),
[router configuration](https://docs.opentripplanner.org/en/latest/RouterConfiguration/).
