package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.model.TransportType
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Loads canned transport types from shared JSON resources instead of a database. */
class FakeTransportTypeRepository {
    /** Returns transport types in the JSON file's display order. */
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
