package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import android.net.ConnectivityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import androidx.compose.ui.geometry.Offset
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.overlay.MapOverlayScope
import rnd_transit_mtl.shared.generated.resources.Res
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import rnd_transit_mtl.shared.generated.resources.map_user_pointer_marker
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.SideEffect
import com.example.rnd_transit_mtl.data.location.LocalPlannerLocation
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.map.CameraConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.data.assets.SampleMapBounds
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.imageResource
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.RasterLayer
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
import rnd_transit_mtl.shared.generated.resources.map_sample
import rnd_transit_mtl.shared.generated.resources.rnd_origin_marker

private val MontrealCamera = CameraPosition(
    center = Position(MontrealCenter.longitude, MontrealCenter.latitude),
    zoom = 12.0
)
private val OfflineBounds = with(SampleMapBounds) {
    BoundingBox(west = west, south = south, east = east, north = north)
}
private val OfflineCorners = with(SampleMapBounds) {
    PositionQuad(
        topLeft = Position(west, north),
        topRight = Position(east, north),
        bottomRight = Position(east, south),
        bottomLeft = Position(west, south)
    )
}

/** Supplies the live Android renderer. The URL can be overridden by the Android host. */
@Composable
@UiComposable
fun ProvideAndroidTransitMap(
    styleUrl: String,
    content: @Composable @UiComposable () -> Unit
) {
    if (LocalInspectionMode.current) {
        content()
    } else {
        val session = rememberAndroidPlannerMapSession()
        val tripFlowMap = remember(styleUrl, session) {
            movableContentOf { data: TransitMapData, modifier: Modifier ->
                AndroidTransitMap(data, styleUrl, session, modifier)
            }
        }
        CompositionLocalProvider(
            LocalPlannerLocation provides session,
            LocalTransitMap provides { data, modifier ->
                if (data.sharedTripFlow) tripFlowMap(data, modifier)
                else AndroidTransitMap(data, styleUrl, session, modifier)
            },
            content = content
        )
    }
}

