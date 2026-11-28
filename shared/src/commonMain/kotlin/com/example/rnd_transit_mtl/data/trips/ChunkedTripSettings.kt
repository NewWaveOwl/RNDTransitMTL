package com.example.rnd_transit_mtl.data.trips

import com.russhwolf.settings.Settings

internal class ChunkedTripSettings(private val settings: Settings = Settings()) {
    fun read(key: String): String? {
        val slot = settings.getIntOrNull(key) ?: return null
        val prefix = slotPrefix(key, slot)
        val chunkCount = settings.getInt(prefix + "count", 0)
        return buildString {
            repeat(chunkCount) { index ->
                append(settings.getString(prefix + index, ""))
            }
        }
    }

    fun write(key: String, value: String) {
        val currentSlot = settings.getInt(key, 0)
        val nextSlot = 1 - currentSlot
        val prefix = slotPrefix(key, nextSlot)
        removeSlot(key, nextSlot)
        val chunks = value.chunked(4_000)
        chunks.forEachIndexed { index, chunk -> settings.putString(prefix + index, chunk) }
        settings.putInt(prefix + "count", chunks.size)
        // Switch slots only after the new value is complete.
        settings.putInt(key, nextSlot)
        removeSlot(key, currentSlot)
    }

    fun clear(key: String) {
        settings.remove(key)
        removeSlot(key, 0)
        removeSlot(key, 1)
    }

    private fun removeSlot(key: String, slot: Int) {
        val prefix = slotPrefix(key, slot)
        settings.keys.filter { it.startsWith(prefix) }.forEach(settings::remove)
    }

    private fun slotPrefix(key: String, slot: Int) = "${key}_${slot}_"
}
