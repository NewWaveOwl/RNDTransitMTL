package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.data.location.LocationJumpFilter
import com.example.rnd_transit_mtl.data.location.LocationAccuracyMonitor
import com.example.rnd_transit_mtl.data.location.PlannerLocation
import com.example.rnd_transit_mtl.model.geo.GeoBounds
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.filterNotNull
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

internal val MontrealCenter = GeoPoint(latitude = 45.5019, longitude = -73.5673)

// Farthest CMM vertex from MontrealCenter in Québec's SDA 1:20,000 layer 4 (V2026-10).
// https://servicescarto.mern.gouv.qc.ca/pes/rest/services/Territoire/SDA_WMS/MapServer/4
private val farthestCmmPoint = GeoPoint(latitude = 45.98474970, longitude = -73.18629259)
private val montrealServiceRadiusMeters = MontrealCenter.distanceMetersTo(farthestCmmPoint) + 5_000.0

internal val MontrealBrowsingBounds = run {
    val angularRadius = montrealServiceRadiusMeters / GeoPoint.EARTH_RADIUS_METERS
    val latitudeOffset = angularRadius * 180.0 / PI
    val centerLatitude = MontrealCenter.latitude * PI / 180.0
    val longitudeOffset = asin(sin(angularRadius) / cos(centerLatitude)) * 180.0 / PI
    GeoBounds(
        north = MontrealCenter.latitude + latitudeOffset,
        south = MontrealCenter.latitude - latitudeOffset,
        west = MontrealCenter.longitude - longitudeOffset,
        east = MontrealCenter.longitude + longitudeOffset
    )
}

internal class AndroidPlannerMapSession(private val context: Context) : PlannerLocation {
    private val preferences = context.getSharedPreferences("planner_map", Context.MODE_PRIVATE)
    var camera: CameraPosition? = restoreCamera()
        private set
    var autoCenterPending = true
    var recenter by mutableIntStateOf(0)
    override var fix by mutableStateOf<LocationUpdate.Fix?>(null)
    override val jumpFilter = LocationJumpFilter()
    override val accuracyMonitor = LocationAccuracyMonitor(
        dismissedAtMillis = preferences.getLong("accuracy_prompt_dismissed_at", -1L).takeIf { it >= 0L },
        onDismiss = { preferences.edit().putLong("accuracy_prompt_dismissed_at", it).apply() },
    )
    override fun openAppLocationSettings(): Boolean = openSettings(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
    override fun openDeviceLocationSettings(): Boolean = openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    private fun openSettings(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
    var uncertaintyMeters by mutableStateOf<Double?>(null)
        private set
    override val containsPoint: (GeoPoint) -> Boolean = { point ->
        MontrealCenter.distanceMetersTo(point) <= montrealServiceRadiusMeters
    }

    var pendingResult: ((LocationUpdate.Fix?) -> Unit)? by mutableStateOf(null)
    lateinit var askPermission: () -> Unit
    lateinit var hasPermission: () -> Boolean
    var permitted by mutableStateOf(false)
    var permissionChecked by mutableStateOf(false)
    var latestLocationUpdate by mutableStateOf<LocationUpdate?>(null)
    override val currentUpdate: LocationUpdate?
        get() = when {
            permissionChecked && !permitted -> LocationUpdate.PermissionDenied
            accuracyMonitor.state.locationEnabled == false -> LocationUpdate.Unavailable
            else -> latestLocationUpdate
        }
    override val automaticOriginUpdates = snapshotFlow {
        when {
            !permissionChecked -> null
            !permitted -> LocationUpdate.PermissionDenied
            latestLocationUpdate is LocationUpdate.Fix -> fix
            else -> latestLocationUpdate
        }
    }.filterNotNull()
    override val trackingUpdates = snapshotFlow {
        when {
            !permissionChecked -> null
            !permitted -> LocationUpdate.PermissionDenied
            else -> latestLocationUpdate
        }
    }.filterNotNull()
    var locationAttempt by mutableIntStateOf(0)
        private set

    private var requestedAtMillis = 0L
    val movementHeading = com.example.rnd_transit_mtl.data.location.MovementHeading()

    fun receive(update: LocationUpdate) {
        if (update is LocationUpdate.Fix) {
            val result = jumpFilter.evaluate(update, System.currentTimeMillis()) ?: return
            accuracyMonitor.receive(update)
            fix = result.trustedFix
            uncertaintyMeters = fix?.let { retained ->
                maxOf(update.accuracyMeters, retained.accuracyMeters) + retained.point.distanceMetersTo(update.point)
            }
            if (result.accepted) {
                result.trustedFix?.let { movementHeading.receive(it) }
                if ((update.timestampEpochMillis ?: 0L) >= requestedAtMillis) complete(result.trustedFix)
            }
        } else if (update == LocationUpdate.PermissionDenied) {
            complete(null)
        }
        // A temporary provider outage must not cancel a pending refresh before GPS recovers.
        // Fresh but suspicious readings still count as provider activity, never as progress.
        latestLocationUpdate = update
    }

    override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) {
        autoCenterPending = false
        requestedAtMillis = System.currentTimeMillis()
        pendingResult = onResult ?: { result ->
            if (result != null && containsPoint(result.point)) recenter++
        }
        permitted = hasPermission()
        if (permitted) {
            locationAttempt++
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
        if (camera?.center == position.center && camera?.zoom == position.zoom) return
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
    val refreshAccess = {
        session.permitted = source.hasPermission()
        session.permissionChecked = true
        session.accuracyMonitor.updateAccess(source.permissionLevel(), source.isLocationEnabled())
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshAccess()
        if (!session.permitted) session.complete(null)
    }
    session.hasPermission = source::hasPermission
    session.askPermission = {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    var started by remember { mutableStateOf(false) }
    LifecycleStartEffect(session) {
        refreshAccess()
        started = true
        onStopOrDispose {
            started = false
            session.latestLocationUpdate = null
            session.accuracyMonitor.pause()
        }
    }
    LaunchedEffect(started) {
        if (started) while (isActive) {
            refreshAccess()
            delay(1_000)
        }
    }
    LaunchedEffect(session.permitted, session.accuracyMonitor.state.permission, started, session.locationAttempt) {
        if (session.permitted && started) source.updates.collect { update ->
            session.receive(update)
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