@Composable
@UiComposable
private fun AndroidTransitMap(data: TransitMapData, styleUrl: String, session: AndroidPlannerMapSession, modifier: Modifier) {
    val connectivity = LocalContext.current.getSystemService(ConnectivityManager::class.java)
    var offline by remember(styleUrl) { mutableStateOf(connectivity.activeNetwork == null) }
    var attempt by remember(styleUrl) { mutableIntStateOf(0) }
    val trip = data.trip
    val fix = session.fix.takeIf { data.browsing }
    val confirmedPin = data.pin.takeUnless { data.pickerOpen }
    val region = MontrealBrowsingBounds
    val regionalBounds = BoundingBox(west = region.west, south = region.south, east = region.east, north = region.north)
    if (data.pickerOpen) SideEffect { session.autoCenterPending = false }
    val legs = trip?.legs.orEmpty()
    val bounds = remember(legs) {
        val points = legs.flatMap { it.geometry }
        if (points.isEmpty()) null else BoundingBox(
            west = points.minOf { it.longitude },
            south = points.minOf { it.latitude },
            east = points.maxOf { it.longitude },
            north = points.maxOf { it.latitude }
        )
    }
    val path = remember(legs) { legs.flatMap { it.geometry.zipWithNext() } }
    val distances = remember(path) { path.map { (from, to) -> from.distanceMetersTo(to) } }
    val marker = data.progress?.let { positionAlongRoute(path, distances, it) }
    val travelled = remember(path, distances, marker, data.progress) {
        val progress = data.progress?.coerceIn(0f, 1f) ?: 0f
        var remaining = distances.sum() * progress
        path.zip(distances).mapNotNull { (segment, distance) ->
            if (remaining <= 0 || distance <= 0) return@mapNotNull null
            remaining -= distance
            listOf(segment.first, if (remaining >= 0) segment.second else marker ?: segment.second)
        }
    }

    key(styleUrl, attempt, offline) {
        val offlineImage = if (offline) {
            val bitmap = imageResource(Res.drawable.map_sample)
            remember(bitmap) { PreparedImage.fromBitmap(bitmap) }
        } else null
        val map = rememberMapState(
            baseStyle = if (offline) BaseStyle.Empty else BaseStyle.Uri(styleUrl),
            initialCameraPosition = if (data.browsing && !offline) session.camera ?: MontrealCamera else MontrealCamera
        ) {
            if (offlineImage != null) {
                val source = rememberImageSource(OfflineCorners, offlineImage)
                RasterLayer(id = "bundled-montreal-map", source = source)
            }
            if (fix != null && fix.accuracyMeters > 0) {
                AccuracyArea(fix.point, fix.accuracyMeters)
            }
            if (trip != null) {
                val routeData = remember(legs) { GeoJsonData.JsonString(routeGeoJson(legs)) }
                val route = rememberGeoJsonSource(routeData)
                LineLayer(id = "trip-route-outline", source = route, color = const(Color.White), width = const(13.dp))
                LineLayer(id = "trip-route", source = route, color = const(TransitMain), width = const(7.dp))
                if (travelled.isNotEmpty()) {
                    val travelledRoute = rememberGeoJsonSource(GeoJsonData.JsonString(linesGeoJson(travelled)))
                    LineLayer(id = "trip-travelled", source = travelledRoute, color = const(Color(0xFFFFBA00)), width = const(7.dp))
                }
                TripDot("trip-start", trip.origin.point, TransitMain, 7)
            }
        }

        val styleFailed = map.style.loadState is StyleLoadState.Failed
        LaunchedEffect(styleFailed) {
            if (styleFailed && !offline) offline = true
        }
        val latestData by rememberUpdatedState(data)
        var fittedTripId by remember { mutableStateOf<TripId?>(null) }
        LaunchedEffect(map, trip?.id, data.recenterRequest) {
            if (trip == null) {
                fittedTripId = null
                return@LaunchedEffect
            }
            if (bounds == null || data.followPosition ||
                (fittedTripId == trip.id && data.recenterRequest == 0)
            ) return@LaunchedEffect

            snapshotFlow { latestData.cameraInsetsReady }.first { it }
            val viewport = map.awaitViewport()
            val padding = latestData.visibleMapPadding(viewport.size.height)
            val margin = minOf(24.dp, viewport.size.width / 4, (viewport.size.height - padding.top - padding.bottom) / 4)
            fittedTripId = trip.id
            map.animateCameraToBounds(
                bounds,
                CameraFit(
                    cameraPadding = DpPadding.Zero,
                    fitPadding = DpPadding(margin, padding.top + margin, margin, padding.bottom + margin)
                ),
                CameraAnimation.Ease.Standard
            )
        }

        val onManualCameraMove by rememberUpdatedState(data.onManualCameraMove)
        LaunchedEffect(map, data.followPosition) {
            if (!data.followPosition) return@LaunchedEffect
            snapshotFlow { map.isCameraMoving && map.cameraMoveReason == CameraMoveReason.Gesture }
                .collect { moving -> if (moving) onManualCameraMove() }
        }
        LaunchedEffect(map, data.followPosition, marker, data.recenterRequest, data.topInset, data.bottomInset) {
            if (data.followPosition && marker != null) {
                val viewport = map.awaitViewport()
                map.setCameraPosition(
                    map.cameraPosition.copy(
                        center = marker.position(),
                        padding = data.visibleMapPadding(viewport.size.height)
                    )
                )
            }
        }

        if (data.browsing) {
            val localFix = fix?.point?.takeIf {
                session.containsPoint(it) && (!offline || it in SampleMapBounds)
            }
            val latestLocalFix by rememberUpdatedState(localFix)
            val hasLocalFix = localFix != null
            var centeredPin by remember {
                mutableStateOf(confirmedPin.takeIf { !offline && session.camera != null })
            }
            LaunchedEffect(map, offline && data.pickerOpen) {
                val centeringOnFix = hasLocalFix && (session.autoCenterPending || session.recenter != 0)
                if (offline && (data.pickerOpen || (confirmedPin == null && !centeringOnFix))) {
                    snapshotFlow { latestData.cameraInsetsReady }.first { it }
                    val viewport = map.awaitViewport()
                    map.fitCameraToBounds(
                        OfflineBounds,
                        CameraFit(cameraPadding = latestData.visibleMapPadding(viewport.size.height))
                    )
                }
            }
            LaunchedEffect(map) {
                if (offline) return@LaunchedEffect
                val saved = session.camera
                val centeringOnFix = hasLocalFix &&
                    (session.recenter != 0 || (session.autoCenterPending && confirmedPin == null))
                if (!centeringOnFix && confirmedPin == centeredPin) {
                    if (saved == null) {
                        map.fitCameraToBounds(regionalBounds, CameraFit(fitPadding = DpPadding(24.dp, 24.dp, 24.dp, 24.dp)))
                    } else if (saved != map.cameraPosition) {
                        map.animateCamera(
                            CameraUpdate(
                                center = saved.center,
                                zoom = saved.zoom,
                                bearing = saved.bearing,
                                pitch = saved.pitch,
                                padding = saved.padding
                            ),
                            CameraAnimation.Ease.Standard
                        )
                    }
                }
                snapshotFlow {
                    if (map.viewport != null && !map.isCameraMoving) map.cameraPosition else null
                }.collect { position -> if (position != null) session.saveCamera(position) }
            }
            LaunchedEffect(map) {
                snapshotFlow { map.cameraMoveReason }.collect { reason ->
                    if (reason == CameraMoveReason.Gesture) session.autoCenterPending = false
                }
            }
            LaunchedEffect(map, hasLocalFix, session.recenter, data.pickerOpen, data.interactive, confirmedPin) {
                if (data.pickerOpen) {
                    centeredPin = null
                    return@LaunchedEffect
                }
                if (!data.interactive) return@LaunchedEffect
                val request = session.recenter
                val recenterOnFix = hasLocalFix && request != 0
                val targetPin = confirmedPin?.takeIf { it != centeredPin && !recenterOnFix }
                val centeringOnFix = recenterOnFix ||
                    (hasLocalFix && session.autoCenterPending && confirmedPin == null)
                if (targetPin == null && !centeringOnFix) return@LaunchedEffect

                snapshotFlow { latestData.cameraInsetsReady }.first { it }
                val viewport = map.awaitViewport()
                val point = targetPin ?: latestLocalFix ?: return@LaunchedEffect
                if (targetPin == null && request == 0 && !session.autoCenterPending) return@LaunchedEffect
                centeredPin = confirmedPin
                session.autoCenterPending = false
                map.animateCamera(
                    CameraUpdate(
                        center = point.position(),
                        zoom = maxOf(13.0, map.cameraPosition.zoom),
                        padding = latestData.visibleMapPadding(viewport.size.height)
                    ),
                    CameraAnimation.Ease.Standard
                )
                if (session.recenter == request) session.recenter = 0
            }
        }

        Box(modifier.background(Color(0xFFE9ECE8))) {
            MaplibreMap(
                state = map,
                modifier = Modifier.fillMaxSize(),
                cameraConstraints = if (data.browsing) CameraConstraints {
                    boundingBox = if (offline && data.pickerOpen) OfflineBounds else regionalBounds
                    minZoom = 7.0
                } else CameraConstraints.Standard,
                interactions = if (data.interactive) MapInteractions {
                    if (data.onPick != null) callbacks {
                        // Register a separate long press so releasing a hold cannot select a point.
                        longClick { onEvent { ClickResult.Pass } }
                        click {
                            onEvent { event ->
                                val point = event.position
                                if (point != null && point.longitude in -180.0..180.0) {
                                    val geoPoint = GeoPoint(point.latitude, point.longitude)
                                    if (!offline || geoPoint in SampleMapBounds) data.onPick(geoPoint)
                                    ClickResult.Consume
                                } else ClickResult.Pass
                            }
                        }
                    }
                } else MapInteractions.None,
                overlay = {
                    if (fix != null && (fix.point != data.pin || !data.pickerOpen)) UserPointer(fix.point)
                    data.pin?.let { point ->
                        if (data.pickerOpen) UserPointer(point)
                        else if (fix?.point != point) OriginMarker(point)
                    }
                    if (trip != null && map.style.loadState == StyleLoadState.Ready) {
                        DestinationMarker(trip.destination.point)
                        if (marker != null) {
                            val ahead = positionAlongRoute(path, distances, (data.progress + 0.001f).coerceAtMost(1f))
                            val to = ahead ?: marker
                            val from = if (to == marker) {
                                positionAlongRoute(path, distances, (data.progress - 0.001f).coerceAtLeast(0f)) ?: marker
                            } else marker
                            val direction = Offset(
                                ((to.longitude - from.longitude) *
                                    kotlin.math.cos(Math.toRadians(marker.latitude))).toFloat(),
                                (from.latitude - to.latitude).toFloat()
                            )
                            val rotation = tripPointerRotation(Offset.Zero, direction) -
                                map.cameraPosition.bearing.toFloat()
                            UserPointer(marker, rotation)
                        }
                    }
                }
            )

            if (!offline && (!data.browsing || data.pickerOpen || !data.interactive)) {
                val uriHandler = LocalUriHandler.current
                Surface(
                    modifier = Modifier
                        .align(if (trip == null) Alignment.TopStart else Alignment.BottomStart)
                        .padding(start = 8.dp, top = 8.dp, bottom = data.bottomInset + 4.dp)
                        .clickable { uriHandler.openUri("https://openfreemap.org/quick_start/") },
                    color = TransitWhite,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "OpenFreeMap",
                        color = TransitComplementary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            val statusAlignment = if (trip == null) Alignment.TopCenter else Alignment.Center
            val statusOffset = if (trip == null) 64.dp else 0.dp
            when (map.style.loadState) {
                StyleLoadState.Ready -> if (offline) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = data.bottomInset + 4.dp),
                        color = TransitWhite,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = {
                                if (connectivity.activeNetwork != null) {
                                    offline = false
                                    attempt++
                                }
                            }) {
                                Text("Offline map · Retry", color = TransitComplementary)
                            }
                            Text(
                                "© OpenStreetMap contributors",
                                color = TransitComplementary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp)
                            )
                        }
                    }
                }
                is StyleLoadState.Failed -> Surface(
                    modifier = Modifier.align(statusAlignment).padding(top = statusOffset),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Couldn’t load the map")
                        TextButton(onClick = {
                            offline = connectivity.activeNetwork == null
                            attempt++
                        }) { Text("Retry") }
                    }
                }
                else -> CircularProgressIndicator(
                    modifier = Modifier.align(statusAlignment).padding(top = statusOffset).size(24.dp).semantics { contentDescription = "Loading map" }
                )
            }
        }
    }
}

