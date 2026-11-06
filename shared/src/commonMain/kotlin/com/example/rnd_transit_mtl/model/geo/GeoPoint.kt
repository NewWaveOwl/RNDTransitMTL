package com.example.rnd_transit_mtl.model.geo

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A real-world position in WGS 84 degrees, the coordinate system used by
 * OpenStreetMap, GPS, and routing services.
 *
 * @property latitude Degrees north of the equator, from -90 to 90.
 * @property longitude Degrees east of Greenwich, from -180 to 180.
 */
@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be between -90 and 90."
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be between -180 and 180."
        }
    }

    /**
     * Straight-line ("as the crow flies") distance over the Earth's surface,
     * using the haversine formula. It is not a street or transit distance.
     *
     * @param other Second position.
     * @return Distance in meters; never negative.
     */
    fun distanceMetersTo(other: GeoPoint): Double {
        val latitudeChange = (other.latitude - latitude).toRadians()
        val longitudeChange = (other.longitude - longitude).toRadians()
        val a = sin(latitudeChange / 2).pow(2) +
            cos(latitude.toRadians()) * cos(other.latitude.toRadians()) * sin(longitudeChange / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private companion object {
        /** Mean Earth radius used by the haversine formula. */
        const val EARTH_RADIUS_METERS = 6_371_008.8

        fun Double.toRadians(): Double = this * PI / 180
    }
}
