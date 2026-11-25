package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import android.net.ConnectivityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.data.assets.SampleMapBounds
import com.example.rnd_transit_mtl.data.location.LocalPlannerLocation
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.explorationWaypoints
import com.example.rnd_transit_mtl.navigation.CurrentTripScreenKey
import com.example.rnd_transit_mtl.navigation.LocalNavigator
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.tripLegColor
import kotlin.math.cos
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.imageResource
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.map.AndroidRenderMode
import org.maplibre.compose.map.CameraConstraints
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MapUiOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberImageSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.compose.util.PositionQuad
import org.maplibre.compose.util.PreparedImage
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.map_sample

// Layout settings — dp route widths, camera margins, and zoom limits for the live Android map.
private val RouteOutlineWidth = 13.dp
private val RouteLegWidth = 7.dp
private val RouteTravelledWidth = 2.dp
private val TripFitMaxMargin = 24.dp
private val BrowsingRegionalFitPadding = 24.dp
private const val DefaultMontrealZoom = 12.0
private const val BrowsingMinZoom = 7.0
private const val BrowsingFocusMinZoom = 13.0
private const val ActiveTripRecenterMinZoom = 16.0

// Animation settings — milliseconds for offline fallback check and automatic browsing recenter.
private const val OFFLINE_STYLE_FALLBACK_DELAY_MILLIS = 2_000L
private const val AUTO_CENTER_SETTLE_DELAY_MILLIS = 1_200L
private const val AUTO_CENTER_EASE_DURATION_MILLIS = 1_800

// The shared map moves through Compose during navigation, so its native view must
// respect Compose clipping and opacity when parked.
private val SharedMapUiOptions = MapUiOptions(MapUiOptions.Standard) { renderMode = AndroidRenderMode.Texture }

private val MontrealCamera = CameraPosition(
    center = Position(MontrealCenter.longitude, MontrealCenter.latitude),
    zoom = DefaultMontrealZoom,
)
private val OfflineBounds = with(SampleMapBounds) {
    BoundingBox(west = west, south = south, east = east, north = north)
}
private val OfflineCorners = with(SampleMapBounds) {
    PositionQuad(
        topLeft = Position(west, north),
        topRight = Position(east, north),
        bottomRight = Position(east, south),
        bottomLeft = Position(west, south),
    )
}

/**
 * Supplies the live Android MapLibre renderer and shared planner location session.
 *
 * @param styleUrl MapLibre style URL; can be overridden by the Android host.
 * @param onMapReady Called once the shared map style and viewport have rendered their first frame.
 * @param content Application content rendered with [LocalTransitMap] and [LocalPlannerLocation] provided.
 */
@Composable
@UiComposable
fun ProvideAndroidTransitMap(
    styleUrl: String,
    onMapReady: () -> Unit = {},
    content: @Composable @UiComposable () -> Unit,
) {
    if (LocalInspectionMode.current) {
        content()
    } else {
        val session = rememberAndroidPlannerMapSession()
        val latestMapReady by rememberUpdatedState(onMapReady)
        val tripFlowMap = remember(styleUrl, session) {
            movableContentOf { data: TransitMapData, modifier: Modifier ->
                AndroidTransitMap(data, styleUrl, session, modifier, onFirstFrame = { latestMapReady() })
            }
        }
        var sharedMapData by remember(tripFlowMap) {
            mutableStateOf(TransitMapData(sharedTripFlow = true, browsing = true, interactive = false))
        }
        CompositionLocalProvider(
            LocalSharedTransitMapHost provides { modifier, active ->
                val data = sharedMapData
                tripFlowMap(
                    if (active) data else data.copy(interactive = false, onPick = null, followPosition = false),
                    if (active) modifier else modifier.clearAndSetSemantics {},
                )
            },
            LocalPlannerLocation provides session,
            LocalTransitMap provides { data, modifier ->
                if (data.sharedTripFlow) {
                    val destination = LocalNavigator.current.current
                    if (destination == MainScreenKey || destination is CurrentTripScreenKey) {
                        SideEffect { sharedMapData = data }
                    }
                } else {
                    AndroidTransitMap(data, styleUrl, session, modifier)
                }
            },
            content = content,
        )
    }
}

