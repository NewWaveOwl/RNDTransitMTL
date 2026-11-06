package com.example.rnd_transit_mtl.model

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.trip.OsmElementType
import com.example.rnd_transit_mtl.model.trip.OsmReference
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.TripRequest
import com.example.rnd_transit_mtl.model.trip.TripRequestError
import com.example.rnd_transit_mtl.model.trip.TripStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripModelsTest {
    private val walk = TransportId("walk")
    private val metro = TransportId("metro")
    private val supported = setOf(walk, metro)
    private val berriUqam = Place("Berri-UQAM", GeoPoint(45.51510, -73.56113))
    private val sainteJustine = Place("CHU Sainte-Justine", GeoPoint(45.502880, -73.624792))

    private fun request(
        plannedMinutes: Int = 30,
        modes: List<TransportId> = listOf(walk),
        destination: Place? = sainteJustine,
        zonesByTransport: Map<TransportId, List<ZoneId>> = emptyMap()
    ) = TripRequest(
        ownerId = UserId("user-1"),
        origin = berriUqam,
        destination = destination,
        plannedMinutes = plannedMinutes,
        modes = modes,
        zonesByTransport = zonesByTransport
    )

    @Test
    fun placeRequiresALabelAndPositiveOsmId() {
        assertFailsWith<IllegalArgumentException> { Place(" ", GeoPoint(45.5, -73.6)) }
        assertFailsWith<IllegalArgumentException> { OsmReference(OsmElementType.Node, 0) }
    }

    @Test
    fun routeLegRejectsImpossibleValues() {
        val line = listOf(berriUqam.point, sainteJustine.point)

        assertFailsWith<IllegalArgumentException> {
            RouteLeg(walk, geometry = listOf(berriUqam.point), distanceMeters = 1.0, durationSeconds = 1, isRouted = false)
        }
        assertFailsWith<IllegalArgumentException> {
            RouteLeg(walk, geometry = line, distanceMeters = -1.0, durationSeconds = 1, isRouted = false)
        }
        assertFailsWith<IllegalArgumentException> {
            RouteLeg(walk, geometry = line, distanceMeters = 1.0, durationSeconds = -1, isRouted = false)
        }
        val leg = RouteLeg(walk, geometry = line, distanceMeters = 0.0, durationSeconds = 0, isRouted = false)
        assertEquals(berriUqam.point, leg.start)
        assertEquals(sainteJustine.point, leg.end)
    }

    @Test
    fun onlyTheAgreedStatusChangesAreAllowed() {
        val allowed = setOf(
            TripStatus.Generated to TripStatus.Active,
            TripStatus.Active to TripStatus.Completed,
            TripStatus.Active to TripStatus.Cancelled
        )

        for (from in TripStatus.entries) {
            for (to in TripStatus.entries) {
                assertEquals((from to to) in allowed, from.canMoveTo(to), "$from -> $to")
            }
        }
        assertTrue(TripStatus.Completed.isFinal && TripStatus.Cancelled.isFinal)
        assertFalse(TripStatus.Generated.isFinal || TripStatus.Active.isFinal)
    }

    @Test
    fun validRequestHasNoErrors() {
        assertEquals(emptyList(), request().validate(supported))
        assertEquals(emptyList(), request(destination = null).validate(supported))
    }

    @Test
    fun zeroAndOffStepMinutesAreRejected() {
        assertEquals(listOf(TripRequestError.InvalidPlannedMinutes(0)), request(plannedMinutes = 0).validate(supported))
        assertEquals(listOf(TripRequestError.InvalidPlannedMinutes(7)), request(plannedMinutes = 7).validate(supported))
        assertEquals(listOf(TripRequestError.InvalidPlannedMinutes(365)), request(plannedMinutes = 365).validate(supported))
    }

    @Test
    fun modeProblemsAreReportedSpecifically() {
        val bike = TransportId("bike")

        assertEquals(listOf(TripRequestError.NoModes), request(modes = emptyList()).validate(supported))
        assertEquals(listOf(TripRequestError.DuplicateMode(walk)), request(modes = listOf(walk, walk)).validate(supported))
        assertEquals(listOf(TripRequestError.UnsupportedMode(bike)), request(modes = listOf(bike)).validate(supported))
    }

    @Test
    fun zoneRestrictionsMustMatchEnabledModes() {
        assertEquals(
            listOf(TripRequestError.ZonesForUnselectedMode(metro)),
            request(zonesByTransport = mapOf(metro to listOf(ZoneId("A")))).validate(supported)
        )
        assertEquals(
            listOf(TripRequestError.NoZonesForMode(walk)),
            request(zonesByTransport = mapOf(walk to emptyList())).validate(supported)
        )
    }

    @Test
    fun sameOriginAndDestinationIsRejected() {
        assertEquals(
            listOf(TripRequestError.SameOriginAndDestination),
            request(destination = berriUqam.copy(label = "Same spot")).validate(supported)
        )
    }

    @Test
    fun everyProblemIsReportedAtOnce() {
        val errors = request(plannedMinutes = 0, modes = emptyList(), destination = berriUqam).validate(supported)

        assertEquals(
            listOf(
                TripRequestError.InvalidPlannedMinutes(0),
                TripRequestError.NoModes,
                TripRequestError.SameOriginAndDestination
            ),
            errors
        )
    }
}
