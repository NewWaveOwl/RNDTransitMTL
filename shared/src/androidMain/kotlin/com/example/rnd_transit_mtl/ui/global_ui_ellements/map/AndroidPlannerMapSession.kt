package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.data.location.PlannerLocation
import com.example.rnd_transit_mtl.model.geo.GeoBounds
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlinx.coroutines.delay
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

// Provisional regional bounds until the official buffered territory can be bundled.
internal val MontrealBrowsingBounds = GeoBounds(north = 46.11, south = 45.10, west = -74.54, east = -72.90)

internal class AndroidPlannerMapSession(context: Context) : PlannerLocation {
    private val preferences = context.getSharedPreferences("planner_map", Context.MODE_PRIVATE)
    var camera: CameraPosition? = restoreCamera()
        private set
    var autoCenterPending = true
    var recenter by mutableIntStateOf(0)
    override var fix by mutableStateOf<LocationUpdate.Fix?>(null)
    override val containsPoint: (GeoPoint) -> Boolean = { point ->
        point.latitude in MontrealBrowsingBounds.south..MontrealBrowsingBounds.north &&
            point.longitude in MontrealBrowsingBounds.west..MontrealBrowsingBounds.east
    }

    var pendingResult: ((LocationUpdate.Fix?) -> Unit)? by mutableStateOf(null)
    lateinit var askPermission: () -> Unit
    lateinit var hasPermission: () -> Boolean
    var permitted by mutableStateOf(false)
    var locationAttempt by mutableIntStateOf(0)
        private set

    override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) {
        autoCenterPending = false
        pendingResult = onResult ?: { result -> if (result != null) recenter++ }
        permitted = hasPermission()
        if (permitted) {
            val currentFix = fix
            if (currentFix != null) complete(currentFix) else locationAttempt++
        } else {
            askPermission()
        }
    }

    fun complete(result: LocationUpdate.Fix?) {
        val callback = pendingResult
        pendingResult = null
        callback?.invoke(result)
    }

    fun saveCamera(position: CameraPosition) {
        camera = position
        preferences.edit()
            .putString("longitude", position.center.longitude.toString())
            .putString("latitude", position.center.latitude.toString())
            .putString("zoom", position.zoom.toString())
            .apply()
    }

    private fun restoreCamera(): CameraPosition? {
        val longitude = preferences.getString("longitude", null)?.toDoubleOrNull() ?: return null
        val latitude = preferences.getString("latitude", null)?.toDoubleOrNull() ?: return null
        val zoom = preferences.getString("zoom", null)?.toDoubleOrNull() ?: return null
        if (longitude !in MontrealBrowsingBounds.west..MontrealBrowsingBounds.east ||
            latitude !in MontrealBrowsingBounds.south..MontrealBrowsingBounds.north ||
            zoom !in 0.0..20.0
        ) return null
        return CameraPosition(center = Position(longitude, latitude), zoom = zoom)
    }
}

@Composable
internal fun rememberAndroidPlannerMapSession(): AndroidPlannerMapSession {
    val context = LocalContext.current.applicationContext
    val session = remember(context) { AndroidPlannerMapSession(context) }
    val source = remember(context) { AndroidLocationSource(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        session.permitted = source.hasPermission()
        if (!session.permitted) session.complete(null)
    }
    session.hasPermission = source::hasPermission
    session.askPermission = {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    var started by remember { mutableStateOf(false) }
    LifecycleStartEffect(session) {
        session.permitted = source.hasPermission()
        started = true
        onStopOrDispose {
            started = false
            session.fix = null
        }
    }
    LaunchedEffect(session.permitted, started, session.locationAttempt) {
        if (session.permitted && started) source.updates.collect { update ->
            session.fix = update as? LocationUpdate.Fix
            session.complete(session.fix)
        }
    }
    val pending = session.pendingResult
    LaunchedEffect(pending, session.permitted, started) {
        if (pending != null && session.permitted && started) {
            delay(15_000)
            session.complete(null)
        }
    }
    return session
}
