package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.example.rnd_transit_mtl.data.location.LocationSource
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

internal class AndroidLocationSource(private val context: Context) : LocationSource {
    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override val updates = callbackFlow<LocationUpdate> {
        if (!hasPermission()) {
            trySend(LocationUpdate.PermissionDenied)
            close()
            return@callbackFlow
        }
        val manager = context.getSystemService(LocationManager::class.java)
        val registered = mutableListOf<String>()
        fun reportIfUnavailable() {
            if (registered.none(manager::isProviderEnabled)) trySend(LocationUpdate.Unavailable)
        }
        val listener = object : LocationListener {
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = reportIfUnavailable()
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onLocationChanged(location: Location) {
                val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
                if (ageNanos !in 0L..15_000_000_000L) return
                trySend(LocationUpdate.Fix(
                    GeoPoint(location.latitude, location.longitude),
                    if (location.hasAccuracy()) location.accuracy.toDouble() else 0.0
                ))
            }
        }
        // Android keeps these listeners registered when location services are toggled.
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            runCatching {
                manager.requestLocationUpdates(provider, 2_000L, 2f, listener, Looper.getMainLooper())
                registered.add(provider)
            }
        }
        reportIfUnavailable()
        awaitClose { manager.removeUpdates(listener) }
    }
}
