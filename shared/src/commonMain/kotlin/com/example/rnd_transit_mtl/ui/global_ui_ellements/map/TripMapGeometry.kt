package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.rnd_transit_mtl.data.assets.toSampleMapPoint
import com.example.rnd_transit_mtl.data.generation.TripGenerationBounds
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.View.current_trip.CurrentTripContent

/** Actual bundled map_sample PNG dimensions, used if a painter has no intrinsic size. */
internal val bundledMapSize = Size(1510f, 746f)

internal fun validMapSourceSize(intrinsicSize: Size): Size =
    if (intrinsicSize.width.isFinite() && intrinsicSize.height.isFinite() &&
        intrinsicSize.width > 0f && intrinsicSize.height > 0f
    ) intrinsicSize else bundledMapSize

/**
 * Center-aligned uniform Crop matches the planner's Image(ContentScale.Crop).
 * Fit is used for static details, with room around edge-point markers.
 */
internal fun mapImageRect(
    viewport: Size,
    source: Size,
    crop: Boolean,
    markerInset: Float = 0f
): Rect {
    val width = (viewport.width - markerInset * 2f).coerceAtLeast(1f)
    val height = (viewport.height - markerInset * 2f).coerceAtLeast(1f)
    val scale = if (crop) {
        maxOf(viewport.width / source.width, viewport.height / source.height)
    } else {
        minOf(width / source.width, height / source.height)
    }
    val imageWidth = source.width * scale
    val imageHeight = source.height * scale
    return Rect(
        left = (viewport.width - imageWidth) / 2f,
        top = (viewport.height - imageHeight) / 2f,
        right = (viewport.width + imageWidth) / 2f,
        bottom = (viewport.height + imageHeight) / 2f
    )
}

internal fun TripPoint.toMapPosition(rect: Rect): Offset =
    Offset(rect.left + x * rect.width, rect.top + y * rect.height)

/**
 * One leg as MockTripMap draws it.
 *
 * @property points Leg geometry on the PNG in travel order; at least two points.
 * @property isRouted Whether the geometry came from a routing service. Unrouted
 * legs are drawn dashed so a straight-line fallback never looks like a real route.
 */
data class TripMapLeg(val points: List<TripPoint>, val isRouted: Boolean) {
    init {
        require(points.size >= 2) { "A map leg needs at least a start and an end point." }
    }
}

/** The stored legs of a trip on map_sample.png, for MockTripMap. */
fun Trip.toSampleMapLegs(): List<TripMapLeg> = legs.map { leg ->
    TripMapLeg(leg.geometry.map { it.toSampleMapPoint("Route") }, leg.isRouted)
}

/**
 * Joins legs into one line, dropping each shared joint so it appears once.
 *
 * @param legs Leg points in travel order; each leg starts where the previous ends.
 */
internal fun routeLine(legs: List<List<Offset>>): List<Offset> =
    legs.flatMapIndexed { index, points -> if (index == 0) points else points.drop(1) }

/**
 * Splits whole-route progress into how far along each leg has been travelled.
 *
 * Legs before the current position are 1, legs after it are 0, and the
 * current leg gets its own fraction, all measured by drawn length.
 *
 * @param legs Leg points in travel order.
 * @param progress Fraction of the whole route, bounded to 0..1.
 */
internal fun legProgressFractions(legs: List<List<Offset>>, progress: Float): List<Float> {
    val lengths = legs.map { points ->
        points.zipWithNext().sumOf { (from, to) -> (to - from).getDistance().toDouble() }
    }
    val total = lengths.sum()
    if (total <= 0.0) return legs.map { boundedTripProgress(progress) }

    var remaining = total * boundedTripProgress(progress)
    return lengths.map { length ->
        val fraction = if (length <= 0.0) {
            if (remaining > 0.0) 1f else 0f
        } else {
            (remaining / length).coerceIn(0.0, 1.0).toFloat()
        }
        remaining = (remaining - length).coerceAtLeast(0.0)
        fraction
    }
}

/**
 * A position part of the way along a polyline, by length.
 *
 * @property point Position on the line.
 * @property direction Direction of travel there; zero for a line without length.
 * @property travelled Points from the start up to and including [point].
 */
internal data class PolylinePosition(
    val point: Offset,
    val direction: Offset,
    val travelled: List<Offset>
)

/**
 * Finds the position at a fraction of a polyline's total length, so a pointer
 * follows turns instead of cutting straight across them.
 *
 * @param points Line in travel order; at least one point.
 * @param progress Fraction of the length, bounded to 0..1.
 */
internal fun positionAlongPolyline(points: List<Offset>, progress: Float): PolylinePosition {
    require(points.isNotEmpty()) { "A polyline needs at least one point." }
    val segments = points.zipWithNext()
    val total = segments.sumOf { (from, to) -> (to - from).getDistance().toDouble() }
    if (segments.isEmpty() || total <= 0.0) {
        return PolylinePosition(points.first(), Offset.Zero, listOf(points.first()))
    }

    var remaining = total * boundedTripProgress(progress)
    val travelled = mutableListOf(points.first())
    for ((from, to) in segments) {
        val length = (to - from).getDistance().toDouble()
        if (length == 0.0) continue
        if (remaining <= length) {
            val point = from + (to - from) * (remaining / length).toFloat()
            travelled += point
            return PolylinePosition(point, to - from, travelled)
        }
        remaining -= length
        travelled += to
    }
    // Rounding can leave a sliver past the end; stop on the last real segment.
    val (from, to) = segments.last { (a, b) -> a != b }
    return PolylinePosition(points.last(), to - from, travelled)
}

/**
 * New endpoints are generated in the visible crop below the title/zoom controls
 * and above the progress footer.
 * CurrentTripContent reserves at most 45% for that footer. Safe generation
 * margins are applied inside this area by MockTripGenerator.
 */
internal fun plannerTripBounds(
    viewport: Size,
    source: Size,
    titleHeight: Float
): TripGenerationBounds {
    val rect = mapImageRect(viewport, source, crop = true)
    val usableBottom = viewport.height * 0.55f
    val usableTop = titleHeight.coerceIn(0f, usableBottom * 0.6f)
    return TripGenerationBounds(
        left = ((0f - rect.left) / rect.width).coerceIn(0f, 1f),
        right = ((viewport.width - rect.left) / rect.width).coerceIn(0f, 1f),
        top = ((usableTop - rect.top) / rect.height).coerceIn(0f, 1f),
        bottom = ((usableBottom - rect.top) / rect.height).coerceIn(0f, 1f)
    )
}
