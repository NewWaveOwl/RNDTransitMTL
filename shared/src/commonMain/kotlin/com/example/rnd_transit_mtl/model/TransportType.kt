package com.example.rnd_transit_mtl.model

/**
 * Describes a selectable transport option.
 *
 * @param id Stable transport identifier referenced by route records and selections.
 * @param label Transport name displayed in the interface.
 * @param usesRoutes Whether this transport exposes routes that the user can select.
 */
data class TransportType(
    val id: String,
    val label: String,
    val usesRoutes: Boolean
)
