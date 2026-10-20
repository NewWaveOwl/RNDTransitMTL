package com.example.rnd_transit_mtl.state

import androidx.compose.runtime.compositionLocalOf

/**
 * Shares the App-owned TripsStore with descendant screens.
 *
 * Screens and previews must explicitly receive a provider. Creating
 * fallback stores here would produce unrelated collections.
 */
val LocalTripsStore = compositionLocalOf<TripsStore> {
    error("TripsStore must be provided by App.")
}