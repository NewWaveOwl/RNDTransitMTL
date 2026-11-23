# One-way, round-trip and return journeys

Implements [#92](https://github.com/NewWaveOwl/RNDTransitMTL/issues/92) and
[#107](https://github.com/NewWaveOwl/RNDTransitMTL/issues/107), following the
[round trip Figma frame](https://www.figma.com/design/EBOrT92jIVwFv0mB8jeeVY/RndTransit_main?node-id=391-4353).

The preferences sheet offers **One way** (the default for existing drafts) and
**Round trip**. Round-trip generation requests an outbound adventure and a fresh
route from its turnaround to the exact original start. Both directions, transit
waiting, and orientation reserves must fit the selected available time. The return
departs after the outbound arrival and its orientation reserve. A missing inbound
route never becomes a successful one-way proposal. Accepted geometry is saved once.

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
sample-provider requests in both directions, and the initial GPS fix of a loop.
Compose integration tests exercise return failure/retry, preview/start, explicit
arrival, original-trip review, selector semantics, and completion sheet controls.
`supabase/tests/trip_journey_metadata.sql` validates legacy and linked records,
constraint failures, ownership isolation, and rollback using a local PostgreSQL runtime.