/**
 * Identifies the style instance displayed by MapLibre.
 *
 * @property offline Whether this attempt uses the bundled map.
 * @property attempt Incremented to load a fresh instance of the same style.
 */
internal data class MapStyleAttempt(val offline: Boolean = false, val attempt: Int = 0) {
    /** Reloads the current style, even when the device is offline. */
    fun retry() = copy(attempt = attempt + 1)

    /** Switches from the bundled style when a network is available.
     * @param hasNetwork Whether Android currently reports an active network.
     */
    fun retryOnline(hasNetwork: Boolean) = if (hasNetwork) MapStyleAttempt(attempt = attempt + 1) else this
}

/** Renders the MapLibre map, route/uncertainty layers, camera controllers, and status overlays. */
@Composable
@UiComposable
private fun AndroidTransitMap(
    data: TransitMapData,
    styleUrl: String,
    session: AndroidPlannerMapSession,
    modifier: Modifier,
    onFirstFrame: (() -> Unit)? = null,
) {
    val connectivity = LocalContext.current.getSystemService(ConnectivityManager::class.java)
    // Let MapLibre try its ambient cache before switching to the bundled image.
    var styleAttempt by remember(styleUrl) { mutableStateOf(MapStyleAttempt()) }
    val offline = styleAttempt.offline
    val trip = data.trip
    val fix = session.fix.takeIf { data.browsing }
    val confirmedPin = data.pin.takeIf { !data.pickerOpen && data.centerOnPin }
    val regionalBounds = remember {
        with(MontrealBrowsingBounds) { BoundingBox(west = west, south = south, east = east, north = north) }
    }
    if (data.pickerOpen) SideEffect { session.autoCenterPending = false }
    val legs = trip?.legs.orEmpty()
    val bounds = remember(legs) { computeRouteBounds(legs) }
    val path = remember(legs) { legs.flatMap { it.geometry.zipWithNext() } }
    val distances = remember(path) { path.map { (from, to) -> from.distanceMetersTo(to) } }
    val routeMarker = data.progress?.let { positionAlongRoute(path, distances, it) }
    val marker = data.userPosition ?: routeMarker.takeIf { data.simulatePosition }
    val browsingPosition = rememberAnimatedMapPosition(fix?.point.takeIf { data.browsing })
    val travelled = remember(path, distances, routeMarker, data.progress) {
        computeTravelledSegments(path, distances, routeMarker, data.progress)
    }

    key(styleUrl, styleAttempt) {
        val savedCamera = remember { session.camera.takeIf { data.browsing } }
        val offlineImage = if (offline) {
            val bitmap = imageResource(Res.drawable.map_sample)
            remember(bitmap) { PreparedImage.fromBitmap(bitmap) }
        } else null
        val map = rememberMapState(
            baseStyle = if (offline) BaseStyle.Empty else BaseStyle.Uri(styleUrl),
            initialCameraPosition = savedCamera ?: MontrealCamera,
        ) {
            if (offlineImage != null) {
                val source = rememberImageSource(OfflineCorners, offlineImage)
                RasterLayer(id = "bundled-montreal-map", source = source)
            }
            val uncertaintyCenter = data.userPosition ?: browsingPosition ?: fix?.point
            val uncertaintyMeters = data.userUncertaintyMeters ?: session.uncertaintyMeters ?: fix?.accuracyMeters
            if (uncertaintyCenter != null && uncertaintyMeters != null && uncertaintyMeters.isFinite() && uncertaintyMeters > 0) {
                AccuracyArea(uncertaintyCenter, uncertaintyMeters)
            }
            if (trip != null) {
                val routeData = remember(legs) { GeoJsonData.JsonString(routeGeoJson(legs)) }
                val route = rememberGeoJsonSource(routeData)
                LineLayer(id = "trip-route-outline", source = route, color = const(Color.White), width = const(RouteOutlineWidth))
                legs.forEachIndexed { index, leg ->
                    val legData = remember(leg) { GeoJsonData.JsonString(routeGeoJson(listOf(leg))) }
                    val legSource = rememberGeoJsonSource(legData)
                    LineLayer(id = "trip-leg-$index", source = legSource, color = const(tripLegColor(index)), width = const(RouteLegWidth))
                }
                if (travelled.isNotEmpty()) {
                    val travelledRoute = rememberGeoJsonSource(GeoJsonData.JsonString(linesGeoJson(travelled)))
                    LineLayer(
                        id = "trip-travelled",
                        source = travelledRoute,
                        color = const(Color.White.copy(alpha = 0.5f)),
                        width = const(RouteTravelledWidth),
                    )
                }
                TripDot("trip-start", trip.origin.point, TransitMain, 7)
            }
        }

        val styleLoadState = map.style.loadState
        if (onFirstFrame != null) {
            LaunchedEffect(map) {
                snapshotFlow { map.style.loadState == StyleLoadState.Ready && map.viewport != null }.first { it }
                onFirstFrame()
            }
        }
        LaunchedEffect(map, styleLoadState) {
            if (offline || styleLoadState == StyleLoadState.Ready) return@LaunchedEffect
            if (styleLoadState is StyleLoadState.Failed) {
                styleAttempt = styleAttempt.copy(offline = true)
            } else if (connectivity.activeNetwork == null) {
                delay(OFFLINE_STYLE_FALLBACK_DELAY_MILLIS)
                if (connectivity.activeNetwork == null && map.style.loadState != StyleLoadState.Ready) {
                    styleAttempt = styleAttempt.copy(offline = true)
                }
            }
        }

        AndroidTripCameraEffects(map = map, data = data, trip = trip, bounds = bounds, marker = marker)
        if (data.browsing) {
            AndroidBrowsingCameraEffects(
                map = map,
                data = data,
                session = session,
                savedCamera = savedCamera,
                confirmedPin = confirmedPin,
                fix = fix,
                offline = offline,
                regionalBounds = regionalBounds,
                styleLoadState = styleLoadState,
            )
        }

        Box(modifier.background(Color(0xFFE9ECE8))) {
            MaplibreMap(
                state = map,
                modifier = Modifier.fillMaxSize(),
                uiOptions = if (data.sharedTripFlow) SharedMapUiOptions else MapUiOptions.Standard,
                cameraConstraints = if (data.browsing) {
                    CameraConstraints {
                        boundingBox = regionalBounds
                        minZoom = BrowsingMinZoom
                    }
                } else {
                    CameraConstraints.Standard
                },
                interactions = buildMapInteractions(data, offline),
                overlay = {
                    val selectedOrigin = data.pin.takeIf { data.pickerOpen || data.centerOnPin }
                    if (browsingPosition != null && browsingPosition != selectedOrigin) {
                        UserPointer(browsingPosition, session.movementHeading.degrees - map.cameraPosition.bearing.toFloat())
                    }
                    data.userPosition?.let {
                        UserPointer(it, session.movementHeading.degrees - map.cameraPosition.bearing.toFloat())
                    }
                    selectedOrigin?.let { OriginMarker(it) }
                    if (trip != null && map.style.loadState == StyleLoadState.Ready) {
                        if (trip.explorationWaypoints.isEmpty()) DestinationMarker(trip.destination.point)
                        else trip.explorationWaypoints.forEach { DestinationMarker(it.point) }
                        if (marker != null && data.userPosition == null && data.progress != null) {
                            val rotation = simulatedPointerRotation(path, distances, marker, data.progress, map.cameraPosition.bearing)
                            UserPointer(marker, rotation)
                        }
                    }
                },
            )

            AndroidMapStatusOverlays(
                hasTrip = trip != null,
                offline = offline,
                styleLoadState = styleLoadState,
                data = data,
                onRetryStyle = { styleAttempt = styleAttempt.retry() },
                onRetryOnline = {
                    styleAttempt = styleAttempt.retryOnline(connectivity.activeNetwork != null)
                },
            )
        }
    }
}

