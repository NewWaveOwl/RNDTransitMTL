package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.data.location.LocationRecoveryPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationRecoveryPolicyTest {
    @Test
    fun stalledTrackingRetriesAfterThirtySecondsWithBoundedBackoff() {
        val policy = LocationRecoveryPolicy(1_000)
        assertFalse(policy.retryIfStalled(31_000))
        assertTrue(policy.retryIfStalled(31_001))
        assertFalse(policy.retryIfStalled(91_001))
        assertTrue(policy.retryIfStalled(91_002))
        assertFalse(policy.retryIfStalled(211_002))
        assertTrue(policy.retryIfStalled(211_003))
        assertFalse(policy.retryIfStalled(331_003))
        assertTrue(policy.retryIfStalled(331_004))
    }

    @Test
    fun freshMeasurementsPreventRestartsAndRecoveryResetsTheBackoff() {
        val policy = LocationRecoveryPolicy(1_000)
        repeat(12) { index ->
            val now = 1_000L + (index + 1) * 10_000L
            policy.recordFix(now, now) // Accuracy is deliberately irrelevant to listener health.
            assertFalse(policy.retryIfStalled(now))
        }
        assertTrue(policy.retryIfStalled(151_001))
        policy.recordFix(160_000, 160_000)
        assertFalse(policy.retryIfStalled(190_000))
        assertTrue(policy.retryIfStalled(190_001))
    }

    @Test
    fun oldDuplicateAndFutureMeasurementsDoNotPostponeRecovery() {
        val policy = LocationRecoveryPolicy(1_000)
        policy.recordFix(2_000, 2_000)
        policy.recordFix(2_000, 20_000)
        policy.recordFix(10_000, 30_000)
        policy.recordFix(40_000, 30_000)
        assertFalse(policy.retryIfStalled(32_000))
        assertTrue(policy.retryIfStalled(32_001))
    }
}
