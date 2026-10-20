package com.example.rnd_transit_mtl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.example.rnd_transit_mtl.data.MockTripGenerator
import com.example.rnd_transit_mtl.data.TripGenerationBounds
import com.example.rnd_transit_mtl.data.TripGenerationInput
import com.example.rnd_transit_mtl.data.TripGenerationResult
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.state.TripActionResult
import com.example.rnd_transit_mtl.state.TripsStore
import com.example.rnd_transit_mtl.ui.bundledMapSize
import com.example.rnd_transit_mtl.ui.mapImageRect
import com.example.rnd_transit_mtl.ui.plannerTripBounds
import com.example.rnd_transit_mtl.ui.toMapPosition
import com.example.rnd_transit_mtl.ui.tripPointerRotation
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pure geometry/state checks; no Compose clock or changing preview data. */
class TripMapGeometryTest {
    @Test
    fun portraitCropKeepsPngProportionsAndMatchesCenteredPlannerImage() {
        val viewport = Size(390f, 700f)
        val rect = mapImageRect(viewport, bundledMapSize, crop = true)
        assertEquals(700f, rect.height, absoluteTolerance = 0.001f)
        assertTrue(rect.width > viewport.width)
        assertEquals(195f, rect.center.x, absoluteTolerance = 0.001f)
        assertEquals(350f, rect.center.y, absoluteTolerance = 0.001f)
        assertEquals(
            bundledMapSize.width / bundledMapSize.height,
            rect.width / rect.height,
            absoluteTolerance = 0.001f
        )
    }

    @Test
    fun suppliedUpwardPointerFacesAllFourMovementDirections() {
        assertEquals(0f, tripPointerRotation(Offset.Zero, Offset(0f, -1f)))
        assertEquals(90f, tripPointerRotation(Offset.Zero, Offset(1f, 0f)))
        assertEquals(180f, tripPointerRotation(Offset.Zero, Offset(0f, 1f)))
        assertEquals(270f, tripPointerRotation(Offset.Zero, Offset(-1f, 0f)))
    }

    @Test
    fun seededEndpointsUseVisibleCropOnceAndKeepSyntheticDistance() {
        val viewport = Size(390f, 700f)
        val bounds = plannerTripBounds(viewport, bundledMapSize, titleHeight = 64f)
        val localTrip = generatedTrip(TripGenerationBounds())
        val visibleTrip = generatedTrip(bounds)
        val rect = mapImageRect(viewport, bundledMapSize, crop = true)
        assertEquals(localTrip.id, visibleTrip.id)
        assertEquals(localTrip.distanceKm, visibleTrip.distanceKm)
        assertEquals(bounds.toSourcePoint(localTrip.start), visibleTrip.start)
        assertEquals(bounds.toSourcePoint(localTrip.destination), visibleTrip.destination)

        for (point in listOf(visibleTrip.start, visibleTrip.destination)) {
            val screenPoint = point.toMapPosition(rect)
            assertTrue(screenPoint.x in 0f..viewport.width)
            assertTrue(screenPoint.y in 64f..(viewport.height * 0.55f))
        }
        val startOnScreen = visibleTrip.start.toMapPosition(rect)
        val destinationOnScreen = visibleTrip.destination.toMapPosition(rect)
        // Normalized separation is enforced in the visible generation area.
        assertTrue(
            localTrip.start.normalizedDistanceTo(localTrip.destination) >=
                MockTripGenerator.MIN_NORMALIZED_SEPARATION
        )
        assertTrue((destinationOnScreen - startOnScreen).getDistance() > 60f)
    }

    @Test
    fun viewportChangeAndRestorationDoNotReplaceStoredEndpointsOrElapsedTime() {
        val bounds = plannerTripBounds(Size(390f, 700f), bundledMapSize, 64f)
        val trip = generatedTrip(bounds)
        val store = TripsStore(nowEpochMillis = { 20_000L })
        assertEquals(TripActionResult.Applied, store.start(trip))
        assertEquals(TripActionResult.Applied, store.updateElapsed(trip.id, 3_500L))
        val restored = assertNotNull(TripsStore.fromSavedStateJson(
            store.toSavedStateJson(), nowEpochMillis = { 30_000L }
        ))
        val active = assertNotNull(restored.activeTrip)
        assertEquals(trip.id, active.id)
        assertEquals(trip.start, active.start)
        assertEquals(trip.destination, active.destination)
        assertEquals(3_500L, restored.elapsedMillis)
        assertEquals(0.35f, assertNotNull(restored.progressFor(trip.id)), absoluteTolerance = 0.0001f)
        assertTrue(restored.completedTrips.isEmpty())

        // A different camera changes projected pixels, not the saved Trip.
        val desktopRect = mapImageRect(Size(1000f, 700f), bundledMapSize, crop = true)
        assertTrue(trip.start.toMapPosition(desktopRect) !=
            trip.start.toMapPosition(mapImageRect(Size(390f, 700f), bundledMapSize, true)))
        assertEquals(trip, restored.activeTrip)
    }

    private fun generatedTrip(bounds: TripGenerationBounds): Trip =
        assertIs<TripGenerationResult.Success>(
            MockTripGenerator(
                random = Random(7),
                idFactory = { "visible-crop-test" },
                nowEpochMillis = { 1_000L }
            ).generate(
                input = TripGenerationInput(
                    title = "Trip 1",
                    description = "Random mock route with 30 planned minutes.",
                    imageUrl = "",
                    plannedMinutes = 30,
                    selectedTransportIds = listOf("walk"),
                    selectedRouteIds = emptyList(),
                    attractionIntensity = 90f
                ),
                transportTypes = listOf(TransportType("walk", "Walk", false)),
                transportRoutes = emptyList(),
                existingTripIds = emptySet(),
                bounds = bounds
            )
        ).trip
}
