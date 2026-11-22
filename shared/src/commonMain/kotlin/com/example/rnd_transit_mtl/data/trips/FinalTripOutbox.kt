package com.example.rnd_transit_mtl.data.trips

import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.state.review.ReviewDraft
import com.example.rnd_transit_mtl.state.trip.TripsStore
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Keeps pending final records and unsaved reviews across process exits, scoped to their owner. */
internal class FinalTripOutbox(providedSettings: Settings? = null) {
    private val settings by lazy { providedSettings ?: Settings() }

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
            clear(KEY)
            return migrated
        }
        return pending ?: Pending(owner, emptyList())
    }

    private fun readPending(key: String): Pending? {
        val slot = settings.getIntOrNull(key) ?: return null
        val prefix = slotPrefix(key, slot)
        val count = settings.getInt(prefix + "count", 0)
        val encoded = buildString {
            repeat(count) { append(settings.getString(prefix + it, "")) }
        }
        return runCatching { Json.decodeFromString<Pending>(encoded) }.getOrNull()
    }

    private fun write(pending: Pending) {
        val key = ownerKey(pending.owner)
        if (pending.trips.isEmpty() && pending.reviews.isEmpty()) {
            clear(key)
            return
        }

        // Desktop preferences limit individual values. Switch slots only after all chunks are written.
        val oldSlot = settings.getInt(key, 0)
        val slot = 1 - oldSlot
        val prefix = slotPrefix(key, slot)
        removeSlot(key, slot)
        val chunks = Json.encodeToString(pending).chunked(4_000)
        chunks.forEachIndexed { index, chunk -> settings.putString(prefix + index, chunk) }
        settings.putInt(prefix + "count", chunks.size)
        settings.putInt(key, slot)
        removeSlot(key, oldSlot)
    }

    private fun clear(key: String) {
        settings.remove(key)
        removeSlot(key, 0)
        removeSlot(key, 1)
    }

    private fun removeSlot(key: String, slot: Int) {
        val prefix = slotPrefix(key, slot)
        settings.keys.filter { it.startsWith(prefix) }.forEach(settings::remove)
    }

    private fun ownerKey(owner: String) = "${KEY}_$owner"

    private fun slotPrefix(key: String, slot: Int) = "${key}_${slot}_"

    private companion object {
        const val KEY = "transit_pending_final_trips"
    }
}