@Composable
private fun TripDot(id: String, point: GeoPoint, color: Color, radius: Int) {
    val source = rememberGeoJsonSource(
        GeoJsonData.JsonString("""{"type":"Point","coordinates":${point.coordinates()}}""")
    )
    CircleLayer(
        id = id,
        source = source,
        color = const(color),
        radius = const(radius.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp)
    )
}

@Composable
private fun AccuracyArea(point: GeoPoint, meters: Double) {
    val ring = (0..48).joinToString(",") { index ->
        val angle = 2.0 * kotlin.math.PI * (index % 48) / 48
        val latitude = point.latitude + meters * kotlin.math.sin(angle) / 111_320.0
        val longitude = point.longitude + meters * kotlin.math.cos(angle) /
            (111_320.0 * kotlin.math.cos(Math.toRadians(point.latitude)).coerceAtLeast(0.01))
        "[$longitude,$latitude]"
    }
    val source = rememberGeoJsonSource(
        GeoJsonData.JsonString("""{"type":"Polygon","coordinates":[[$ring]]}""")
    )
    org.maplibre.compose.layers.FillLayer(id = "device-accuracy", source = source, color = const(Color(0x33FFBA00)))
}

private fun TransitMapData.visibleMapPadding(height: Dp): DpPadding {
    val maxInset = (height - 1.dp).coerceAtLeast(0.dp)
    val top = topInset.coerceIn(0.dp, maxInset)
    return DpPadding(top = top, bottom = bottomInset.coerceIn(0.dp, maxInset - top))
}

