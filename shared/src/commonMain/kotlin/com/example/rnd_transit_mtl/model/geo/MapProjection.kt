package com.example.rnd_transit_mtl.model.geo

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

/**
 * A position on a flat map image, as fractions of its width and height.
 *
 * x = 0 is the left edge and x = 1 the right edge; y = 0 is the top edge
 * and y = 1 the bottom edge, matching TripPoint. Values outside 0..1 mean
 * the position lies beyond the image.
 *
 * @property x Horizontal fraction of the image width.
 * @property y Vertical fraction of the image height.
 */
data class MapPosition(val x: Double, val y: Double)

/**
 * Converts between real coordinates and positions on a north-up map image
 * that covers [bounds].
 *
 * Uses Web Mercator, the projection of OpenStreetMap and most web map tiles,
 * so a screenshot of those maps lines up exactly: horizontal position is
 * linear in longitude, and vertical position is linear in Mercator y.
 *
 * @property bounds Real-world rectangle shown by the image.
 */
class MapProjection(val bounds: GeoBounds) {
    private val northMercatorY = mercatorY(bounds.north)
    private val southMercatorY = mercatorY(bounds.south)

    /**
     * Finds where a real position appears on the image.
     *
     * @param point Position to place; it may lie outside [bounds].
     * @return Image fractions; outside 0..1 when [point] is not in [bounds].
     */
    fun toMapPosition(point: GeoPoint): MapPosition = MapPosition(
        x = (point.longitude - bounds.west) / (bounds.east - bounds.west),
        y = (northMercatorY - mercatorY(point.latitude)) / (northMercatorY - southMercatorY)
    )

    /**
     * Finds the real position shown at a place on the image.
     *
     * @param position Image fractions, normally within 0..1.
     * @return Matching coordinates. Fails if the position is so far outside
     * the image that it has no valid latitude or longitude.
     */
    fun toGeoPoint(position: MapPosition): GeoPoint = GeoPoint(
        latitude = latitudeFromMercatorY(
            northMercatorY - position.y * (northMercatorY - southMercatorY)
        ),
        longitude = bounds.west + position.x * (bounds.east - bounds.west)
    )

    private companion object {
        fun mercatorY(latitude: Double): Double =
            ln(tan(PI / 4 + latitude.toRadians() / 2))

        fun latitudeFromMercatorY(mercatorY: Double): Double =
            (2 * atan(exp(mercatorY)) - PI / 2).toDegrees()

        fun Double.toRadians(): Double = this * PI / 180

        fun Double.toDegrees(): Double = this * 180 / PI
    }
}
