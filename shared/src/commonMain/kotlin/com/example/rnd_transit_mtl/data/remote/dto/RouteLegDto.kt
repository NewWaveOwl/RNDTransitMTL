package com.example.rnd_transit_mtl.data.remote.dto

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GeoJSON LineString, the line format returned by OSRM and GraphHopper and
 * stored by PostGIS.
 *
 * @property type Always "LineString" for valid data. It has no default value
 * because the JSON encoder omits defaults, and GeoJSON requires the field.
 * @property coordinates Points as [longitude, latitude]; GeoJSON puts longitude first.
 */
@Serializable
data class LineStringDto(
    val type: String,
    val coordinates: List<List<Double>>
) {
    companion object {
        const val LINE_STRING_TYPE = "LineString"
    }
}

/** Backend form of a RouteLeg. */
@Serializable
data class RouteLegDto(
    @SerialName("transport_id") val transportId: String,
    @SerialName("route_id") val routeId: String? = null,
    val geometry: LineStringDto,
    @SerialName("distance_meters") val distanceMeters: Double,
    @SerialName("duration_seconds") val durationSeconds: Long,
    @SerialName("is_routed") val isRouted: Boolean
)

/** Converts a leg into its backend form. */
fun RouteLeg.toDto(): RouteLegDto = RouteLegDto(
    transportId = transportId.value,
    routeId = routeId?.value,
    geometry = LineStringDto(
        type = LineStringDto.LINE_STRING_TYPE,
        coordinates = geometry.map { listOf(it.longitude, it.latitude) }
    ),
    distanceMeters = distanceMeters,
    durationSeconds = durationSeconds,
    isRouted = isRouted
)

/**
 * Converts a backend row into a RouteLeg.
 *
 * @return The leg, or null when the geometry is not a LineString of
 * [longitude, latitude] pairs or the row breaks a RouteLeg rule.
 */
fun RouteLegDto.toDomainOrNull(): RouteLeg? = runCatchingInvalid {
    require(geometry.type == LineStringDto.LINE_STRING_TYPE) { "Geometry must be a LineString." }
    RouteLeg(
        transportId = TransportId(transportId),
        routeId = routeId?.let(::RouteId),
        geometry = geometry.coordinates.map { position ->
            require(position.size >= 2) { "Each position needs a longitude and a latitude." }
            GeoPoint(latitude = position[1], longitude = position[0])
        },
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        isRouted = isRouted
    )
}
