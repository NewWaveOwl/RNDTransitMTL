package com.example.rnd_transit_mtl.model.trip

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import kotlinx.serialization.Serializable

/**
 * One leg of a trip travelled with a single transport mode, in travel order.
 *
 * isRouted tells whether geometry follows real streets or transit lines from a
 * routing service. Fake and fallback routes set it to false, so screens never
 * present a straight line as a real route.
 *
 * @property transportId Mode used for this leg.
 * @property routeId Line used, such as a bus route; null for modes without routes.
 * @property geometry Path in travel order; at least a start and an end point.
 * @property distanceMeters Travelled distance; never negative.
 * @property durationSeconds Expected travel time; never negative.
 * @property isRouted True only when geometry came from a real routing service.
 */
@Serializable
data class RouteLeg(
    val transportId: TransportId,
    val routeId: RouteId? = null,
    val geometry: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val isRouted: Boolean
) {
    init {
        require(geometry.size >= 2) { "A route leg needs at least a start and an end point." }
        require(distanceMeters.isFinite() && distanceMeters >= 0.0) {
            "Leg distance must not be negative."
        }
        require(durationSeconds >= 0L) { "Leg duration must not be negative." }
    }

    companion object {
        /**
         * Creates an unrouted leg drawn as a straight line, for demo trips and
         * fallbacks before a routing service exists. isRouted is always false.
         *
         * @param transportId Mode used for this leg.
         * @param from Where the leg starts.
         * @param to Where the leg ends.
         * @param durationSeconds Expected travel time; never negative.
         * @param routeId Line used, if any.
         * @return A two-point leg whose distance is the straight-line distance.
         */
        fun straightLine(
            transportId: TransportId,
            from: GeoPoint,
            to: GeoPoint,
            durationSeconds: Long,
            routeId: RouteId? = null
        ): RouteLeg = RouteLeg(
            transportId = transportId,
            routeId = routeId,
            geometry = listOf(from, to),
            distanceMeters = from.distanceMetersTo(to),
            durationSeconds = durationSeconds,
            isRouted = false
        )
    }

    /** Where this leg begins. */
    val start: GeoPoint get() = geometry.first()

    /** Where this leg ends. */
    val end: GeoPoint get() = geometry.last()
}
