package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

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
        CompositionLocalProvider(
            LocalTransitMap provides { data, modifier -> AndroidTransitMap(data, styleUrl, modifier) },
            content = content
        )
    }
}

@Composable
private fun AndroidTransitMap(data: TransitMapData, styleUrl: String, modifier: Modifier) {
    var attempt by remember(styleUrl) { mutableIntStateOf(0) }
    val trip = data.trip
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
            initialCameraPosition = MontrealCamera
        ) {
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

        Box(modifier) {
            MaplibreMap(
                state = map,
                modifier = Modifier.fillMaxSize(),
                interactions = if (data.interactive) MapInteractions.Standard else MapInteractions.None,
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
                    modifier = Modifier.align(statusAlignment).padding(top = statusOffset).semantics { contentDescription = "Loading map" }
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
