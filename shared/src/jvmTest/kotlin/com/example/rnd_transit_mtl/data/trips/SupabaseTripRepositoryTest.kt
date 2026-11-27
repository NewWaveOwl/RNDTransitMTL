package com.example.rnd_transit_mtl.data.trips

import com.example.rnd_transit_mtl.data.remote.dto.toDto
import com.example.rnd_transit_mtl.data.remote.dto.TripDto
import com.example.rnd_transit_mtl.model.ids.GroupTripId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.review.TripReview
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import java.net.InetSocketAddress
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SupabaseTripRepositoryTest {
    private val owner = UserId("owner")
    private val walk = TransportId("walk")
    private val origin = Place("Origin", GeoPoint(45.5, -73.6))
    private val destination = Place("Destination", GeoPoint(45.6, -73.5))
    private val sharedRoute = GroupTripId("shared-route")
    private val pending = Trip(
        id = TripId("pending-trip"), ownerId = owner, status = TripStatus.Completed,
        title = "Original title", description = "Original description", imageUrl = "",
        origin = origin, destination = destination,
        legs = listOf(RouteLeg.straightLine(walk, origin.point, destination.point, 600)),
        plannedMinutes = 10,
        selectedTransports = listOf(TripTransportSnapshot(walk, "Walk", false)),
        selectedRoutes = emptyList(), attractionIntensity = 50f,
        createdAt = 1000L, completedAt = 2000L,
    )

    @Test
    fun retryAcknowledgesExistingTripAfterRemoteEdits() = runTest {
        val edited = pending.copy(
            title = "Remote title", description = "Remote description",
            review = TripReview(5),
        )
        withStoredTrip(edited) { repository ->
            assertEquals(edited, repository.saveFinalTrip(pending))
        }
    }

    @Test
    fun retryRejectsDifferentImmutableIdentity() = runTest {
        for (different in listOf(
            pending.copy(id = TripId("different-trip")),
            pending.copy(ownerId = UserId("different-owner")),
            pending.copy(createdAt = 999L),
        )) {
            withStoredTrip(different) { repository ->
                assertFailsWith<IllegalStateException> { repository.saveFinalTrip(pending) }
            }
        }
    }

    @Test
    fun groupSaveSendsNoRouteAndReadsTheSharedRouteBack() = runTest {
        val groupTrip = pending.copy(groupTripId = sharedRoute)
        val posted = mutableListOf<JsonObject>()
        withServer(rows = { listOf(rowJson(groupTrip)) }, onPost = { posted += it }) { repository ->
            assertEquals(groupTrip, repository.saveFinalTrip(groupTrip))
        }

        val payload = posted.single()
        assertEquals(JsonPrimitive("shared-route"), payload["group_trip_id"])
        assertEquals(emptySet(), payload.keys intersect SupabaseTripRepository.SHARED_ROUTE_FIELDS)
        assertTrue(payload.keys.containsAll(listOf("id", "owner_id", "status", "title", "description", "completed_at")))
    }

    @Test
    fun soloSaveStillSendsItsRouteAndNoGroupLink() = runTest {
        val posted = mutableListOf<JsonObject>()
        withServer(rows = { listOf(rowJson(pending)) }, onPost = { posted += it }) { repository ->
            repository.saveFinalTrip(pending)
        }

        val payload = posted.single()
        assertTrue(payload.keys.containsAll(listOf("origin", "destination", "legs", "transports")))
        assertFalse("group_trip_id" in payload.keys)
        assertFalse("journey" in payload.keys)
    }

    @Test
    fun historySkipsGroupRowsWhoseSharedRouteIsNotReadable() = runTest {
        val unreadable = rowJson(pending.copy(id = TripId("hidden-group-trip"), groupTripId = sharedRoute))
            .let { JsonObject(it + ("group_trip" to JsonNull)) }
        withServer(rows = { listOf(rowJson(pending), unreadable) }) { repository ->
            assertEquals(listOf(pending), repository.listHistory())
        }
    }

    @Test
    fun retryRejectsADifferentGroupTripLink() = runTest {
        val mine = pending.copy(groupTripId = sharedRoute)
        withServer(rows = { listOf(rowJson(mine.copy(groupTripId = GroupTripId("other-route")))) }) { repository ->
            assertFailsWith<IllegalStateException> { repository.saveFinalTrip(mine) }
        }
    }

    /**
     * The row PostgREST returns: a group trip's route arrives in the embedded group_trip
     * object, not on the member's own row.
     */
    private fun rowJson(trip: Trip): JsonObject {
        val fields = Json.encodeToJsonElement(TripDto.serializer(), trip.toDto()).jsonObject
        if (trip.groupTripId == null) return fields
        val shared = SupabaseTripRepository.SHARED_ROUTE_FIELDS
        return JsonObject(fields.filterKeys { it !in shared } +
            ("group_trip" to JsonObject(fields.filterKeys { it in shared })))
    }

    private suspend fun withServer(
        rows: () -> List<JsonObject>,
        onPost: (JsonObject) -> Unit = {},
        block: suspend (SupabaseTripRepository) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/v1/trips") { exchange ->
            val request = exchange.requestBody.use { it.readBytes() }.decodeToString()
            if (exchange.requestMethod == "POST") {
                val body = Json.parseToJsonElement(request)
                onPost(if (body is JsonArray) body.single().jsonObject else body.jsonObject)
                exchange.sendResponseHeaders(201, -1)
            } else {
                val body = JsonArray(rows()).toString().toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        val client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "test-key") {
            install(Postgrest)
        }
        try {
            block(SupabaseTripRepository(owner, client))
        } finally {
            client.close()
            server.stop(0)
        }
    }

    // Model an insert that already succeeded: duplicate POSTs do nothing and GET
    // returns the current remote row, which may have changed since the first insert.
    private suspend fun withStoredTrip(stored: Trip, block: suspend (SupabaseTripRepository) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/v1/trips") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            if (exchange.requestMethod == "POST") {
                exchange.sendResponseHeaders(201, -1)
            } else {
                val body = Json.encodeToString(listOf(stored.toDto())).toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        val client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "test-key") {
            install(Postgrest)
        }
        try {
            block(SupabaseTripRepository(owner, client))
        } finally {
            client.close()
            server.stop(0)
        }
    }
}
