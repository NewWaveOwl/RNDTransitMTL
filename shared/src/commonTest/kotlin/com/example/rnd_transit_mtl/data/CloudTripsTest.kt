package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.View.history.previews.unreviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.data.trips.CloudTrips
import com.example.rnd_transit_mtl.data.trips.TripRepository
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.review.TripReview
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudTripsTest {
    @Test
    fun pendingTripDetailsStayAvailableAfterFailedSave() = runTest {
        val trip = unreviewedHistoryPreviewTrip
        val store = TripsStore()
        store.restorePendingFinalTrips(listOf(trip))
        val repository = RecordingRepository().apply { failSave = true }
        val cloud = CloudTrips(repository, store)

        assertFailsWith<IllegalStateException> { cloud.flushFinalTrips() }
        assertEquals(trip, cloud.details(trip.id))
        assertTrue(trip.id in store.state.value.pendingFinalTripIds)

        repository.failSave = false
        cloud.flushFinalTrip(trip.id)
        assertFalse(trip.id in store.state.value.pendingFinalTripIds)
        assertEquals(trip, cloud.history().single())
        assertEquals(1, cloud.revision)
    }

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

    @Test fun pendingCompletedAndCancelledTripsCanBeDeletedOfflineWithoutUploading() = runTest {
        for (status in listOf(TripStatus.Completed, TripStatus.Cancelled)) {
            val original = unreviewedHistoryPreviewTrip
            val trip = original.copy(status = status,
                completedAt = original.completedAt.takeIf { status == TripStatus.Completed },
                cancelledAt = original.completedAt.takeIf { status == TripStatus.Cancelled })
            val store = TripsStore()
            store.restorePendingFinalTrips(listOf(trip))
            var persisted = listOf(trip)
            store.onPendingFinalTripsChanged = { persisted = it }
            val repository = RecordingRepository().apply { failSave = true; failDelete = true }
            val cloud = CloudTrips(repository, store)

            cloud.removeTrip(trip.id)
            cloud.flushFinalTrips()

            assertNull(store.findInHistory(trip.id))
            assertTrue(store.state.value.pendingFinalTripIds.isEmpty())
            assertTrue(persisted.isEmpty())
            assertTrue(repository.events.isEmpty())
            val restored = TripsStore().apply { restorePendingFinalTrips(persisted) }
            assertNull(restored.findInHistory(trip.id))
        }
    }

    @Test fun deletionWaitsForAnInflightSaveThenRemovesItsCloudRecord() = runTest {
        val trip = unreviewedHistoryPreviewTrip
        val store = TripsStore().apply { restorePendingFinalTrips(listOf(trip)) }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = RecordingRepository().apply {
            beforeSave = { entered.complete(Unit); release.await() }
        }
        val cloud = CloudTrips(repository, store)
        val sync = async { cloud.flushFinalTrips() }
        entered.await()
        val deletion = async { cloud.removeTrip(trip.id) }
        runCurrent()
        assertFalse(deletion.isCompleted)
        release.complete(Unit)
        sync.await(); deletion.await()
        cloud.flushFinalTrips()
        assertEquals(listOf("save", "delete"), repository.events)
        assertTrue(repository.records.isEmpty())
        assertNull(store.findInHistory(trip.id))
        assertTrue(store.pendingFinalTrips().isEmpty())
    }

    @Test fun failedInflightSaveDoesNotBlockLocalDeletionOrResurrectTheTrip() = runTest {
        val trip = unreviewedHistoryPreviewTrip
        val store = TripsStore().apply { restorePendingFinalTrips(listOf(trip)) }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = RecordingRepository().apply {
            failSave = true
            beforeSave = { entered.complete(Unit); release.await() }
        }
        val cloud = CloudTrips(repository, store)
        val sync = async { runCatching { cloud.flushFinalTrips() } }
        entered.await()
        val deletion = async { cloud.removeTrip(trip.id) }
        runCurrent()
        release.complete(Unit)
        assertTrue(sync.await().isFailure)
        deletion.await()
        cloud.flushFinalTrips()
        assertTrue(repository.events.isEmpty())
        assertNull(store.findInHistory(trip.id))
        assertTrue(store.state.value.pendingFinalTripIds.isEmpty())
    }

    @Test fun syncedTripRemainsLocalWhenCloudDeletionFailsAndCanBeRetried() = runTest {
        val trip = unreviewedHistoryPreviewTrip
        val store = TripsStore().apply { acceptStoredTrip(trip) }
        val repository = RecordingRepository().apply { records[trip.id] = trip; failDelete = true }
        val cloud = CloudTrips(repository, store)
        assertFailsWith<IllegalStateException> { cloud.removeTrip(trip.id) }
        assertEquals(trip, store.findInHistory(trip.id))
        repository.failDelete = false
        cloud.removeTrip(trip.id)
        assertEquals(listOf("delete"), repository.events)
        assertNull(store.findInHistory(trip.id))
    }

    private class RecordingRepository : TripRepository {
        val records = mutableMapOf<TripId, Trip>()
        val events = mutableListOf<String>()
        var failSave = false
        var failDelete = false
        var beforeSave: suspend () -> Unit = {}

        override suspend fun saveFinalTrip(trip: Trip): Trip {
            beforeSave()
            check(!failSave) { "Offline" }
            events += "save"
            records[trip.id] = trip
            return trip
        }
        override suspend fun listHistory() = records.values.toList()
        override suspend fun getTrip(id: TripId) = records[id]
        override suspend fun updateTrip(id: TripId, title: String, description: String): Boolean =
            error("Unused")
        override suspend fun deleteTrip(id: TripId) {
            check(!failDelete) { "Offline" }
            events += "delete"
            records.remove(id)
        }
        override suspend fun saveReview(tripId: TripId, review: TripReview): TripReview {
            val trip = checkNotNull(records[tripId]) { "Parent trip missing" }
            events += "review"
            records[tripId] = trip.copy(review = review)
            return review
        }
        override suspend fun deleteReview(tripId: TripId) = error("Unused")
    }
}