/** Coordinates route-bounds fitting, explicit recentering, and follow-position camera tracking for trips. */
@Composable
private fun AndroidTripCameraEffects(
    map: MapState,
    data: TransitMapData,
    trip: Trip?,
    bounds: BoundingBox?,
    marker: GeoPoint?,
) {
    val latestData by rememberUpdatedState(data)
    var fittedTripId by remember { mutableStateOf<TripId?>(null) }
    LaunchedEffect(map, trip?.id, data.recenterRequest) {
        if (trip == null) {
            fittedTripId = null
            return@LaunchedEffect
        }
        if (bounds == null || data.followPosition || (fittedTripId == trip.id && data.recenterRequest == 0)) {
            return@LaunchedEffect
        }
        snapshotFlow { latestData.cameraInsetsReady }.first { it }
        val viewport = map.awaitViewport()
        val padding = latestData.visibleMapPadding(viewport.size.height)
        val margin = minOf(
            TripFitMaxMargin,
            viewport.size.width / 4,
            (viewport.size.height - padding.top - padding.bottom) / 4,
        )
        fittedTripId = trip.id
        map.animateCameraToBounds(
            bounds,
            CameraFit(
                cameraPadding = DpPadding.Zero,
                fitPadding = DpPadding(margin, padding.top + margin, margin, padding.bottom + margin),
            ),
            CameraAnimation.Ease.Standard,
        )
    }

    val onManualCameraMove by rememberUpdatedState(data.onManualCameraMove)
    LaunchedEffect(map, data.followPosition) {
        if (!data.followPosition) return@LaunchedEffect
        snapshotFlow { map.isCameraMoving && map.cameraMoveReason == CameraMoveReason.Gesture }
            .collect { moving -> if (moving) onManualCameraMove() }
    }

    var handledRecenterRequest by remember(map, trip?.id) { mutableIntStateOf(0) }
    var recenterAnimating by remember(map, trip?.id) { mutableStateOf(false) }
    val latestMarker by rememberUpdatedState(marker)
    // A moving GPS marker must not cancel and restart an explicit recenter animation.
    LaunchedEffect(map, trip?.id, data.recenterRequest, marker != null) {
        val request = data.recenterRequest
        if (request <= 0 || request == handledRecenterRequest || marker == null) return@LaunchedEffect
        snapshotFlow { latestData.cameraInsetsReady }.first { it }
        val viewport = map.awaitViewport()
        val target = latestMarker ?: return@LaunchedEffect
        handledRecenterRequest = request
        recenterAnimating = true
        try {
            map.animateCamera(
                CameraUpdate(
                    center = target.position(),
                    zoom = maxOf(ActiveTripRecenterMinZoom, map.cameraPosition.zoom),
                    padding = latestData.visibleMapPadding(viewport.size.height),
                ),
                CameraAnimation.Ease.Standard,
            )
        } finally {
            recenterAnimating = false
        }
    }

    // Overlay resizing only reveals/hides the map. Keep the last camera padding
    // until the user explicitly recenters, including on subsequent GPS updates.
    LaunchedEffect(map, trip?.id, data.followPosition, marker, recenterAnimating, handledRecenterRequest) {
        if (data.followPosition && marker != null && !recenterAnimating && data.recenterRequest == handledRecenterRequest) {
            map.setCameraPosition(map.cameraPosition.copy(center = marker.position()))
        }
    }
}

