package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.data.assets.toSampleMapPlace
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.TripPoint
import kotlin.test.assertEquals

/** Allowed float rounding when a stored place is converted back to a PNG position. */
internal const val MAP_POINT_TOLERANCE: Float = 1e-5f

/** Fixture origin: the map position (0.20, 0.30) on map_sample.png. */
internal val testOrigin: Place = TripPoint("Start", 0.20f, 0.30f).toSampleMapPlace()

/** Fixture destination: the map position (0.80, 0.70) on map_sample.png. */
internal val testDestination: Place = TripPoint("Destination", 0.80f, 0.70f).toSampleMapPlace()

/**
 * One straight leg between two fixture places, as the generator creates.
 *
 * @param transportId Mode of the leg; must be one of the trip's selected transports.
 * @param from Leg start; defaults to testOrigin.
 * @param to Leg end; defaults to testDestination.
 */
internal fun testLegs(
    transportId: TransportId,
    from: Place = testOrigin,
    to: Place = testDestination
): List<RouteLeg> = listOf(RouteLeg.straightLine(transportId, from.point, to.point, durationSeconds = 30 * 60L))

/**
 * Checks two PNG positions match within MAP_POINT_TOLERANCE, for points that
 * went through a conversion to coordinates and back.
 *
 * @param expected Position the code should produce.
 * @param actual Position it produced.
 */
internal fun assertNearMapPoint(expected: TripPoint, actual: TripPoint) {
    assertEquals(expected.x, actual.x, absoluteTolerance = MAP_POINT_TOLERANCE)
    assertEquals(expected.y, actual.y, absoluteTolerance = MAP_POINT_TOLERANCE)
}
