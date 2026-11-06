package com.example.rnd_transit_mtl.model.trip

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlinx.serialization.Serializable

/**
 * Kind of OpenStreetMap element a place comes from.
 *
 * OSM numbers each kind separately, so node 5 and way 5 are different places.
 */
@Serializable
enum class OsmElementType { Node, Way, Relation }

/**
 * Identifies the OpenStreetMap element a place was found from, such as a
 * Nominatim search result. Both parts are needed to look the element up again.
 *
 * @property type Kind of element.
 * @property id Positive element number within its kind.
 */
@Serializable
data class OsmReference(val type: OsmElementType, val id: Long) {
    init {
        require(id > 0) { "OpenStreetMap IDs are positive." }
    }
}

/**
 * A named real-world location, such as a trip origin or destination.
 *
 * @property label Name shown to the user.
 * @property point Real coordinates of the place.
 * @property osmReference Source element when the place came from OpenStreetMap;
 * null for places picked on the map or typed in.
 */
@Serializable
data class Place(
    val label: String,
    val point: GeoPoint,
    val osmReference: OsmReference? = null
) {
    init {
        require(label.isNotBlank()) { "A place must have a label." }
    }
}
