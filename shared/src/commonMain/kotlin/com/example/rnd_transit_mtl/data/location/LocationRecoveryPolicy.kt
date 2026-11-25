package com.example.rnd_transit_mtl.data.location

internal const val LOCATION_FIX_MAX_AGE_MILLIS = 15_000L
internal const val LOCATION_UPDATE_TIMEOUT_MILLIS = 30_000L
private const val MaximumRetryIntervalMillis = 120_000L

/** Monotonic measurement times keep retries independent of accuracy and wall-clock changes. */
internal class LocationRecoveryPolicy(startedAtMillis: Long) {
    private var lastMeasurementAt: Long? = null
    private var retryInterval = LOCATION_UPDATE_TIMEOUT_MILLIS
    private var retryAt = startedAtMillis + retryInterval

    fun recordFix(measuredAtMillis: Long, nowMillis: Long) {
        if (nowMillis - measuredAtMillis !in 0L..LOCATION_FIX_MAX_AGE_MILLIS ||
            lastMeasurementAt?.let { measuredAtMillis <= it } == true) return
        lastMeasurementAt = measuredAtMillis
        retryInterval = LOCATION_UPDATE_TIMEOUT_MILLIS
        retryAt = measuredAtMillis + retryInterval
    }

    /** Retry after 30 seconds of silence, then back off to 60 and 120 seconds. */
    fun retryIfStalled(nowMillis: Long): Boolean {
        if (nowMillis <= retryAt) return false
        retryInterval = (retryInterval * 2).coerceAtMost(MaximumRetryIntervalMillis)
        retryAt = nowMillis + retryInterval
        return true
    }
}
