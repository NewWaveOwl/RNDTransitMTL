package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.geo.uncertaintyRing
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.overlay.MapOverlayScope
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.Position
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ellipse_starting
import rnd_transit_mtl.shared.generated.resources.map_user_pointer_marker

// Layout settings — dp sizes and stroke widths for Android MapLibre overlays and GeoJSON layers.
private val UserPointerSize = 36.dp
private val OriginMarkerSize = 16.dp
private val DestinationMarkerSize = 36.dp
private val DestinationFlagStrokeWidth = 2.dp
private val TripDotStrokeWidth = 2.dp
private val AccuracyRingOutlineWidth = 1.5.dp

/**
 * Renders a circular MapLibre dot marker layer for a geographic coordinate.
 *
 * @param id Unique MapLibre layer identifier for the circle layer.
 * @param point Geographic coordinate in WGS84 decimal degrees (`latitude`, `longitude`), serialized as GeoJSON `[longitude, latitude]`.
 * @param color Fill color of the circle marker.
 * @param radius Circle radius in dp (rendered with a white [TripDotStrokeWidth] outline).
 */
@Composable
internal fun TripDot(
    id: String,
    point: GeoPoint,
    color: Color,
    radius: Int,
) {
    val source = rememberGeoJsonSource(
        GeoJsonData.JsonString("""{"type":"Point","coordinates":${point.coordinates()}}"""),
    )
    CircleLayer(
        id = id,
        source = source,
        color = const(color),
        radius = const(radius.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(TripDotStrokeWidth),
    )
}

/**
 * Draws the translucent yellow GPS uncertainty ring polygon around [point].
 *
 * @param point Centre coordinate in WGS84 decimal degrees (`latitude`, `longitude`).
 * @param meters Horizontal GPS uncertainty radius in metres; callers pass finite positive values.
 */
@Composable
internal fun AccuracyArea(point: GeoPoint, meters: Double) {
    val ring = remember(point, meters) {
        point.uncertaintyRing(meters).joinToString(",") { "[${it.longitude},${it.latitude}]" }
    }
    val source = rememberGeoJsonSource(
        GeoJsonData.JsonString("""{"type":"Polygon","coordinates":[[$ring]]}"""),
    )
    FillLayer(id = "device-accuracy", source = source, color = const(Color(0x33FFBA00)))
    LineLayer(
        id = "device-accuracy-outline",
        source = source,
        color = const(Color(0x88FFBA00)),
        width = const(AccuracyRingOutlineWidth),
    )
}

/**
 * Draws the directional orange/blue user pointer overlay at [point].
 *
 * @receiver MapLibre overlay scope positioning composable markers over the map viewport.
 * @param point User coordinate in WGS84 decimal degrees (`latitude`, `longitude`).
 * @param rotation Clockwise screen-space heading rotation in degrees relative to the top of the viewport (`0f` points up).
 */
@Composable
internal fun MapOverlayScope.UserPointer(point: GeoPoint, rotation: Float = 0f) {
    val pointer = painterResource(Res.drawable.map_user_pointer_marker)
    Canvas(Modifier.placedAt(point.position()).zIndex(1f).size(UserPointerSize)) {
        drawDirectedTripPointer(pointer, center, size.width, rotation)
    }
}

/**
 * Draws the starting-point pin overlay at [point].
 *
 * @receiver MapLibre overlay scope positioning composable markers over the map viewport.
 * @param point Starting-point coordinate in WGS84 decimal degrees (`latitude`, `longitude`).
 */
@Composable
internal fun MapOverlayScope.OriginMarker(point: GeoPoint) {
    Image(
        painter = painterResource(Res.drawable.ellipse_starting),
        contentDescription = "Starting point",
        modifier = Modifier.placedAt(point.position()).size(OriginMarkerSize),
    )
}

/**
 * Draws the yellow destination flag marker overlay at [point].
 *
 * @receiver MapLibre overlay scope positioning composable markers over the map viewport.
 * @param point Destination or waypoint coordinate in WGS84 decimal degrees (`latitude`, `longitude`).
 */
@Composable
internal fun MapOverlayScope.DestinationMarker(point: GeoPoint) {
    Canvas(Modifier.placedAt(point.position()).size(DestinationMarkerSize)) {
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
        drawPath(flag, TransitComplementary, style = Stroke(DestinationFlagStrokeWidth.toPx()))
    }
}

/**
 * Computes clamped top and bottom camera padding so insets never collapse the viewport [height].
 *
 * @receiver Map presentation configuration supplying requested `topInset` and `bottomInset` in dp.
 * @param height Total MapLibre viewport height in dp.
 * @return Clamped [DpPadding] in dp preserving at least `1.dp` of unobstructed height when [height] allows.
 */
internal fun TransitMapData.visibleMapPadding(height: Dp): DpPadding {
    val maxInset = (height - 1.dp).coerceAtLeast(0.dp)
    val top = topInset.coerceIn(0.dp, maxInset)
    return DpPadding(top = top, bottom = bottomInset.coerceIn(0.dp, maxInset - top))
}

/**
 * Converts a [GeoPoint] to a MapLibre Spatial-K [Position].
 *
 * @receiver Geographic coordinate in WGS84 decimal degrees (`latitude`, `longitude`).
 * @return Spatial-K [Position] ordered `(longitude, latitude)` in decimal degrees.
 */
internal fun GeoPoint.position(): Position = Position(longitude, latitude)

/**
 * Formats a [GeoPoint] as a RFC 7946 GeoJSON coordinate pair.
 *
 * @receiver Geographic coordinate in WGS84 decimal degrees (`latitude`, `longitude`).
 * @return GeoJSON coordinate array literal `"[longitude,latitude]"` in decimal degrees.
 */
internal fun GeoPoint.coordinates(): String = "[$longitude,$latitude]"

/**
 * Serializes the polylines of [legs] into a GeoJSON `MultiLineString`.
 *
 * @param legs Ordered route legs whose WGS84 [GeoPoint] geometries (`latitude`, `longitude`) are rendered.
 * @return RFC 7946 GeoJSON `MultiLineString` object string with `[longitude,latitude]` coordinate pairs.
 */
internal fun routeGeoJson(legs: List<RouteLeg>): String = linesGeoJson(legs.map { it.geometry })

/**
 * Serializes multiple coordinate polylines into a GeoJSON `MultiLineString`.
 *
 * @param geometry Polylines of WGS84 [GeoPoint] coordinates (`latitude`, `longitude`).
 * @return RFC 7946 GeoJSON `MultiLineString` object string with `[longitude,latitude]` coordinate pairs.
 */
internal fun linesGeoJson(geometry: List<List<GeoPoint>>): String {
    val lines = geometry.joinToString(",") { line ->
        line.joinToString(",", prefix = "[", postfix = "]") { it.coordinates() }
    }
    return """{"type":"MultiLineString","coordinates":[$lines]}"""
}

/**
 * Interpolates a geographic position along route [segments] at normalized [progress] (`0f..1f`).
 *
 * @param segments Consecutive WGS84 [GeoPoint] pairs (`latitude`, `longitude`) forming the route geometry.
 * @param distances Precomputed segment lengths in metres matching [segments] by index.
 * @param progress Normalized fraction along the total route length, clamped to `0f..1f`.
 * @return Interpolated WGS84 [GeoPoint] in decimal degrees, or null when [segments] is empty.
 */
internal fun positionAlongRoute(
    segments: List<Pair<GeoPoint, GeoPoint>>,
    distances: List<Double>,
    progress: Float,
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
                start.longitude + (end.longitude - start.longitude) * t,
            )
        }
        remaining -= distance
    }
    return segments.last().second
}
