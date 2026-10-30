package com.example.rnd_transit_mtl

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.model.trip.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.example.rnd_transit_mtl.navigation.CurrentTripScreenKey
import com.example.rnd_transit_mtl.navigation.ReviewOrigin
import com.example.rnd_transit_mtl.navigation.TripDetailsScreenKey
import com.example.rnd_transit_mtl.navigation.TripReviewScreenKey
import com.example.rnd_transit_mtl.navigation.backStackConfig
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

/** Saves destinations the way the back stack does, because a restore failure only appears at runtime. */
class TypedIdNavigationStateTest {
    private val serializer = PolymorphicSerializer(NavKey::class)

    private fun assertSurvivesSaving(key: NavKey) {
        val saved = encodeToSavedState(serializer, key, backStackConfig)
        assertEquals(key, decodeFromSavedState(serializer, saved, backStackConfig))
    }

    @Test
    fun destinationsHoldingATripIdSurviveSaving() {
        val tripId = TripId("trip-1")

        assertSurvivesSaving(TripDetailsScreenKey(tripId))
        assertSurvivesSaving(
            TripReviewScreenKey(tripId, TripReviewMode.EDIT, ReviewOrigin.Details(tripId))
        )
    }

    @Test
    fun currentTripDestinationKeepsEveryTypedIdInItsTrip() {
        val bus = TransportId("bus")
        val zone = ZoneId("A")

        assertSurvivesSaving(
            CurrentTripScreenKey(
                Trip(
                    id = TripId("trip-1"),
                    title = "Sample trip",
                    description = "Fixed record for save verification.",
                    imageUrl = "",
                    start = TripPoint("Start", 0.2f, 0.2f),
                    destination = TripPoint("Destination", 0.8f, 0.8f),
                    plannedMinutes = 30,
                    selectedTransports = listOf(TripTransportSnapshot(bus, "Bus", true)),
                    selectedRoutes = listOf(TripRouteSnapshot(RouteId("bus:401"), bus, "401")),
                    attractionIntensity = 50f,
                    distanceKm = 1.5,
                    createdAtEpochMillis = 1_000L,
                    selectedZoneIds = listOf(zone),
                    selectedZonesByTransport = mapOf(bus to listOf(zone))
                )
            )
        )
    }
}
