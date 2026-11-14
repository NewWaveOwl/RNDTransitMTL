package com.example.rnd_transit_mtl.state.trip

import androidx.compose.runtime.saveable.Saver

/** Keeps each account's trips while the active navigation session changes. */
class SessionTripsStores private constructor(
    private val stores: MutableMap<String, TripsStore>,
) {
    constructor() : this(mutableMapOf())

    fun forIdentity(identity: String): TripsStore =
        stores.getOrPut(identity) { TripsStore() }

    fun clearAccountStores(except: String? = null) {
        stores.keys.removeAll { it.startsWith("account:") && it != except }
    }

    companion object {
        val Saver: Saver<SessionTripsStores, Map<String, String>> = Saver(
            save = { holder -> holder.stores.mapValues { it.value.toSavedStateJson() } },
            restore = { saved ->
                SessionTripsStores(saved.mapNotNull { (identity, json) ->
                    TripsStore.fromSavedStateJson(json)?.let { identity to it }
                }.toMap().toMutableMap())
            },
        )
    }
}
