package com.example.rnd_transit_mtl.data.location

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlinx.coroutines.flow.Flow

internal interface PlannerLocation {
    val fix: LocationUpdate.Fix?
    val automaticOriginUpdates: Flow<LocationUpdate>? get() = null
    val containsPoint: (GeoPoint) -> Boolean
    fun request(onResult: ((LocationUpdate.Fix?) -> Unit)? = null)
}

internal val LocalPlannerLocation = staticCompositionLocalOf<PlannerLocation?> { null }
