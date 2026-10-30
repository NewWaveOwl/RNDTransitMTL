package com.example.rnd_transit_mtl.model

import androidx.compose.runtime.saveable.SaverScope
import com.example.rnd_transit_mtl.model.ids.GroupId
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.state.NullableTransportIdSaver
import com.example.rnd_transit_mtl.state.NullableTripIdSaver
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IdsTest {
    @Serializable
    private data class SavedRecord(
        val tripId: TripId,
        val pendingTripId: TripId?,
        val zonesByTransport: Map<TransportId, List<ZoneId>>
    )

    @Test
    fun savedJsonIsTheSameAsWithPlainStrings() {
        val record = SavedRecord(
            tripId = TripId("trip-1"),
            pendingTripId = null,
            zonesByTransport = mapOf(TransportId("bus") to listOf(ZoneId("A")))
        )
        val existingFormat =
            """{"tripId":"trip-1","pendingTripId":null,"zonesByTransport":{"bus":["A"]}}"""

        assertEquals(existingFormat, Json.encodeToString(SavedRecord.serializer(), record))
        assertEquals(record, Json.decodeFromString(SavedRecord.serializer(), existingFormat))
    }

    @Test
    fun blankIdsAreRejected() {
        assertFailsWith<IllegalArgumentException> { TripId(" ") }
        assertFailsWith<IllegalArgumentException> { UserId("") }
        assertFailsWith<IllegalArgumentException> { GroupId(" ") }
        assertFailsWith<IllegalArgumentException> { TransportId("") }
        assertFailsWith<IllegalArgumentException> { RouteId(" ") }
        assertFailsWith<IllegalArgumentException> { ZoneId("") }
    }

    @Test
    fun toStringReturnsTheRawValueForMessagesAndKeys() {
        assertEquals("Trip reference: trip-1", "Trip reference: ${TripId("trip-1")}")
        assertEquals("ZONE A", "ZONE ${ZoneId("A")}")
    }

    @Test
    fun saversStoreTheRawValueAndSkipNull() {
        val scope = SaverScope { true }

        assertEquals("trip-1", with(NullableTripIdSaver) { scope.save(TripId("trip-1")) })
        assertEquals(TripId("trip-1"), NullableTripIdSaver.restore("trip-1"))
        assertNull(with(NullableTripIdSaver) { scope.save(null) })

        assertEquals("bus", with(NullableTransportIdSaver) { scope.save(TransportId("bus")) })
        assertEquals(TransportId("bus"), NullableTransportIdSaver.restore("bus"))
        assertNull(with(NullableTransportIdSaver) { scope.save(null) })
    }

    @Test
    fun blankIdInSavedJsonFailsToDecode() {
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString(TripId.serializer(), "\"  \"")
        }
    }
}
