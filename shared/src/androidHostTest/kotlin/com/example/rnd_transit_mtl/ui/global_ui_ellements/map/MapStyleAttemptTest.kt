package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import kotlin.test.Test
import kotlin.test.assertEquals

class MapStyleAttemptTest {
    @Test
    fun failedBundledStyleReloadsWithoutNetworkAccess() {
        val bundled = MapStyleAttempt(offline = true, attempt = 2)
        assertEquals(MapStyleAttempt(offline = true, attempt = 3), bundled.retry())
    }

    @Test
    fun workingBundledStyleSwitchesOnlineOnlyAfterConnectivityReturns() {
        val bundled = MapStyleAttempt(offline = true, attempt = 2)
        assertEquals(bundled, bundled.retryOnline(hasNetwork = false))
        assertEquals(MapStyleAttempt(offline = false, attempt = 3), bundled.retryOnline(hasNetwork = true))
    }
}
