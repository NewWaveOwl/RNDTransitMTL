package com.example.rnd_transit_mtl.data.location

/** A short sequence, rather than one provider outlier, can confirm a real relocation. */
internal class LocationCandidateConfirmation {
    private data class Sample(val fix: LocationUpdate.Fix, val time: Long)
    private val samples = mutableListOf<Sample>()

    fun clear() = samples.clear()

    fun prefersTo(fix: LocationUpdate.Fix, time: Long): Boolean {
        val first = samples.firstOrNull() ?: return false
        if (time - first.time > 15_000L) { clear(); return false }
        return first.fix.accuracyMeters * 2 < fix.accuracyMeters
    }

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
        val stationary: Boolean = false,
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
        val previousReadingAt = lastReadingTime
        lastReadingTime = time
        val anchor = acceptedFix
        fun result(accepted: Boolean, relocated: Boolean = false, stationary: Boolean = false): Result =
            Result(fix, accepted, relocated, acceptedFix, stationary).also { lastResult = it }
        if (anchor != null) {
            // Poorer provider activity is diagnostic input, not evidence against a better candidate.
            val freshReliableAnchor = anchor.accuracyMeters <= 50.0 &&
                time - (anchorTime ?: time) <= LOCATION_UPDATE_TIMEOUT_MILLIS
            if ((anchor.accuracyMeters <= 50.0 && fix.accuracyMeters > 50.0) ||
                (freshReliableAnchor && fix.accuracyMeters > maxOf(15.0, anchor.accuracyMeters * 2)) ||
                confirmation.prefersTo(fix, time)) return result(false)
            val improved = fix.accuracyMeters <= 50.0 && fix.accuracyMeters * 2 < anchor.accuracyMeters
            val displacement = anchor.point.distanceMetersTo(fix.point)
            val noise = maxOf(3.0, minOf(15.0, anchor.accuracyMeters + fix.accuracyMeters))
            val slow = fix.speedMetersPerSecond?.let { it < 0.3 } != false
            if (previousReadingAt != null && !improved && slow && displacement <= noise) {
                // Fresh supporting measurements may retain a point; they must not create travel.
                acceptedFix = anchor.copy(timestampEpochMillis = fix.timestampEpochMillis,
                    speedMetersPerSecond = fix.speedMetersPerSecond ?: anchor.speedMetersPerSecond)
                anchorTime = time
                confirmation.clear()
                return result(true, stationary = true)
            }
            // Zero speed describes this measurement, not movement since an old anchor.
            // After an outage, allow consistent fixes to confirm the new stationary position.
            if (freshReliableAnchor && previousReadingAt != null && !improved && fix.speedMetersPerSecond?.let { it < 0.3 } == true &&
                anchor.speedMetersPerSecond?.let { it < 0.3 } == true) return result(false)
            // A precise correction inside an earlier coarse uncertainty circle can recover immediately.
            if (improved && displacement <= anchor.accuracyMeters + fix.accuracyMeters) {
                val relocated = displacement > maxOf(3.0, fix.accuracyMeters)
                acceptedFix = fix
                anchorTime = time
                confirmation.clear()
                return result(true, relocated)
            }
        }
        // A planner without measured movement must not assume that every device is travelling at bus speed.
        val speed = if (speedOwner == null && fix.speedMetersPerSecond?.let { it >= 0.3 } != true)
            minOf(maximumSpeedMetersPerSecond, 3.5) else maximumSpeedMetersPerSecond
        val plausible = anchor == null || plausibleMovement(anchor, anchorTime ?: time, fix, time, speed)
        val relocated = !plausible && fix.accuracyMeters <= 50.0 && confirmation.add(fix, time, speed)
        val accepted = plausible || relocated
        if (accepted) {
            confirmation.clear()
            // Weak readings cannot gradually drag a reliable anchor across the map.
            if (anchor == null || fix.accuracyMeters <= 50.0 || anchor.accuracyMeters > 50.0 || relocated) {
                acceptedFix = fix
                anchorTime = time
            }
        }
        return result(accepted, relocated)
    }

    companion object {
        const val DefaultMaximumSpeed = 45.0
    }
}