/** Coordinates initial regional fit, saved camera persistence, and auto/manual recentering in browsing mode. */
@Composable
private fun AndroidBrowsingCameraEffects(
    map: MapState,
    data: TransitMapData,
    session: AndroidPlannerMapSession,
    savedCamera: CameraPosition?,
    confirmedPin: GeoPoint?,
    fix: com.example.rnd_transit_mtl.data.location.LocationUpdate.Fix?,
    offline: Boolean,
    regionalBounds: BoundingBox,
    styleLoadState: StyleLoadState,
) {
    val latestData by rememberUpdatedState(data)
    val localFix = fix?.takeIf { session.containsPoint(it.point) }
    val latestLocalFix by rememberUpdatedState(localFix)
    val hasLocalFix = localFix != null
    var centeredPin by remember { mutableStateOf(confirmedPin.takeIf { savedCamera != null }) }
    var cameraReady by remember { mutableStateOf(false) }

    LaunchedEffect(map) {
        val viewport = map.awaitViewport()
        if (savedCamera == null && confirmedPin == null && map.cameraMoveReason != CameraMoveReason.Gesture) {
            val fit = if (offline) {
                CameraFit(cameraPadding = latestData.visibleMapPadding(viewport.size.height))
            } else {
                CameraFit(
                    fitPadding = DpPadding(
                        BrowsingRegionalFitPadding,
                        BrowsingRegionalFitPadding,
                        BrowsingRegionalFitPadding,
                        BrowsingRegionalFitPadding,
                    ),
                )
            }
            map.fitCameraToBounds(if (offline) OfflineBounds else regionalBounds, fit)
        }
        cameraReady = true
        snapshotFlow { if (!map.isCameraMoving) map.cameraPosition else null }
            .collect { position -> if (position != null) session.saveCamera(position) }
    }

    LaunchedEffect(map, cameraReady, offline, data.pickerOpen) {
        if (!cameraReady || !offline || !data.pickerOpen) return@LaunchedEffect
        snapshotFlow { latestData.cameraInsetsReady }.first { it }
        val viewport = map.awaitViewport()
        val visible = viewport.visibleBounds
        // Preserve a usable saved view, but bring the bundled image back when it is off screen.
        val overlapsOfflineMap = visible.west < OfflineBounds.east &&
            visible.east > OfflineBounds.west &&
            visible.south < OfflineBounds.north &&
            visible.north > OfflineBounds.south
        if (!overlapsOfflineMap) {
            map.fitCameraToBounds(
                OfflineBounds,
                CameraFit(cameraPadding = latestData.visibleMapPadding(viewport.size.height)),
            )
        }
    }

    LaunchedEffect(map) {
        snapshotFlow { map.cameraMoveReason }.collect { reason ->
            if (reason == CameraMoveReason.Gesture) session.autoCenterPending = false
        }
    }

    LaunchedEffect(map, cameraReady, styleLoadState, hasLocalFix, session.recenter, data.pickerOpen, data.interactive, confirmedPin) {
        if (data.pickerOpen) {
            centeredPin = null
            return@LaunchedEffect
        }
        if (!cameraReady || styleLoadState != StyleLoadState.Ready || !data.interactive) return@LaunchedEffect
        val request = session.recenter
        val recenterOnFix = hasLocalFix && request != 0
        val targetPin = confirmedPin?.takeIf { it != centeredPin && !recenterOnFix }
        val centeringOnFix = recenterOnFix || (hasLocalFix && session.autoCenterPending)
        if (targetPin == null && !centeringOnFix) return@LaunchedEffect

        snapshotFlow { latestData.cameraInsetsReady }.first { it }
        val viewport = map.awaitViewport()
        val automatic = targetPin == null && request == 0
        if (automatic && savedCamera != null) delay(AUTO_CENTER_SETTLE_DELAY_MILLIS)

        val point = targetPin ?: latestLocalFix?.point ?: return@LaunchedEffect
        if (automatic && (!session.autoCenterPending || map.cameraMoveReason == CameraMoveReason.Gesture)) {
            return@LaunchedEffect
        }
        centeredPin = confirmedPin
        session.autoCenterPending = false
        val camera = map.cameraPosition
        val center = GeoPoint(camera.center.latitude, camera.center.longitude)
        if (automatic && savedCamera != null &&
            center.distanceMetersTo(point) <= maxOf(50.0, latestLocalFix?.accuracyMeters ?: 0.0)
        ) {
            return@LaunchedEffect
        }

        map.animateCamera(
            CameraUpdate(
                center = point.position(),
                zoom = if (automatic && savedCamera != null) camera.zoom else maxOf(BrowsingFocusMinZoom, camera.zoom),
                padding = latestData.visibleMapPadding(viewport.size.height),
            ),
            if (automatic) CameraAnimation.Ease { duration = AUTO_CENTER_EASE_DURATION_MILLIS.milliseconds }
            else CameraAnimation.Ease.Standard,
        )
        if (session.recenter == request) session.recenter = 0
    }
}

