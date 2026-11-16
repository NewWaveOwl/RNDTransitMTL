package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.View.main.transitDepartureSummary
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.trip.LegNavigation
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.TransitTiming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TransitDepartureSummaryTest {
    private val now = Instant.parse("2026-06-01T20:25:00Z").toEpochMilliseconds()
    private val timing = TransitTiming("2026-06-01T20:45:00Z", "2026-06-01T21:00:00Z", receivedAt = now)
    private fun leg(timing: TransitTiming? = this.timing, name: String? = "411") = RouteLeg(
        TransportId("bus"), geometry = listOf(GeoPoint(45.5, -73.6), GeoPoint(45.51, -73.6)),
        distanceMeters = 1000.0, durationSeconds = 900, isRouted = true,
        transitTiming = timing, navigation = LegNavigation(routeShortName = name)
    )

    @Test fun countdownUsesCurrentTimeAndMontrealClock() {
        assertEquals("Bus 411 in 20 min (16:45)", transitDepartureSummary(leg(), null, now))
        assertEquals("Bus 411 in 19 min (16:45)", transitDepartureSummary(leg(), null, now + 60_000))
        assertEquals("Bus 411 in 1 min (16:45)", transitDepartureSummary(leg(), null, now + 20 * 60_000 - 1))
        assertEquals("Bus 411 now (16:45)", transitDepartureSummary(leg(), null, now + 20 * 60_000))
        assertEquals("Bus 411 departed (16:45)", transitDepartureSummary(leg(), null, now + 21 * 60_000))
    }

    @Test fun onlyFreshRealtimeEstimatesOverrideTheSchedule() {
        val estimated = timing.copy(estimatedDeparture = "2026-06-01T20:46:00Z", realtimeApplied = true,
            predictionSourceStatus = "fresh", predictionSourceTimestamp = now / 1000)
        assertEquals("Bus 411 in 21 min (16:46)", transitDepartureSummary(leg(estimated), null, now))
        assertEquals("Bus 411 in 20 min (16:45)", transitDepartureSummary(leg(estimated.copy(
            predictionSourceTimestamp = now / 1000 - 181)), null, now))
        assertEquals("Bus 411 in 20 min (16:45)", transitDepartureSummary(leg(estimated.copy(
            estimatedDeparture = "invalid")), null, now))
    }

    @Test fun winterAndMidnightUseTheStopTimezone() {
        val winter = timing.copy(scheduledDeparture = "2026-11-15T21:45:00Z")
        val winterNow = Instant.parse("2026-11-15T21:25:00Z").toEpochMilliseconds()
        assertEquals("Bus 411 in 20 min (16:45)", transitDepartureSummary(leg(winter), null, winterNow))
        val midnight = timing.copy(scheduledDeparture = "2026-11-16T05:05:00Z")
        val midnightNow = Instant.parse("2026-11-16T04:45:00Z").toEpochMilliseconds()
        assertEquals("Bus 411 in 20 min (00:05)", transitDepartureSummary(leg(midnight), null, midnightNow))
    }

    @Test fun transportNamesPrefixTheirLines() {
        for ((id, label) in listOf("bus" to "Bus", "metro" to "Metro", "rem" to "REM", "train" to "Train")) {
            assertEquals("$label 411 in 20 min (16:45)", transitDepartureSummary(
                leg().copy(transportId = TransportId(id)), null, now))
        }
    }

    @Test fun missingNamesAndTimesHaveReadableFallbacks() {
        assertEquals("Bus Orange in 20 min (16:45)", transitDepartureSummary(leg(name = ""), "Orange", now))
        assertEquals("Bus - time unavailable", transitDepartureSummary(leg(null, null), null, now))
        assertEquals("Bus 411 - time unavailable", transitDepartureSummary(leg(timing.copy(
            scheduledDeparture = "invalid")), null, now))
    }
}
