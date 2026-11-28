package com.example.rnd_transit_mtl.data.trips

import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.state.review.ReviewDraft
import com.example.rnd_transit_mtl.state.trip.TripsStore
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Keeps pending final records and unsaved reviews across process exits, scoped to their owner. */
internal class FinalTripOutbox(providedSettings: Settings? = null) {
    private val storage by lazy { ChunkedTripSettings(providedSettings ?: Settings()) }

    @Serializable
    private data class Pending(
        val owner: String,
        val trips: List<Trip>,
        val reviews: List<PendingReview> = emptyList(),
    )

    @Serializable
    private data class PendingReview(val trip: Trip, val draft: ReviewDraft)

    /** Restore before connecting callbacks, so a partial restoration never replaces the saved data. */
    fun bind(owner: String, store: TripsStore) {
        val pending = read(owner)
        store.restorePendingFinalTrips(pending.trips)
        for ((trip, draft) in pending.reviews) {
            // Compose saved state can already contain a newer draft for the same trip.
            if (store.findCompleted(trip.id) == null) store.restoreFinalTrip(trip)
            if (trip.id !in store.reviewDrafts) store.updateReviewDraft(trip.id, draft)
        }
        store.onPendingFinalTripsChanged = { persist(owner, store) }
        store.onReviewDraftsChanged = { persist(owner, store) }
        persist(owner, store)
    }

    private fun persist(owner: String, store: TripsStore) {
        val reviews = store.reviewDrafts.mapNotNull { (id, draft) ->
            store.findCompleted(id)?.let { PendingReview(it, draft) }
        }
        write(Pending(owner, store.pendingFinalTrips(), reviews))
    }

    private fun read(owner: String): Pending {
        val pending = readPending(ownerKey(owner))?.takeIf { it.owner == owner }
        val legacy = readPending(KEY)
        if (legacy?.owner == owner) {
            val migrated = pending ?: legacy
            write(migrated)
            storage.clear(KEY)
            return migrated
        }
        return pending ?: Pending(owner, emptyList())
    }

    private fun readPending(key: String): Pending? =
        storage.read(key)?.let { runCatching { Json.decodeFromString<Pending>(it) }.getOrNull() }

    private fun write(pending: Pending) {
        val key = ownerKey(pending.owner)
        if (pending.trips.isEmpty() && pending.reviews.isEmpty()) {
            storage.clear(key)
        } else {
            storage.write(key, Json.encodeToString(pending))
        }
    }

    private fun ownerKey(owner: String) = "${KEY}_$owner"

    private companion object {
        const val KEY = "transit_pending_final_trips"
    }
}
