package com.example.rnd_transit_mtl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.navigation.*
import com.example.rnd_transit_mtl.state.trip.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Checks that repeat/copy navigation never starts or overwrites a historical record. */
class PlannerCopyTest {
    @Test
    fun copyRequestsPreserveSavedIdentityAndPreferences() {
        val trip = reviewedHistoryPreviewTrip
        val store = checkNotNull(TripsStore.fromSavedStateJson(
            Json.encodeToString(TripsState(completedTrips = listOf(trip))), { 1_800_000_000_000L }))
        val stack = NavBackStack<NavKey>(TripDetailsScreenKey(trip.id))
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, store)
        assertFalse(navigation.openPlannerCopy("missing", true))
        assertTrue(navigation.openPlannerCopy(trip.id, true))
        assertEquals(MainScreenKey, navigator.current)
        assertEquals(trip.id to true, navigation.plannerCopyRequest)
        val planner = MainPlannerViewModel()
        planner.copyTripPreferences(trip)
        assertEquals(trip.plannedMinutes, planner.uiState.value.minutes)
        assertEquals(trip.selectedTransports.map { it.id }, planner.uiState.value.selectedTransportIds)
        assertEquals(trip.selectedZonesByTransport, planner.uiState.value.selectedZonesByTransport)
        navigation.consumePlannerCopy()
        assertNull(navigation.plannerCopyRequest)
        assertTrue(navigation.openPlannerCopy(trip.id, false))
        assertEquals(trip.id to false, navigation.plannerCopyRequest)
        assertNull(store.activeTrip)
        assertEquals(listOf(trip), store.completedTrips)
    }
}
