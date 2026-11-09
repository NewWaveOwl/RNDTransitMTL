package com.example.rnd_transit_mtl.data.trips

import com.example.rnd_transit_mtl.model.trip.Trip
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Persists the signed-in account's unacknowledged final records across process exits. */
internal class FinalTripOutbox {
    private val settings by lazy { Settings() }

    @Serializable
    private data class Pending(val owner: String, val trips: List<Trip>)

    fun read(owner: String): List<Trip> {
        val pending = readPending(ownerKey(owner))
        val legacy = readPending(KEY)
        if (legacy?.owner == owner) {
            val trips = pending?.trips ?: legacy.trips
            write(owner, trips)
            clear(KEY)
            return trips
        }
        return pending?.trips.orEmpty()
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

    fun write(owner: String, trips: List<Trip>) {
        val key = ownerKey(owner)
        if (trips.isEmpty()) {
            clear(key)
            return
        }

        // Desktop preferences limit individual values. Switch slots only after all chunks are written.
        val oldSlot = settings.getInt(key, 0)
        val slot = 1 - oldSlot
        val prefix = slotPrefix(key, slot)
        removeSlot(key, slot)
        val chunks = Json.encodeToString(Pending(owner, trips)).chunked(4_000)
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
