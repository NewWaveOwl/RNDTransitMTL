package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun offlineRetryShowsStillOfflineNoticeWithoutChangingMapKeyUntilOnline() {
        val offlineState = MapStyleUiState().fallBackToOffline()
        assertFalse(offlineState.showStillOffline)

        val firstRejected = offlineState.retryOnline(hasNetwork = false)
        assertEquals(offlineState.styleAttempt, firstRejected.styleAttempt)
        assertTrue(firstRejected.showStillOffline)
        assertEquals(1, firstRejected.offlineRetryCount)

        val dismissed = firstRejected.dismissStillOffline()
        assertFalse(dismissed.showStillOffline)

        val secondRejected = dismissed.retryOnline(hasNetwork = false)
        assertEquals(offlineState.styleAttempt, secondRejected.styleAttempt)
        assertTrue(secondRejected.showStillOffline)
        assertEquals(2, secondRejected.offlineRetryCount)

        val reconnected = secondRejected.retryOnline(hasNetwork = true)
        assertEquals(MapStyleAttempt(offline = false, attempt = 1), reconnected.styleAttempt)
        assertFalse(reconnected.showStillOffline)
    }
}