/** Builds MapLibre click/long-click handlers respecting interactivity and offline sample bounds. */
private fun buildMapInteractions(data: TransitMapData, offline: Boolean): MapInteractions =
    if (data.interactive) {
        MapInteractions {
            if (data.onPick != null) {
                callbacks {
                    // Register a separate long press so releasing a hold cannot select a point.
                    longClick { onEvent { ClickResult.Pass } }
                    click {
                        onEvent { event ->
                            val point = event.position
                            if (point != null && point.longitude in -180.0..180.0) {
                                val geoPoint = GeoPoint(point.latitude, point.longitude)
                                if (!offline || geoPoint in SampleMapBounds) data.onPick(geoPoint)
                                ClickResult.Consume
                            } else {
                                ClickResult.Pass
                            }
                        }
                    }
                }
            }
        }
    } else {
        MapInteractions.None
    }

/** Computes the bounding box enclosing all coordinates across [legs], or null if empty. */
private fun computeRouteBounds(legs: List<com.example.rnd_transit_mtl.model.trip.RouteLeg>): BoundingBox? {
    val points = legs.flatMap { it.geometry }
    if (points.isEmpty()) return null
    return BoundingBox(
        west = points.minOf { it.longitude },
        south = points.minOf { it.latitude },
        east = points.maxOf { it.longitude },
        north = points.maxOf { it.latitude },
    )
}

