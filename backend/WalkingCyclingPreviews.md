# Chosen walking and cycling previews — M4-11 / #107

TravelTracker's existing protected **POST /v1/routes/plan** uses the chosen origin
and destination without generating a random destination or reordering stops.
Walking requests direct WALK routing; cycling requests direct BICYCLE routing.
The returned geometry, duration and distance are supplied by OTP. Cycling is not
walking geometry relabeled or sped up by the TravelTracker adapter.

The [OTP verification evidence](RoutingVerification.md) records actual walking
and cycling samples against the selected Montreal graph and version. Those
samples do not guarantee coverage elsewhere or on a changed graph.

## Optional named trip time zone

The four existing required request fields remain origin, destination, mode and
departureTime. The optional timeZone field accepts an IANA identifier such as
America/Toronto. Omitting it or sending JSON null preserves existing callers.
A blank, unknown or numeric zone is invalid. A supplied zone must allow the
timestamp's explicit UTC offset at its local date/time, including DST changes.

Nonexistent spring-forward times are rejected. Ambiguous fall-back times work
when the caller explicitly supplies either valid offset; the adapter never
chooses an offset or server-local time silently.

The response adds timeZone (the supplied named zone, or null) and
requestedDepartureTime (the canonical offset-aware requested value). OTP receives
the same instant converted to UTC. Existing candidate and leg fields are retained.

## Preview consistency and failure behavior

- Invalid endpoint fields, geographic bounds, missing timestamps, offset/zone
  mismatches and unsupported request fields are rejected before OTP I/O.
- Only expected leg modes are accepted. Geometry must decode, have the declared
  point count and connect in the selected order. The adapter retains the existing
  maximum 250-metre network snapping tolerance; it never draws a fabricated
  connector between a marker and a road.
- Distance and duration must be finite and nonnegative; direct estimates must
  be positive. Leg durations match their selected timestamp span within one
  second for provider rounding. Itinerary duration matches first-leg departure
  through final-leg arrival, including inter-leg waiting, rather than the sum
  of movement durations.
- Encoded geometry is bounded to one million characters per itinerary, with
  cancellation checks while decoding. No provider retry changes the user's
  selected endpoints or itinerary.
- A known no-route result returns no candidates. Missing/malformed routing state,
  unexpected modes, bad geometry/timing, timeouts and router outages return a
  sanitized 503 instead of an invented route or a misleading no-route success.
  Caller cancellation propagates and request capacity is released for retry.

The backend only returns previews. Catalogue/trip writes and accepting an
alternative require their normal explicit user confirmation. Failed previews
do not replace manual planning data.

## Realtime timing compatibility

The strict duration check uses estimated timestamps only when the provider marks
a transit vehicle leg realtime and supplies both estimated endpoints. A
realtime-marked vehicle leg missing either estimated endpoint is unavailable;
it is not silently relabeled scheduled. Legs without that realtime flag retain
scheduled timestamps. Street legs remain estimated, without a live road-traffic claim.

This is verified against OTP **v2.10.0** source: GraphQL LegImpl.duration delegates
to Leg.duration, which spans its actual start/end times. ScheduledTransitLeg's
LegCallTime stores those realtime times as estimates and derives scheduled
times by subtracting the respective delays. Different departure/arrival delays
can therefore change the actual duration. A deterministic fixture covers this;
no live feed predictions were used or claimed in this session.

Primary source references:
[LegImpl](https://github.com/opentripplanner/OpenTripPlanner/blob/v2.10.0/application/src/main/java/org/opentripplanner/apis/gtfs/datafetchers/LegImpl.java),
[Leg](https://github.com/opentripplanner/OpenTripPlanner/blob/v2.10.0/application/src/main/java/org/opentripplanner/model/plan/Leg.java),
[ScheduledTransitLeg](https://github.com/opentripplanner/OpenTripPlanner/blob/v2.10.0/application/src/main/java/org/opentripplanner/model/plan/leg/ScheduledTransitLeg.java),
[LegCallTime](https://github.com/opentripplanner/OpenTripPlanner/blob/v2.10.0/application/src/main/java/org/opentripplanner/model/plan/leg/LegCallTime.java).
