package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.model.TransportRoute
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Supplies sample transit routes and bus numbers from shared resources.
 */
class FakeTransportRouteRepository {
    /**
     * Loads sample routes from the bundled JSON resource.
     *
     * @return Routes with their stable IDs, owning transport IDs, and display labels.
     */
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