@Composable
private fun MapOverlayScope.UserPointer(point: GeoPoint, rotation: Float = 0f) {
    val pointer = painterResource(Res.drawable.map_user_pointer_marker)
    Canvas(Modifier.placedAt(point.position()).size(36.dp)) {
        drawDirectedTripPointer(pointer, center, size.width, rotation)
    }
}

@Composable
private fun MapOverlayScope.OriginMarker(point: GeoPoint) {
    Image(
        painter = painterResource(Res.drawable.rnd_origin_marker),
        contentDescription = "Starting point",
        modifier = Modifier.placedAt(point.position()).size(36.dp)
    )
}

@Composable
private fun MapOverlayScope.DestinationMarker(point: GeoPoint) {
    Canvas(Modifier.placedAt(point.position()).size(36.dp)) {
        drawCircle(Color(0xFFFFBA00))
        val flag = Path().apply {
            moveTo(12.dp.toPx(), 26.dp.toPx())
            lineTo(12.dp.toPx(), 11.dp.toPx())
            lineTo(18.dp.toPx(), 11.dp.toPx())
            lineTo(19.dp.toPx(), 13.dp.toPx())
            lineTo(25.dp.toPx(), 13.dp.toPx())
            lineTo(25.dp.toPx(), 20.dp.toPx())
            lineTo(19.dp.toPx(), 20.dp.toPx())
            lineTo(18.dp.toPx(), 18.dp.toPx())
            lineTo(12.dp.toPx(), 18.dp.toPx())
        }
        drawPath(flag, TransitComplementary, style = Stroke(2.dp.toPx()))
    }
}

