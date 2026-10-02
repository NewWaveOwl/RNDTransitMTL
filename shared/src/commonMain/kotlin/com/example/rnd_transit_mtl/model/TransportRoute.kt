package com.example.rnd_transit_mtl.model

/**
 * Describes one route and its owning transport option.
 *
 * @param id Stable route identifier stored in the selection list.
 * @param transportTypeId Identifier of the TransportType that owns this route.
 * @param label Route name or number displayed to the user.
 */
data class TransportRoute(
    val id: String,
    val transportTypeId: String,
    val label: String
)
