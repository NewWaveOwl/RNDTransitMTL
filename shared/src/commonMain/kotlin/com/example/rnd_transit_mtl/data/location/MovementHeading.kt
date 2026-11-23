package com.example.rnd_transit_mtl.data.location

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

/** Retains the last meaningful movement heading while stationary or inside GPS noise. */
internal class MovementHeading {
    var degrees by mutableStateOf(0f)
        private set
    private var anchor: LocationUpdate.Fix? = null

    fun receive(fix: LocationUpdate.Fix) {
        val previous = anchor
        if (previous == null) { anchor = fix; return }
        if (previous.point.distanceMetersTo(fix.point) < maxOf(3.0, minOf(15.0,
                maxOf(previous.accuracyMeters, fix.accuracyMeters)))) return
        val lat1 = previous.point.latitude * PI / 180
        val lat2 = fix.point.latitude * PI / 180
        val lon = (fix.point.longitude - previous.point.longitude) * PI / 180
        degrees = ((atan2(sin(lon) * cos(lat2),
            cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(lon)) * 180 / PI + 360) % 360).toFloat()
        anchor = fix
    }
}
