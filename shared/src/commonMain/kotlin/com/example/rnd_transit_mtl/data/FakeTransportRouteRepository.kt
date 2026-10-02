package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.model.TransportRoute
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Loads canned routes and bus numbers from shared JSON resources. */
class FakeTransportRouteRepository {
    /** Returns every route with the stable ID stored in the JSON file. */
    suspend fun getTransportRoutes(): List<TransportRoute> = JsonAssetReader
        .readArray("files/data/transport_routes.json")
        .map { element ->
            val item = element.jsonObject
            TransportRoute(
                id = item.getValue("id").jsonPrimitive.content,
                transportTypeId = item.getValue("transportTypeId").jsonPrimitive.content,
                label = item.getValue("label").jsonPrimitive.content
            )
        }
}
