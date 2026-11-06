package com.example.rnd_transit_mtl.data.routing

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.TripRequest

/**
 * Plans the legs of a trip. Screens never call it directly: the trip
 * generator uses it, so a real provider (OSRM, GraphHopper, a Supabase
 * function) replaces FakeRoutingService without screen changes.
 *
 * Implementations receive a validated request whose destination is set.
 * Returned legs must run in travel order from request.origin.point to
 * request.destination.point exactly, as Trip requires. A provider that snaps
 * points to the street network must add short connecting legs to keep this.
 * Only legs whose geometry follows real streets or lines set isRouted.
 */
interface RoutingService {
    /**
     * Finds a route for one request.
     *
     * @param request Validated request with a destination.
     * @return Legs on success, or the specific reason no route was produced.
     */
    suspend fun route(request: TripRequest): RoutingResult
}

/** Outcome of one routing request. */
sealed class RoutingResult {
    /** @property legs Ordered legs from origin to destination; never empty. */
    data class Success(val legs: List<RouteLeg>) : RoutingResult() {
        init {
            require(legs.isNotEmpty()) { "A route needs at least one leg." }
        }
    }

    /** The provider works but found no route with the requested modes and restrictions. */
    data object NoRouteFound : RoutingResult()

    /** A point lies outside the area this provider covers. */
    data class OutsideServiceArea(val point: GeoPoint) : RoutingResult()

    /** The provider could not be reached or failed; retrying later may work. */
    data class Unavailable(val message: String) : RoutingResult()
}
