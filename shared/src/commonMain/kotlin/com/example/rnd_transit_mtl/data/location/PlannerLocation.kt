package com.example.rnd_transit_mtl.data.location

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlinx.coroutines.flow.Flow

internal interface PlannerLocation {
    /** Last accepted measured position, retained during provider outages and shared across screens. */
    val fix: LocationUpdate.Fix?
    /** Current access/provider state, separate from the retained map position. */
    val currentUpdate: LocationUpdate? get() = fix
    /** One acceptance history survives navigation between the two map screens. */
    val jumpFilter: LocationJumpFilter? get() = null
    val accuracyMonitor: LocationAccuracyMonitor? get() = null
    /** Returns false if this device cannot open the requested system settings. */
    fun openAppLocationSettings(): Boolean = false
    fun openDeviceLocationSettings(): Boolean = false
    val automaticOriginUpdates: Flow<LocationUpdate>? get() = null
    /** Raw provider activity for trip quality/freshness; rejected points cannot change the planner origin. */
    val trackingUpdates: Flow<LocationUpdate>? get() = automaticOriginUpdates
    val containsPoint: (GeoPoint) -> Boolean
    fun request(onResult: ((LocationUpdate.Fix?) -> Unit)? = null)
}

internal val LocalPlannerLocation = staticCompositionLocalOf<PlannerLocation?> { null }

/** Explicit requests wait for a provider measurement; cancellation ignores late callbacks. */
internal suspend fun PlannerLocation.awaitFreshFix(): LocationUpdate.Fix? =
    kotlinx.coroutines.withTimeoutOrNull(16_000) {
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            request { fix -> if (continuation.isActive) continuation.resumeWith(Result.success(fix)) }
        }
    }

/** Reuse only a recent measured position; old map positions still require a new fix. */
internal suspend fun PlannerLocation.awaitPlanningLocation(
    nowEpochMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
): LocationUpdate {
    val current = currentUpdate
    val recent = fix
    if (current is LocationUpdate.Fix && recent?.timestampEpochMillis?.let {
            nowEpochMillis() - it in 0L..LOCATION_FIX_MAX_AGE_MILLIS
        } == true) return recent
    // Requesting also allows the platform to ask for permission when necessary.
    return awaitFreshFix() ?: if (currentUpdate == LocationUpdate.PermissionDenied) {
        LocationUpdate.PermissionDenied
    } else LocationUpdate.Unavailable
}
