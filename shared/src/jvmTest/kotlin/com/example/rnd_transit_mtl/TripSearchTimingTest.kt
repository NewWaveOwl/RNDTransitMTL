package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.View.main.components.advanceSearchPhase
import kotlin.test.Test
import kotlin.test.assertEquals

class TripSearchTimingTest {
    @Test
    fun equalElapsedTimeProducesTheSamePhaseAcrossFrameRates() {
        val elapsedNanos = 5_125_000_000L
        val expected = advanceSearchPhase(0L, elapsedNanos)
        for (frames in listOf(30, 60, 90, 120, 144)) {
            var phase = 0L
            var previous = 0L
            for (frame in 1..frames) {
                val now = elapsedNanos * frame / frames
                phase = advanceSearchPhase(phase, now - previous)
                previous = now
            }
            assertEquals(expected, phase, "Frame count: $frames")
        }
    }

    @Test
    fun droppedFramesCatchUpAndInvalidDeltasDoNotReverseMotion() {
        val start = 1_200_000_000L
        val afterStall = advanceSearchPhase(start, 900_000_000L)
        assertEquals(2_100_000_000L, afterStall)
        assertEquals(afterStall, advanceSearchPhase(afterStall, -1L))
        assertEquals(100_000_000L, advanceSearchPhase(afterStall, 800_000_000L))
    }
}
