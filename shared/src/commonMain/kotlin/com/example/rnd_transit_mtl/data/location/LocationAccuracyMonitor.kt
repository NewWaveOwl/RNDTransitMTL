package com.example.rnd_transit_mtl.data.location

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock

internal enum class LocationPermissionLevel { Unknown, Denied, Approximate, Precise }
internal enum class LocationAccuracyPrompt { PrecisePermission, WeakSignal, LocationDisabled }

internal data class LocationAccuracyState(
    val permission: LocationPermissionLevel = LocationPermissionLevel.Unknown,
    val locationEnabled: Boolean? = null,
    val accuracyMeters: Double? = null,
    val readingAgeSeconds: Long? = null,
    val prompt: LocationAccuracyPrompt? = null,
)

/** Observes provider quality without modifying coordinates, progress or the jump filter. */
internal class LocationAccuracyMonitor(
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private var dismissedAtMillis: Long? = null,
    private val onDismiss: (Long) -> Unit = {},
) {
    var state by mutableStateOf(LocationAccuracyState())
        private set
    private var lastReadingAt: Long? = null
    private var weakSince: Long? = null
    private var recoverySince: Long? = null
    private var weakSignal = false

    fun updateAccess(permission: LocationPermissionLevel, locationEnabled: Boolean) {
        if (permission != state.permission || locationEnabled != state.locationEnabled) resetQuality()
        state = state.copy(permission = permission, locationEnabled = locationEnabled)
        tick()
    }

    fun receive(fix: LocationUpdate.Fix) {
        val now = nowEpochMillis()
        val measuredAt = fix.timestampEpochMillis ?: now
        if (now - measuredAt !in 0L..LOCATION_FIX_MAX_AGE_MILLIS ||
            lastReadingAt?.let { measuredAt <= it } == true) return
        if (lastReadingAt?.let { measuredAt - it > LOCATION_FIX_MAX_AGE_MILLIS } == true) resetQuality()
        lastReadingAt = measuredAt
        state = state.copy(accuracyMeters = fix.accuracyMeters)
        when {
            fix.accuracyMeters > 50.0 -> {
                recoverySince = null
                val since = weakSince ?: measuredAt.also { weakSince = it }
                // Require a sequence spanning the threshold, rather than one old reading.
                if (measuredAt - since >= 15_000L) weakSignal = true
            }
            fix.accuracyMeters <= 25.0 -> {
                weakSince = null
                val since = recoverySince ?: measuredAt.also { recoverySince = it }
                if (measuredAt - since >= 10_000L) weakSignal = false
            }
            else -> {
                weakSince = null
                recoverySince = null
            }
        }
        tick()
    }

    fun tick() {
        val now = nowEpochMillis()
        val age = lastReadingAt?.let { (now - it).coerceAtLeast(0L) }
        if (age != null && age > LOCATION_FIX_MAX_AGE_MILLIS) resetQuality()
        val reason = when {
            state.permission == LocationPermissionLevel.Unknown -> null
            state.locationEnabled == false -> LocationAccuracyPrompt.LocationDisabled
            state.permission == LocationPermissionLevel.Approximate -> LocationAccuracyPrompt.PrecisePermission
            state.permission == LocationPermissionLevel.Precise && weakSignal -> LocationAccuracyPrompt.WeakSignal
            else -> null
        }
        val coolingDown = dismissedAtMillis?.let { now - it in 0L until 10 * 60_000L } == true
        state = state.copy(readingAgeSeconds = age?.div(1_000L), prompt = reason.takeUnless { coolingDown })
    }

    fun dismissPrompt() {
        dismissedAtMillis = nowEpochMillis().also(onDismiss)
        tick()
    }

    /** Background time cannot qualify as sustained weak signal or sustained recovery. */
    fun pause() {
        resetQuality()
        state = state.copy(prompt = null)
    }

    private fun resetQuality() {
        weakSince = null
        recoverySince = null
        weakSignal = false
    }
}
