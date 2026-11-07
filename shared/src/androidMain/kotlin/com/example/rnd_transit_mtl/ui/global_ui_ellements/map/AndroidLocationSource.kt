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
        val listener = object : LocationListener {
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onLocationChanged(location: Location) {
                trySend(LocationUpdate.Fix(
                    GeoPoint(location.latitude, location.longitude),
                    if (location.hasAccuracy()) location.accuracy.toDouble() else 0.0
                ))
            }
        }
        val registered = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { provider ->
                runCatching {
                    if (!manager.isProviderEnabled(provider)) return@runCatching false
                    manager.requestLocationUpdates(provider, 2_000L, 2f, listener, Looper.getMainLooper())
                    true
                }.getOrDefault(false)
            }
        if (registered.isEmpty()) trySend(LocationUpdate.Unavailable)
        awaitClose { manager.removeUpdates(listener) }
    }
}
