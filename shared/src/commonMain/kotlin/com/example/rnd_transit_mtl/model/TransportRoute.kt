package com.example.rnd_transit_mtl.model

/** Describes one selectable route and the transport type that owns it. */
data class TransportRoute(
    val id: String,
    val transportTypeId: String,
    val label: String
)
