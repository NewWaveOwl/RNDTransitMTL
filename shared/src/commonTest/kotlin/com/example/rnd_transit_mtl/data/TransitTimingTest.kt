package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.data.remote.dto.toDomainOrNull
import com.example.rnd_transit_mtl.data.remote.dto.toDto
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.TransitTiming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class TransitTimingTest {
    private val timestamp = 1_794_024_913L
    private val timing = TransitTiming(
        scheduledDeparture = "2026-11-06T23:30:11-05:00",
        scheduledArrival = "2026-11-06T23:37:00-05:00",
        estimatedDeparture = "2026-11-06T23:30:11-05:00",
        realtimeApplied = true,
        receivedAt = timestamp * 1000,
        predictionSourceStatus = "fresh",
        predictionSourceTimestamp = timestamp,
        alertSourceStatus = "fresh",
        alertSourceTimestamp = timestamp,
        alerts = listOf("Stop relocated")
    )

    @Test
    fun freshnessUsesTheSourceTimestampRatherThanTheDownloadTime() {
        assertTrue(timing.predictionsFreshAt(timestamp * 1000))
        assertTrue(timing.predictionsFreshAt((timestamp + 180) * 1000))
        assertFalse(timing.predictionsFreshAt((timestamp + 181) * 1000))
        assertFalse(timing.copy(receivedAt = (timestamp + 181) * 1000)
            .predictionsFreshAt((timestamp + 181) * 1000))
        assertFalse(timing.copy(predictionSourceStatus = "unavailable")
            .predictionsFreshAt(timestamp * 1000))
        assertFalse(timing.predictionsFreshAt((timestamp - 31) * 1000))
        assertFalse(timing.copy(predictionSourceTimestamp = null)
            .predictionsFreshAt(timestamp * 1000))
        assertFalse(timing.copy(alertSourceStatus = "stale").alertsFreshAt(timestamp * 1000))
        assertTrue(timing.alertsFreshAt((timestamp + 600) * 1000))
        assertFalse(timing.alertsFreshAt((timestamp + 601) * 1000))
    }

    @Test
    fun savedAndBackendLegsPreserveTheAcceptedTimingSnapshot() {
        val leg = RouteLeg.straightLine(
            TransportId("bus"), GeoPoint(45.51, -73.57), GeoPoint(45.52, -73.58), 409
        ).copy(isRouted = true, transitTiming = timing)
        assertEquals(leg, Json.decodeFromString<RouteLeg>(Json.encodeToString(leg)))
        assertEquals(leg, leg.toDto().toDomainOrNull())
        val oldLeg = leg.copy(transitTiming = null)
        assertEquals(oldLeg, Json.decodeFromString<RouteLeg>(Json.encodeToString(oldLeg)))
    }
}