private fun GeoPoint.position(): Position = Position(longitude, latitude)

private fun GeoPoint.coordinates(): String = "[$longitude,$latitude]"

private fun routeGeoJson(legs: List<RouteLeg>): String = linesGeoJson(legs.map { it.geometry })

private fun linesGeoJson(geometry: List<List<GeoPoint>>): String {
    val lines = geometry.joinToString(",") { line ->
        line.joinToString(",", prefix = "[", postfix = "]") { it.coordinates() }
    }
    return """{"type":"MultiLineString","coordinates":[$lines]}"""
}

private fun positionAlongRoute(
    segments: List<Pair<GeoPoint, GeoPoint>>,
    distances: List<Double>,
    progress: Float
): GeoPoint? {
    if (segments.isEmpty()) return null
    val fraction = progress.coerceIn(0f, 1f)
    if (fraction == 0f) return segments.first().first
    if (fraction == 1f) return segments.last().second
    var remaining = distances.sum() * fraction
    for ((index, segment) in segments.withIndex()) {
        val distance = distances[index]
        if (distance > 0.0 && remaining <= distance) {
            val (start, end) = segment
            val t = remaining / distance
            return GeoPoint(
                start.latitude + (end.latitude - start.latitude) * t,
                start.longitude + (end.longitude - start.longitude) * t
            )
        }
        remaining -= distance
    }
    return segments.last().second
}
