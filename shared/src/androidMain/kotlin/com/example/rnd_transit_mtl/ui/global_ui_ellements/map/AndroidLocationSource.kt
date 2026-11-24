package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.example.rnd_transit_mtl.data.location.LocationSource
import com.example.rnd_transit_mtl.data.location.LocationPermissionLevel
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.data.location.LocationRecoveryPolicy
import com.example.rnd_transit_mtl.data.location.LOCATION_FIX_MAX_AGE_MILLIS
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class AndroidLocationSource(private val context: Context) : LocationSource {
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun refresh() { refreshes.tryEmit(Unit) }
    private fun providers(manager: LocationManager) =
        listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { it in manager.allProviders }

    fun permissionLevel(): LocationPermissionLevel = when {
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ->
            LocationPermissionLevel.Precise
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ->
            LocationPermissionLevel.Approximate
        else -> LocationPermissionLevel.Denied
    }

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java)
        return providers(manager)
            .any { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
    }

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
        val currentRequests = mutableListOf<CancellationSignal>()
        val recovery = LocationRecoveryPolicy(SystemClock.elapsedRealtime())
        fun reportIfUnavailable() {
            if (registered.none(manager::isProviderEnabled)) trySend(LocationUpdate.Unavailable)
        }
        val listener = object : LocationListener {
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = reportIfUnavailable()
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onLocationChanged(location: Location) {
                val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
                if (ageNanos !in 0L..LOCATION_FIX_MAX_AGE_MILLIS * 1_000_000L) return
                recovery.recordFix(location.elapsedRealtimeNanos / 1_000_000L, SystemClock.elapsedRealtime())
                // Missing accuracy must not be represented as a perfect zero-metre measurement.
                if (!location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy < 0f) return
                trySend(LocationUpdate.Fix(
                    GeoPoint(location.latitude, location.longitude),
                    location.accuracy.toDouble(),
                    timestampEpochMillis = System.currentTimeMillis() - ageNanos / 1_000_000L,
                    speedMetersPerSecond = location.speed.toDouble()
                        .takeIf { location.hasSpeed() && it.isFinite() && it >= 0.0 },
                ))
            }
        }
        fun requestCurrentReadings() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            for (provider in registered.toList()) runCatching {
                if (manager.isProviderEnabled(provider)) {
                    val cancellation = CancellationSignal()
                    currentRequests.add(cancellation)
                    manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location ->
                        currentRequests.remove(cancellation)
                        if (location != null) listener.onLocationChanged(location)
                    }
                }
            }
        }
        fun registerProviders() {
            // Android keeps these listeners registered when location services are toggled.
            for (provider in providers(manager)) {
                runCatching {
                    // A stationary device still needs fresh fixes, rather than appearing to lose GPS.
                    manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
                    registered.add(provider)
                }
            }
            reportIfUnavailable()
            requestCurrentReadings()
        }
        registerProviders()
        // GO/recenter request a measurement without tearing down continuous GPS subscriptions.
        val refreshJob = launch { refreshes.collect { requestCurrentReadings() } }
        val watchdog = launch {
            while (isActive) {
                delay(1_000)
                if (!hasPermission()) {
                    trySend(LocationUpdate.PermissionDenied)
                    close()
                    break
                }
                if (recovery.retryIfStalled(SystemClock.elapsedRealtime())) {
                    // Replace the stalled registrations in this same shared source.
                    currentRequests.toList().forEach { it.cancel() }
                    currentRequests.clear()
                    manager.removeUpdates(listener)
                    registered.clear()
                    registerProviders()
                }
            }
        }
        awaitClose {
            watchdog.cancel()
            refreshJob.cancel()
            currentRequests.toList().forEach { it.cancel() }
            manager.removeUpdates(listener)
        }
    }
}
