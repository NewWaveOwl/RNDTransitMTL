package com.example.rnd_transit_mtl.model.transport
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId

/**
 * Describes one route and its owning transport option.
 *
 * @param id Stable route identifier stored in the selection list.
 * @param transportTypeId Identifier of the TransportType that owns this route.
 * @param label Route name or number displayed to the user.
 */
data class TransportRoute(
    val id: RouteId,
    val transportTypeId: TransportId,
    val label: String
)
