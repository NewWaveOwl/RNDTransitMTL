package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.data.generation.*
import com.example.rnd_transit_mtl.data.remote.dto.toDto
import com.example.rnd_transit_mtl.data.remote.dto.toDomainOrNull
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.Trip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class BackendTripGeneratorTest {
    private val origin = Place("Confirmed pin", GeoPoint(45.500000001, -73.6))
    private val destination = Place("Public stop", GeoPoint(45.505, -73.6))
    private val modes = listOf(TransportType(TransportId("walk"), "Walk", false))
    private val input = TripGenerationInput("Trip", "Real route", "", 30, listOf(TransportId("walk")),
        emptyList(), 50f, originPlace = origin)

    private fun response(start: Place = origin, elapsed: Long = 600): String = buildJsonObject {
        put("status", "success")
        put("origin", Json.encodeToJsonElement(start))
        put("destination", Json.encodeToJsonElement(destination))
        put("elapsedSeconds", elapsed)
        put("otp", Json.parseToJsonElement("""
            {"data":{"planConnection":{"routingErrors":[],"edges":[{"node":{
              "duration":600,"waitingTime":0,"legs":[{
                "mode":"WALK","distance":560,"duration":600,
                "legGeometry":{"length":2,"points":"_vutG~~e`Mg^?"}
              }]
            }}]}}}
        """.trimIndent()))
    }.toString()

    @Test
    fun oneBackendCallPreservesExactPinGeometryBudgetAndSavedTiming() = runTest {
        var calls = 0
        val generator = BackendTripGenerator(post = { body ->
            calls++
            val request = Json.decodeFromString<BackendGenerationRequest>(body)
            assertEquals(origin, request.origin)
            assertEquals(listOf(destination.point), request.excludedDestinations)
            response()
        }, reportFailure = { throw AssertionError(it) })
        val trip = assertIs<TripGenerationResult.Success>(generator.generate(input, modes, emptySet(), listOf(destination.point))).trip
        assertEquals(1, calls)
        assertEquals(origin, trip.origin)
        assertEquals(destination, trip.destination)
        assertEquals(30, trip.plannedMinutes)
        assertEquals(600L, trip.routedDurationSeconds)
        assertTrue(trip.legs.any { it.isRouted })
        assertEquals(origin.point, trip.legs.first().start)
        assertEquals(destination.point, trip.legs.last().end)
        assertEquals(trip, Json.decodeFromString<Trip>(Json.encodeToString(trip)))
        assertEquals(trip, trip.toDto().toDomainOrNull())
    }

    @Test
    fun rejectsChangedOriginAndOverBudgetResponse() = runTest {
        for (body in listOf(response(origin.copy(label = "Changed")), response(elapsed = 1801))) {
            val generator = BackendTripGenerator(post = { body })
            assertIs<TripGenerationResult.RoutingUnavailable>(generator.generate(input, modes, emptySet()))
        }
    }

    @Test
    fun failuresRemainDistinctAndCancellationIsPropagated() = runTest {
        assertIs<TripGenerationResult.NoRoute>(BackendTripGenerator(post = { """{"status":"no_trip"}""" })
            .generate(input, modes, emptySet()))
        assertIs<TripGenerationResult.RoutingUnavailable>(BackendTripGenerator(post = { error("Offline") })
            .generate(input, modes, emptySet()))
        assertFailsWith<CancellationException> {
            BackendTripGenerator(post = { throw CancellationException("Cancelled") }).generate(input, modes, emptySet())
        }
    }
}
