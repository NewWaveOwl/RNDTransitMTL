package com.example.rnd_transit_mtl.data.trips

import com.example.rnd_transit_mtl.data.remote.dto.toDto
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
