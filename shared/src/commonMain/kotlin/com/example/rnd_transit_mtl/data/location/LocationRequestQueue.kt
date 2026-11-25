package com.example.rnd_transit_mtl.data.location

/** Independent callers share measurements, without replacing callbacks or extending each other's deadline. */
internal class LocationRequestQueue(collectingInitially: Boolean = true) {
    private data class Pending(val expiresAt: Long?, val callback: (LocationUpdate.Fix?) -> Unit)
    private val pending = mutableListOf<Pending>()
    private var collecting = collectingInitially
    val size get() = pending.size
    val activeSize get() = pending.count { it.expiresAt != null }

    fun add(now: Long, callback: (LocationUpdate.Fix?) -> Unit) {
        pending += Pending(if (collecting) now + 15_000L else null, callback)
    }

    /** Starts the 15-second measurement deadline once permission is granted and collection can run. */
    fun setCollecting(active: Boolean, now: Long): Boolean {
        collecting = active
        if (!active) return false
        var started = false
        for (index in pending.indices) {
            val request = pending[index]
            if (request.expiresAt == null) {
                pending[index] = request.copy(expiresAt = now + 15_000L)
                started = true
            }
        }
        return started
    }

    fun receive(fix: LocationUpdate.Fix, now: Long) {
        val measuredAt = fix.timestampEpochMillis ?: return
        if (now - measuredAt !in 0L..LOCATION_FIX_MAX_AGE_MILLIS) return
        val completed = pending.toList()
        pending.clear()
        completed.forEach { request ->
            request.callback(fix.takeIf { request.expiresAt == null || now < request.expiresAt })
        }
    }

    fun expire(now: Long) {
        val expired = pending.filter { it.expiresAt != null && now >= it.expiresAt }
        pending.removeAll(expired.toSet())
        expired.forEach { it.callback(null) }
    }

    fun denyAccess() {
        val completed = pending.toList()
        pending.clear()
        completed.forEach { it.callback(null) }
    }
}
