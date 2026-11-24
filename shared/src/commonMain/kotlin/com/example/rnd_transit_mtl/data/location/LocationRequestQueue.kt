package com.example.rnd_transit_mtl.data.location

/** Independent callers share measurements, without replacing callbacks or extending each other's deadline. */
internal class LocationRequestQueue {
    private data class Pending(val expiresAt: Long, val callback: (LocationUpdate.Fix?) -> Unit)
    private val pending = mutableListOf<Pending>()
    val size get() = pending.size

    fun add(now: Long, callback: (LocationUpdate.Fix?) -> Unit) {
        pending += Pending(now + 15_000L, callback)
    }

    fun receive(fix: LocationUpdate.Fix, now: Long) {
        val measuredAt = fix.timestampEpochMillis ?: return
        if (now - measuredAt !in 0L..LOCATION_FIX_MAX_AGE_MILLIS) return
        val completed = pending.toList()
        pending.clear()
        completed.forEach { request -> request.callback(fix.takeIf { now < request.expiresAt }) }
    }

    fun expire(now: Long) {
        val expired = pending.filter { now >= it.expiresAt }
        pending.removeAll(expired.toSet())
        expired.forEach { it.callback(null) }
    }

    fun denyAccess() {
        val completed = pending.toList()
        pending.clear()
        completed.forEach { it.callback(null) }
    }
}
