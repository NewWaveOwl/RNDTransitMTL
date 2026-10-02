package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.model.TransportType
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Supplies sample transport types without requiring a database or network.
 */
class FakeTransportTypeRepository {
    /**
     * Loads sample transport types from the bundled JSON resource.
     *
     * @return Transport types in the display order defined by the resource.
     */
    suspend fun getTransportTypes(): List<TransportType> = JsonAssetReader
        .readArray("files/data/transport_types.json")
        .map { element ->
            val item = element.jsonObject
            TransportType(
                id = item.getValue("id").jsonPrimitive.content,
                label = item.getValue("label").jsonPrimitive.content,
                usesRoutes = item.getValue("usesRoutes").jsonPrimitive.boolean
            )
        }
}
