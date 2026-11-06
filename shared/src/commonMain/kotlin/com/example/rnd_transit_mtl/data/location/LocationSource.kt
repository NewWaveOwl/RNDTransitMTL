package com.example.rnd_transit_mtl.data.location

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlinx.coroutines.flow.Flow

/**
 * Supplies the device's position. Each platform adapts its own API (Android
 * FusedLocationProvider, iOS CoreLocation, browser geolocation) behind this
 * interface, and FakeLocationSource replays positions for previews and tests.
 *
 * Collecting updates starts listening and cancelling the collection stops it,
 * so the collector (a ViewModel) owns the lifetime. Screens never depend on
 * a platform location API directly.
 */
interface LocationSource {
    /** Position changes and permission or availability problems, in order. */
    val updates: Flow<LocationUpdate>
}

/** One event from a LocationSource. */
sealed class LocationUpdate {
    /**
     * @property point Measured position.
     * @property accuracyMeters Radius of likely error; never negative.
     */
    data class Fix(val point: GeoPoint, val accuracyMeters: Double) : LocationUpdate() {
        init {
            require(accuracyMeters.isFinite() && accuracyMeters >= 0.0) {
                "Location accuracy must not be negative."
            }
        }
    }

    /** The user has not allowed location access; manual origin choice is still possible. */
    data object PermissionDenied : LocationUpdate()

    /** Location is switched off or no position could be obtained. */
    data object Unavailable : LocationUpdate()
}
