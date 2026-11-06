package com.example.rnd_transit_mtl.model.geo

import kotlinx.serialization.Serializable

/**
 * A north-up rectangle of the Earth, in WGS 84 degrees.
 *
 * Rectangles crossing the 180th meridian are not supported. Latitudes are
 * limited to the Web Mercator range used by OpenStreetMap tiles, because
 * MapProjection cannot represent anything closer to the poles.
 *
 * @property north Latitude of the top edge.
 * @property south Latitude of the bottom edge; less than north.
 * @property west Longitude of the left edge.
 * @property east Longitude of the right edge; greater than west.
 */
@Serializable
data class GeoBounds(
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double
) {
    init {
        require(north.isFinite() && south.isFinite() && west.isFinite() && east.isFinite()) {
            "Bounds must be finite."
        }
        require(south >= -MAX_MERCATOR_LATITUDE && north <= MAX_MERCATOR_LATITUDE) {
            "Latitudes must be within the Web Mercator range."
        }
        require(west >= -180.0 && east <= 180.0) {
            "Longitudes must be between -180 and 180."
        }
        require(north > south) { "North must be greater than south." }
        require(east > west) { "East must be greater than west." }
    }

    /**
     * Reports whether a point lies inside or on the edge of these bounds.
     *
     * @param point Position to test.
     */
    operator fun contains(point: GeoPoint): Boolean =
        point.latitude in south..north && point.longitude in west..east

    companion object {
        /** Highest latitude Web Mercator can show; tiles are cut off there. */
        const val MAX_MERCATOR_LATITUDE = 85.05112878
    }
}
