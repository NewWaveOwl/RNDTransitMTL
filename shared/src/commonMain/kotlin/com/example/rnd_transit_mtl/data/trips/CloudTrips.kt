package com.example.rnd_transit_mtl.data.trips

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val LocalCloudTrips = staticCompositionLocalOf<CloudTrips?> { null }

/** Serializes cloud reads and writes within one signed-in session. */
internal class CloudTrips(
    val repository: TripRepository,
    private val store: TripsStore,
) {
    private val mutex = Mutex()
    var revision by mutableStateOf(0)
        private set

    suspend fun history(): List<Trip> = mutex.withLock {
        repository.listHistory()
    }

    suspend fun details(id: TripId): Trip? = mutex.withLock {
        if (id in store.state.value.pendingFinalTripIds) {
            store.findInHistory(id)
        } else {
            repository.getTrip(id).also { if (it != null) store.acceptStoredTrip(it) }
        }
    }

    suspend fun flushFinalTrips() = mutex.withLock {
        for (trip in store.pendingFinalTrips()) {
            persistFinalTrip(trip)
        }
    }

    /** Save this review/edit's parent without waiting for unrelated pending trips. */
    suspend fun flushFinalTrip(id: TripId) = mutex.withLock {
        store.pendingFinalTrips().firstOrNull { it.id == id }?.let { persistFinalTrip(it) }
        Unit
    }

    private suspend fun persistFinalTrip(trip: Trip) {
        val stored = repository.saveFinalTrip(trip)
        store.acceptStoredTrip(stored, acknowledgeWrite = true)
        revision++
    }

    suspend fun mutate(action: suspend TripRepository.() -> Unit) = mutex.withLock {
        repository.action()
        revision++
    }
}

internal suspend fun tryCloudAction(action: suspend () -> Unit): Boolean =
    try {
        action()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
