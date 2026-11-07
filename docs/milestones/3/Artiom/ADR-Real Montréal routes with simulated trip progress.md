# ADR — Real Montréal routes with simulated trip progress

**Status** — Proposed  
**Issue** — [#21 — Test Montréal routing and walking/cycling alternatives](https://github.com/NewWaveOwl/RNDTransitMTL/issues/21)

## Context

The Android app uses `FakeRoutingService` to return straight-line geometry and a synthetic duration. Issue #21 needs repeatable evidence that Montréal transit and walking or cycling routes can be calculated and mapped to the shared routing contract. The first Android test uses a fixed origin and destination so we can verify the route from request through trip completion.

## Decision

Run a local OpenTripPlanner (OTP) 2 server. OTP builds the routing graph from OpenStreetMap and scheduled STM and exo GTFS feeds. The Android app calls OTP through the existing `RoutingService`. Add agency real-time feeds on the server after scheduled routing works, and keep the credentials in local server configuration. [OTP setup guide](https://docs.opentripplanner.org/en/v2.10.0/Basic-Tutorial/) · [OTP GTFS Realtime configuration](https://docs.opentripplanner.org/en/v2.10.0/GTFS-RT-Config/)

| Component | Responsibility |
|---|---|
| OpenTripPlanner | Calculate itineraries with ordered legs, route geometry, distances, and travel times. |
| STM and exo | Provide scheduled service through static GTFS and eligible delays, cancellations, and alerts through GTFS Realtime. |
| RNDTransitMTL | Request an itinerary, render the same saved route in Preview and Active Trip, and animate the marker along its geometry. |

Reuse `ActiveTripSimulation` and its existing 10-second clock. The preview displays the provider's itinerary duration and distance. Starting the trip plays the route geometry at accelerated speed, and pausing and resuming use the existing trip state.

## Integration order

1. **Start OTP with OpenStreetMap.** Pin an OTP 2 release and load a Montréal-area OSM extract. Run one walking query and one cycling query in GraphiQL at `/graphiql`. Save the requests and responses and verify that the returned paths follow the network. These are the first routed fallback candidates. [OTP setup guide](https://docs.opentripplanner.org/en/v2.10.0/Basic-Tutorial/) · [GraphQL API](https://docs.opentripplanner.org/api/dev-2.x/graphql-gtfs/)
2. **Add STM scheduled service.** Load the STM static GTFS and query Lionel-Groulx → Berri-UQAM using station IDs and a fixed departure time within the feed's service dates. Record the line, departure and arrival times, wait time, and route geometry. [STM open data](https://www.stm.info/a-propos/documents-donnees/donnees-ouvertes)
3. **Run the STM trip in Android.** Add a debug-only entry for the fixed trip. Implement `OtpRoutingService` with `POST /otp/gtfs/v1` and use `planConnection` from the pinned server's schema. Decode the geometry into ordered `RouteLeg` values with mode, line, distance, and duration. Preserve wait and transfer timing in the itinerary details. Use the returned endpoints and connecting legs to satisfy the shared route contract. Save the accepted itinerary and render it in both screens. [OTP planning query](https://docs.opentripplanner.org/api/dev-2.x/graphql-gtfs/queries/planConnection) · [OTP leg schema](https://docs.opentripplanner.org/api/dev-2.x/graphql-gtfs/types/Leg)
4. **Connect STM Realtime.** Configure the server-side updater with the feed that matches the static GTFS. Verify a bus trip update or a supported service alert. Keep scheduled routing available during an updater outage and identify whether the itinerary uses scheduled or real-time data. [STM Developer Portal](https://portail.developpeurs.stm.info/apihub/#/) · [STM open data](https://www.stm.info/a-propos/documents-donnees/donnees-ouvertes)
5. **Add exo scheduled service, then exo Realtime.** Load the exo train GTFS into the same graph with a separate feed ID. Test one train itinerary, then one itinerary with STM and exo legs. After those pass, connect the train update feed through Chrono SAEIV. Confirm which feeds the account can access before adding exo bus updates. [exo open data](https://exo.quebec/en/about/Open-data) · [Chrono SAEIV Developer Portal](https://portail-developpeur.chrono-saeiv.com/apis) · [ARTM feed catalogue](https://donnees.artm.quebec/catalogue/dataset/offre-de-service-en-temps-reel-de-transport-en-commun-gtfs-realtime-des-trains-et-a)

## Acceptance checks for issue #21

- Save the OTP version, static feed versions or download dates, query variables, departure time and timezone, and sanitized responses. Include one transit result, one walking result, and one cycling result. If an endpoint is blocked, record the exact query, failure, and provider decision.
- Check ordered legs, line and mode mapping, route shape, distance, and travel, wait, and transfer times. Report missing geometry as an error. Mark a leg as routed only when it has provider geometry.
- On Android, verify Preview → Start → Complete using the same saved route. Check that the marker starts at the origin, follows the bends, pauses and resumes, and reaches the destination in the 10-second demo. Verify that the itinerary duration shown in Preview stays accurate.
- Test invalid input, no itinerary, and provider timeout. Preserve the user's selections on failure. Offer a routed walking or cycling fallback only when that mode is enabled.

## Scope

The first milestone is the scheduled STM metro trip working end to end on Android. This test can run before real-time credentials are available. Random destination selection that fits the requested time and modes is tracked in [#38](https://github.com/NewWaveOwl/RNDTransitMTL/issues/38). Trip playback uses simulated progress.
