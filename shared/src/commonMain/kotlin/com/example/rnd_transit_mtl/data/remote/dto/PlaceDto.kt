package com.example.rnd_transit_mtl.data.remote.dto

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.trip.OsmElementType
import com.example.rnd_transit_mtl.model.trip.OsmReference
import com.example.rnd_transit_mtl.model.trip.Place
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs are the JSON shapes exchanged with the backend (Supabase/Postgres).
 * They use the database's snake_case names and plain strings, and perform no
 * validation. Convert them to models at the repository boundary so screens and
 * ViewModels never see them. toDomainOrNull() returns null for a row that
 * breaks a model rule, letting the repository skip or report it instead of crashing.
 */

/**
 * Backend form of a Place.
 *
 * @property osmType "node", "way" or "relation"; null with osmId when the place
 * did not come from OpenStreetMap.
 */
@Serializable
data class PlaceDto(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_id") val osmId: Long? = null
)

/** Converts a place into its backend form. */
fun Place.toDto(): PlaceDto = PlaceDto(
    label = label,
    latitude = point.latitude,
    longitude = point.longitude,
    osmType = osmReference?.type?.name?.lowercase(),
    osmId = osmReference?.id
)

/**
 * Converts a backend row into a Place.
 *
 * @return The place, or null when the row breaks a Place rule, names an unknown
 * OSM type, or has only one of osmType and osmId.
 */
fun PlaceDto.toDomainOrNull(): Place? = runCatchingInvalid {
    val reference = when {
        osmType == null && osmId == null -> null
        osmType != null && osmId != null -> OsmReference(osmElementTypeOf(osmType), osmId)
        else -> throw IllegalArgumentException("osm_type and osm_id must be set together.")
    }
    Place(label, GeoPoint(latitude, longitude), reference)
}

private fun osmElementTypeOf(text: String): OsmElementType =
    OsmElementType.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown OSM element type: $text")

/** Runs a conversion, turning model-rule failures into null. */
internal inline fun <T> runCatchingInvalid(convert: () -> T): T? =
    try {
        convert()
    } catch (_: IllegalArgumentException) {
        null
    }
