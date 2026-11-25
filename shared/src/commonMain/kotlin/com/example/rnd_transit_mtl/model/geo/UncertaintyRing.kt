package com.example.rnd_transit_mtl.model.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Closed geodesic ring in metres, also valid near the poles and the date line. */
internal fun GeoPoint.uncertaintyRing(radiusMeters: Double): List<GeoPoint> {
    if (!radiusMeters.isFinite() || radiusMeters <= 0.0) return emptyList()
    val angular = (radiusMeters / GeoPoint.EARTH_RADIUS_METERS).coerceAtMost(PI)
    val latitudeRadians = latitude * PI / 180.0
    val longitudeRadians = longitude * PI / 180.0
    return (0..48).map { index ->
        val bearing = (index % 48) * 2.0 * PI / 48
        val lat = asin((sin(latitudeRadians) * cos(angular) + cos(latitudeRadians) * sin(angular) * cos(bearing)).coerceIn(-1.0, 1.0))
        val lon = longitudeRadians + atan2(sin(bearing) * sin(angular) * cos(latitudeRadians), cos(angular) - sin(latitudeRadians) * sin(lat))
        GeoPoint(lat * 180.0 / PI, ((lon * 180.0 / PI + 540.0) % 360.0) - 180.0)
    }
}
