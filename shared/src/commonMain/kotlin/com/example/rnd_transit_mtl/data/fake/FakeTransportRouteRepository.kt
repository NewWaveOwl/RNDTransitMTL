package com.example.rnd_transit_mtl.data.fake

import com.example.rnd_transit_mtl.model.transport.TransportRoute
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.example.rnd_transit_mtl.data.assets.JsonAssetReader

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