/** Computes the travelled portion of route polylines up to [progress]. */
private fun computeTravelledSegments(
    path: List<Pair<GeoPoint, GeoPoint>>,
    distances: List<Double>,
    routeMarker: GeoPoint?,
    progress: Float?,
): List<List<GeoPoint>> {
    val bounded = progress?.coerceIn(0f, 1f) ?: 0f
    var remaining = distances.sum() * bounded
    return path.zip(distances).mapNotNull { (segment, distance) ->
        if (remaining <= 0 || distance <= 0) return@mapNotNull null
        remaining -= distance
        listOf(segment.first, if (remaining >= 0) segment.second else routeMarker ?: segment.second)
    }
}

/** Computes screen-space pointer rotation for simulated playback along the route geometry. */
private fun simulatedPointerRotation(
    path: List<Pair<GeoPoint, GeoPoint>>,
    distances: List<Double>,
    marker: GeoPoint,
    progress: Float,
    cameraBearing: Double,
): Float {
    val ahead = positionAlongRoute(path, distances, (progress + 0.001f).coerceAtMost(1f))
    val to = ahead ?: marker
    val from = if (to == marker) {
        positionAlongRoute(path, distances, (progress - 0.001f).coerceAtLeast(0f)) ?: marker
    } else {
        marker
    }
    val direction = Offset(
        ((to.longitude - from.longitude) * cos(Math.toRadians(marker.latitude))).toFloat(),
        (from.latitude - to.latitude).toFloat(),
    )
    return tripPointerRotation(Offset.Zero, direction) - cameraBearing.toFloat()
}
