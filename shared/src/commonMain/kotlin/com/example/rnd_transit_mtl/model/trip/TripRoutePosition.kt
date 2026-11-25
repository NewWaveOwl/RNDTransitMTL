package com.example.rnd_transit_mtl.model.trip

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** A measured position projected onto the accepted route, with distance in metres. */
data class TripRoutePosition(val progress: Float, val distanceFromRouteMeters: Double)

/** Follows saved geometry by distance, including turns, rather than extending a straight vector. */
fun Trip.pointAlongRoute(progress: Float): GeoPoint = TripRouteGeometry(this).pointAt(progress)

/** Cached distances avoid rebuilding the route geometry on every estimate frame. */
internal class TripRouteGeometry(private val trip: Trip) {
    private val segments = trip.legs.flatMap { it.geometry.zipWithNext() }
    private val lengths = segments.map { (start, end) -> start.distanceMetersTo(end) }
    private val segmentLegIndices = trip.legs.flatMapIndexed { index, leg -> List(leg.geometry.size - 1) { index } }
    private val segmentStarts = lengths.runningFold(0.0) { sum, length -> sum + length }
    val legLengths = trip.legs.map { leg -> leg.geometry.zipWithNext().sumOf { (a, b) -> a.distanceMetersTo(b) } }
    val totalLength = lengths.sum()

    fun legIndexAt(progress: Float): Int {
        val distance = progress.coerceIn(0f, 1f) * totalLength
        var end = 0.0
        return legLengths.indices.firstOrNull { end += legLengths[it]; distance < end } ?: trip.legs.lastIndex
    }

    /** Display-only matching stays on one real leg and rejects remote or ambiguous jumps. */
    fun matchOnLeg(
        point: GeoPoint,
        legIndex: Int,
        referenceProgress: Float,
        expectedProgress: Float,
        maximumTravelMeters: Double,
        maximumDistanceMeters: Double,
    ): TripRoutePosition? {
        if (totalLength <= 0.0 || !trip.legs[legIndex].isRouted) return null
        val reference = referenceProgress * totalLength
        val expected = expectedProgress * totalLength
        val legEnd = legLengths.take(legIndex + 1).sum()
        val scale = PI / 180.0 * GeoPoint.EARTH_RADIUS_METERS
        val longitudeScale = scale * cos(point.latitude * PI / 180.0)
        val candidates = segments.indices.mapNotNull { index ->
            if (segmentLegIndices[index] != legIndex || lengths[index] <= 0.0) return@mapNotNull null
            val (start, end) = segments[index]
            val x = (start.longitude - point.longitude) * longitudeScale
            val y = (start.latitude - point.latitude) * scale
            val dx = (end.longitude - start.longitude) * longitudeScale
            val dy = (end.latitude - start.latitude) * scale
            val denominator = dx * dx + dy * dy
            val fraction = if (denominator > 0.0) (-(x * dx + y * dy) / denominator).coerceIn(0.0, 1.0) else 0.0
            val distance = (segmentStarts[index] + lengths[index] * fraction).coerceAtMost((legEnd - 0.5).coerceAtLeast(segmentStarts[index]))
            if (abs(distance - reference) > maximumTravelMeters) return@mapNotNull null
            val progress = (distance / totalLength).toFloat().coerceIn(0f, 0.999f)
            val boundedFraction = ((distance - segmentStarts[index]) / lengths[index]).coerceIn(0.0, 1.0)
            val projected = GeoPoint(start.latitude + (end.latitude - start.latitude) * boundedFraction,
                start.longitude + (end.longitude - start.longitude) * boundedFraction)
            val lateral = point.distanceMetersTo(projected)
            if (lateral > maximumDistanceMeters) return@mapNotNull null
            TripRoutePosition(progress, lateral) to (lateral + abs(distance - expected) * 0.25)
        }.sortedBy { it.second }
        val best = candidates.firstOrNull() ?: return null
        val competing = candidates.firstOrNull { abs(it.first.progress - best.first.progress) * totalLength > 30.0 }
        if (competing != null && competing.second - best.second <= 5.0) return null
        return best.first
    }

    fun pointAt(progress: Float): GeoPoint {
        if (totalLength <= 0.0) return trip.origin.point
        var remaining = totalLength * (if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f)
        for (index in segments.indices) {
            val length = lengths[index]
            if (length <= 0.0) continue
            if (remaining <= length) {
                val (start, end) = segments[index]
                val fraction = remaining / length
                return GeoPoint(
                    start.latitude + (end.latitude - start.latitude) * fraction,
                    start.longitude + (end.longitude - start.longitude) * fraction,
                )
            }
            remaining -= length
        }
        return trip.legs.last().end
    }
}

/**
 * Finds the closest route segment without changing the measured position.
 * Near a crossing, equally close segments prefer continuity with the last match.
 * @param point Actual device position in WGS84.
 * @param previousProgress Last matched distance fraction, used only to resolve close ties.
 */
fun Trip.routePosition(point: GeoPoint, previousProgress: Float = 0f): TripRoutePosition? {
    val segments = legs.flatMap { it.geometry.zipWithNext() }
    val lengths = segments.map { (start, end) -> start.distanceMetersTo(end) }
    val total = lengths.sum()
    if (total <= 0.0) return null
    val scale = PI / 180.0 * GeoPoint.EARTH_RADIUS_METERS
    val longitudeScale = scale * cos(point.latitude * PI / 180.0)
    var travelled = 0.0
    var best: TripRoutePosition? = null
    for (index in segments.indices) {
        val (start, end) = segments[index]
        val length = lengths[index]
        if (length <= 0.0) continue
        val x = (start.longitude - point.longitude) * longitudeScale
        val y = (start.latitude - point.latitude) * scale
        val dx = (end.longitude - start.longitude) * longitudeScale
        val dy = (end.latitude - start.latitude) * scale
        val denominator = dx * dx + dy * dy
        val fraction = if (denominator > 0) (-(x * dx + y * dy) / denominator).coerceIn(0.0, 1.0) else 0.0
        val projected = GeoPoint(
            start.latitude + (end.latitude - start.latitude) * fraction,
            start.longitude + (end.longitude - start.longitude) * fraction,
        )
        val candidate = TripRoutePosition(((travelled + length * fraction) / total).toFloat(), point.distanceMetersTo(projected))
        val current = best
        if (current == null || candidate.distanceFromRouteMeters < current.distanceFromRouteMeters - 2.0 ||
            (abs(candidate.distanceFromRouteMeters - current.distanceFromRouteMeters) <= 2.0 &&
                abs(candidate.progress - previousProgress) < abs(current.progress - previousProgress))) {
            best = candidate
        }
        travelled += length
    }
    return best
}
