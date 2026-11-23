package com.example.rnd_transit_mtl.data.location

/** A short sequence, rather than one provider outlier, can confirm a real relocation. */
internal class LocationCandidateConfirmation {
    private data class Sample(val fix: LocationUpdate.Fix, val time: Long)
    private val samples = mutableListOf<Sample>()

    fun clear() = samples.clear()

    fun add(fix: LocationUpdate.Fix, time: Long, maximumSpeed: Double): Boolean {
        if (fix.accuracyMeters > 250.0) {
            clear()
            return false
        }
        val first = samples.firstOrNull()
        val previous = samples.lastOrNull()
        if (previous != null && time <= previous.time) return false
        if (first != null && (time - first.time > 15_000L ||
                !plausibleMovement(first.fix, first.time, fix, time, maximumSpeed) ||
                (previous != null && !plausibleMovement(previous.fix, previous.time, fix, time, maximumSpeed)))) clear()
        samples.add(Sample(fix, time))
        return samples.size >= 3 && time - samples.first().time >= 4_000L
    }
}

private fun plausibleMovement(from: LocationUpdate.Fix, fromTime: Long, to: LocationUpdate.Fix,
                              toTime: Long, maximumSpeed: Double): Boolean {
    // Large reported error must not become permission to jump hundreds of metres.
    val seconds = ((toTime - fromTime).coerceAtLeast(0L) / 1_000.0).coerceAtMost(30.0)
    val allowance = 10.0 + minOf(from.accuracyMeters, 25.0) + minOf(to.accuracyMeters, 25.0)
    return from.point.distanceMetersTo(to.point) <= allowance + maximumSpeed * seconds
}

/** Shared by the planner and active trip; raw measurements remain available to both. */
internal class LocationJumpFilter {
    var maximumSpeedMetersPerSecond: Double = DefaultMaximumSpeed
    private var speedOwner: Any? = null
    var acceptedFix: LocationUpdate.Fix? = null
        private set
    private var anchorTime: Long? = null
    private var lastReadingTime: Long? = null
    private var lastResult: Result? = null
    private val confirmation = LocationCandidateConfirmation()

    data class Result(
        val reading: LocationUpdate.Fix,
        val accepted: Boolean,
        val relocated: Boolean = false,
        val trustedFix: LocationUpdate.Fix? = null,
    )

    fun useMaximumSpeed(owner: Any, metersPerSecond: Double) {
        speedOwner = owner
        maximumSpeedMetersPerSecond = metersPerSecond
    }

    fun releaseMaximumSpeed(owner: Any) {
        if (speedOwner !== owner) return
        speedOwner = null
        maximumSpeedMetersPerSecond = DefaultMaximumSpeed
    }

    /** Seeds an already displayed fix, including an old cache, without inventing a fresh measurement. */
    fun seed(fix: LocationUpdate.Fix, now: Long) {
        if (acceptedFix != null || (fix.timestampEpochMillis ?: now) > now) return
        acceptedFix = fix
        anchorTime = fix.timestampEpochMillis ?: now
    }

    fun evaluate(fix: LocationUpdate.Fix, now: Long): Result? {
        val time = fix.timestampEpochMillis ?: now
        if (now - time !in 0L..LOCATION_FIX_MAX_AGE_MILLIS) return null
        // The session and trip observe the same reading; do not count it twice toward confirmation.
        if (fix.timestampEpochMillis != null && time == lastReadingTime && lastResult?.reading == fix) return lastResult
        if (lastReadingTime?.let { time <= it } == true) return null
        lastReadingTime = time
        val anchor = acceptedFix
        val plausible = anchor == null || plausibleMovement(anchor, anchorTime ?: time, fix, time, maximumSpeedMetersPerSecond)
        val relocated = !plausible && confirmation.add(fix, time, maximumSpeedMetersPerSecond)
        val accepted = plausible || relocated
        if (accepted) {
            confirmation.clear()
            // Weak readings cannot gradually drag a reliable anchor across the map.
            if (anchor == null || fix.accuracyMeters <= 50.0 || anchor.accuracyMeters > 50.0 || relocated) {
                acceptedFix = fix
                anchorTime = time
            }
        }
        return Result(fix, accepted, relocated, acceptedFix).also { lastResult = it }
    }

    companion object {
        const val DefaultMaximumSpeed = 45.0
    }
}
