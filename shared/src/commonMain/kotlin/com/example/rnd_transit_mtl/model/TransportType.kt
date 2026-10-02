package com.example.rnd_transit_mtl.model

/** Describes one transport option and whether the user must choose a route for it. */
data class TransportType(
    val id: String,
    val label: String,
    val usesRoutes: Boolean
)
