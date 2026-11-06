package com.example.rnd_transit_mtl.data.fake

import com.example.rnd_transit_mtl.data.assets.SampleMapBounds
import com.example.rnd_transit_mtl.data.routing.RoutingResult
import com.example.rnd_transit_mtl.data.routing.RoutingService
import com.example.rnd_transit_mtl.model.geo.GeoBounds
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.TripRequest

/**
 * Routes without a network: one straight, unrouted leg from origin to
 * destination using the first requested mode, lasting the planned minutes.
 *
 * Points outside [serviceArea] are rejected, like a real provider limited
 * to its region. By default that is the area of the bundled sample map.
 *
 * @param serviceArea Area this fake accepts.
 */
class FakeRoutingService(
    private val serviceArea: GeoBounds = SampleMapBounds
) : RoutingService {
    override suspend fun route(request: TripRequest): RoutingResult {
        val destination = request.destination ?: return RoutingResult.NoRouteFound
        val mode = request.modes.firstOrNull() ?: return RoutingResult.NoRouteFound

        listOf(request.origin.point, destination.point)
            .firstOrNull { !isInServiceArea(it) }
            ?.let { return RoutingResult.OutsideServiceArea(it) }

        return RoutingResult.Success(
            listOf(
                RouteLeg.straightLine(
                    transportId = mode,
                    from = request.origin.point,
                    to = destination.point,
                    durationSeconds = request.plannedMinutes * 60L
                )
            )
        )
    }

    /**
     * Points picked on the very edge of the sample map can land a rounding
     * error outside it after conversion, so the edge gets a tiny margin.
     */
    private fun isInServiceArea(point: GeoPoint): Boolean =
        point.latitude in (serviceArea.south - EDGE_MARGIN_DEGREES)..(serviceArea.north + EDGE_MARGIN_DEGREES) &&
            point.longitude in (serviceArea.west - EDGE_MARGIN_DEGREES)..(serviceArea.east + EDGE_MARGIN_DEGREES)

    private companion object {
        /** About 0.1 mm; far below any real distance, far above conversion rounding. */
        const val EDGE_MARGIN_DEGREES = 1e-9
    }
}
