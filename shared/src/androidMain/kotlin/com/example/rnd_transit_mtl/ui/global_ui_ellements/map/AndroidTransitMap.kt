package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

private val MontrealCamera = CameraPosition(center = Position(-73.5673, 45.5019), zoom = 12.0)

/** Supplies the live Android renderer. The URL can be overridden by the Android host. */
@Composable
fun ProvideAndroidTransitMap(
    styleUrl: String,
    content: @Composable () -> Unit
) {
    if (LocalInspectionMode.current) {
        content()
    } else {
        val session = rememberAndroidPlannerMapSession()
        val browsingMap = remember(styleUrl, session) {
            movableContentOf { data: TransitMapData, modifier: Modifier ->
                AndroidTransitMap(data, styleUrl, session, modifier)
            }
        }
        CompositionLocalProvider(
            LocalPlannerLocation provides session,
            LocalTransitMap provides { data, modifier ->
                if (data.browsing) browsingMap(data, modifier)
                else AndroidTransitMap(data, styleUrl, session, modifier)
            },
            content = content
        )
    }
}

@Composable
private fun AndroidTransitMap(data: TransitMapData, styleUrl: String, session: AndroidPlannerMapSession, modifier: Modifier) {
    var attempt by remember(styleUrl) { mutableIntStateOf(0) }
    val trip = data.trip
    val fix = session.fix.takeIf { data.browsing }
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

    key(styleUrl, attempt, trip?.id) {
        val map = rememberMapState(
            baseStyle = BaseStyle.Uri(styleUrl),
            initialCameraPosition = if (data.browsing) session.camera ?: MontrealCamera else MontrealCamera
        ) {
            if (fix != null) {
                if (fix.accuracyMeters > 0) AccuracyArea(fix.point, fix.accuracyMeters)
                TripDot("device-location", fix.point, Color(0xFF1976D2), 7)
            }
            data.pin?.let { TripDot("origin-draft", it, Color(0xFFED7100), 10) }
            if (trip != null) {
                val routeData = remember(legs) { GeoJsonData.JsonString(routeGeoJson(legs)) }
                val route = rememberGeoJsonSource(routeData)
                LineLayer(id = "trip-route-outline", source = route, color = const(Color.White), width = const(8.dp))
                LineLayer(id = "trip-route", source = route, color = const(Color(0xFF5943C1)), width = const(5.dp))
                TripDot("trip-start", trip.origin.point, Color(0xFF16744A), 7)
                TripDot("trip-destination", trip.destination.point, Color(0xFFB32436), 7)
                legs.dropLast(1).forEachIndexed { index, leg ->
                    key(index) { TripDot("trip-stop-$index", leg.end, Color(0xFF5943C1), 6) }
                }
                if (marker != null) TripDot("trip-position", marker, Color(0xFFED7100), 9)
            }
        }

        LaunchedEffect(map, bounds, data.topInset, data.bottomInset, data.recenterRequest) {
            if (data.recenterRequest > 0) {
                map.setCameraPosition(
                    if (marker == null) MontrealCamera else map.cameraPosition.copy(
                        center = Position(marker.longitude, marker.latitude),
                        padding = DpPadding(top = data.topInset)
                    )
                )
            } else if (bounds != null) {
                map.fitCameraToBounds(
                    bounds,
                    CameraFit(
                        fitPadding = DpPadding(
                            left = 32.dp,
                            top = data.topInset + 24.dp,
                            right = 32.dp,
                            bottom = data.bottomInset + 24.dp
                        )
                    )
                )
            }
        }

        if (data.browsing) {
            LaunchedEffect(map) {
                if (session.camera == null) {
                    map.fitCameraToBounds(regionalBounds, CameraFit(fitPadding = DpPadding(24.dp, 24.dp, 24.dp, 24.dp)))
                }
                snapshotFlow { map.cameraMoveReason }.collect { reason ->
                    if (reason == CameraMoveReason.Gesture) session.autoCenterPending = false
                }
            }
            LaunchedEffect(map) {
                snapshotFlow {
                    if (map.viewport != null && !map.isCameraMoving) map.cameraPosition else null
                }.collect { position -> if (position != null) session.saveCamera(position) }
            }
            LaunchedEffect(map, fix, session.recenter, data.pickerOpen) {
                val point = fix?.point ?: return@LaunchedEffect
                if (!session.containsPoint(point)) return@LaunchedEffect
                val explicit = session.recenter != 0
                if (!data.pickerOpen && (explicit ||
                        (session.autoCenterPending && map.cameraMoveReason != CameraMoveReason.Gesture))) {
                    session.autoCenterPending = false
                    session.recenter = 0
                    map.setCameraPosition(CameraPosition(center = Position(point.longitude, point.latitude), zoom = 13.0))
                }
            }
        }

        Box(modifier.background(Color(0xFFE9ECE8))) {
            MaplibreMap(
                state = map,
                modifier = Modifier.fillMaxSize(),
                cameraConstraints = if (data.browsing) CameraConstraints {
                    boundingBox = regionalBounds
                    minZoom = 7.0
                } else CameraConstraints.Standard,
                interactions = if (data.interactive) MapInteractions {
                    callbacks {
                        click {
                            onEvent { event ->
                                val point = event.position
                                val onPick = data.onPick
                                if (onPick != null && point != null && point.longitude in -180.0..180.0) {
                                    onPick(GeoPoint(point.latitude, point.longitude))
                                    ClickResult.Consume
                                } else ClickResult.Pass
                            }
                        }
                    }
                } else MapInteractions.None,
                overlay = {}
            )

            val uriHandler = LocalUriHandler.current
            Surface(
                modifier = Modifier
                    .align(if (trip == null) Alignment.TopStart else Alignment.BottomStart)
                    .padding(start = 8.dp, top = 8.dp, bottom = data.bottomInset + 8.dp),
                shape = MaterialTheme.shapes.small
            ) {
                TextButton(onClick = { uriHandler.openUri("https://openfreemap.org/quick_start/") }) {
                    Text("OpenFreeMap · © OpenMapTiles · © OpenStreetMap", style = MaterialTheme.typography.labelSmall)
                }
            }

            val statusAlignment = if (trip == null) Alignment.TopCenter else Alignment.Center
            val statusOffset = if (trip == null) 64.dp else 0.dp
            when (map.style.loadState) {
                StyleLoadState.Ready -> Unit
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
                        TextButton(onClick = { attempt++ }) { Text("Retry") }
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
    org.maplibre.compose.layers.FillLayer(id = "device-accuracy", source = source, color = const(Color(0x331976D2)))
}

private fun GeoPoint.coordinates(): String = "[$longitude,$latitude]"

private fun routeGeoJson(legs: List<RouteLeg>): String {
    val lines = legs.joinToString(",") { leg ->
        leg.geometry.joinToString(",", prefix = "[", postfix = "]") { it.coordinates() }
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
