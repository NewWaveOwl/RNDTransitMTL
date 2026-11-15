package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.View.history.previews.unreviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.data.trips.CloudTrips
import com.example.rnd_transit_mtl.data.trips.TripRepository
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.review.TripReview
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CloudTripsTest {
    @Test
    fun reviewParentIsSavedOnceWithoutFlushingUnrelatedTrips() = runTest {
        val trip = unreviewedHistoryPreviewTrip
        val other = trip.copy(id = TripId("other-pending-trip"))
        val store = TripsStore()
        store.restorePendingFinalTrips(listOf(other, trip))
        val repository = RecordingRepository()
        val cloud = CloudTrips(repository, store)
        val review = TripReview(5)

        cloud.flushFinalTrip(trip.id)
        cloud.flushFinalTrip(trip.id)
        cloud.mutate { saveReview(trip.id, review) }

        assertEquals(listOf("save", "review"), repository.events)
        assertEquals(setOf(other.id), store.state.value.pendingFinalTripIds)
        assertEquals(2, cloud.revision)
    }

    private class RecordingRepository : TripRepository {
        val records = mutableMapOf<TripId, Trip>()
        val events = mutableListOf<String>()
        var failSave = false

        override suspend fun saveFinalTrip(trip: Trip): Trip {
            check(!failSave) { "Offline" }
            events += "save"
            records[trip.id] = trip
            return trip
        }
        override suspend fun listHistory() = records.values.toList()
        override suspend fun getTrip(id: TripId) = records[id]
        override suspend fun updateTrip(id: TripId, title: String, description: String): Boolean =
            error("Unused")
        override suspend fun deleteTrip(id: TripId) { records.remove(id) }
        override suspend fun saveReview(tripId: TripId, review: TripReview): TripReview {
            val trip = checkNotNull(records[tripId]) { "Parent trip missing" }
            events += "review"
            records[tripId] = trip.copy(review = review)
            return review
        }
        override suspend fun deleteReview(tripId: TripId) = error("Unused")
    }
}
