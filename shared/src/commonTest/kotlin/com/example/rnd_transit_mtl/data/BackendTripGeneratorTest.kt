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

    private fun response(start: Place = origin, elapsed: Long = 600, buffer: Long = 0): String = buildJsonObject {
        put("status", "success")
        put("origin", Json.encodeToJsonElement(start))
        put("destination", Json.encodeToJsonElement(destination))
        put("elapsedSeconds", elapsed)
        put("orientationBufferSeconds", buffer)
        put("otp", Json.parseToJsonElement("""
            {"data":{"planConnection":{"routingErrors":[],"edges":[{"node":{
              "duration":600,"waitingTime":0,"legs":[{
                "mode":"WALK","distance":560,"duration":600,
                "from":{"name":"Start"},"to":{"name":"Public stop"},
                "steps":[{"relativeDirection":"DEPART","streetName":"Rue Sherbrooke","distance":280},
                  {"relativeDirection":"LEFT","streetName":"Rue Saint-Denis","distance":280}],
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
        val navigation = assertNotNull(trip.legs.first { it.isRouted }.navigation)
        assertEquals("Public stop", navigation.toName)
        assertEquals("Rue Saint-Denis", navigation.steps.last().streetName)
        assertEquals("LEFT", navigation.steps.last().relativeDirection)
        assertEquals(origin.point, trip.legs.first().start)
        assertEquals(destination.point, trip.legs.last().end)
        assertEquals(trip, Json.decodeFromString<Trip>(Json.encodeToString(trip)))
        assertEquals(trip, trip.toDto().toDomainOrNull())
    }

    @Test
    fun transitInstructionsKeepTheRouteNumberWhenTheCatalogueLabelIsAStreetName() = runTest {
        val numberedResponse = Json.parseToJsonElement(response()).jsonObject
        val otp = numberedResponse.getValue("otp").jsonObject
        val plan = otp.getValue("data").jsonObject.getValue("planConnection").jsonObject
        val node = plan.getValue("edges").jsonArray.single().jsonObject.getValue("node").jsonObject
        val leg = node.getValue("legs").jsonArray.single().jsonObject
        val transit = JsonObject(leg + mapOf(
            "mode" to JsonPrimitive("BUS"),
            "route" to buildJsonObject { put("gtfsId", "STM:24"); put("shortName", "24"); put("longName", "Sherbrooke") },
            "headsign" to JsonPrimitive("East"),
            "start" to buildJsonObject { put("scheduledTime", "2026-11-13T10:00:00-05:00") },
            "end" to buildJsonObject { put("scheduledTime", "2026-11-13T10:10:00-05:00") },
            "steps" to JsonArray(emptyList())
        ))
        val updatedNode = JsonObject(node + ("legs" to JsonArray(listOf(transit))))
        val updatedPlan = JsonObject(plan + ("edges" to JsonArray(listOf(buildJsonObject { put("node", updatedNode) }))))
        val body = JsonObject(numberedResponse + ("otp" to buildJsonObject { put("data", buildJsonObject { put("planConnection", updatedPlan) }) }))
        val bus = TransportId("bus")
        val trip = assertIs<TripGenerationResult.Success>(BackendTripGenerator(post = { body.toString() })
            .generate(input.copy(selectedTransportIds = listOf(bus)), listOf(TransportType(bus, "Bus", true)), emptySet())).trip
        val navigation = assertNotNull(trip.legs.first { it.isRouted }.navigation)
        assertEquals("24", navigation.routeShortName)
        assertEquals("East", navigation.headsign)
        assertEquals("Sherbrooke", trip.selectedRoutes.single().label)
    }

    @Test
    fun rejectsChangedOriginAndOverBudgetResponse() = runTest {
        for (body in listOf(response(origin.copy(label = "Changed")), response(elapsed = 1801))) {
            val generator = BackendTripGenerator(post = { body })
            assertIs<TripGenerationResult.RoutingUnavailable>(generator.generate(input, modes, emptySet()))
        }
    }

    @Test
    fun sendsTheChosenPaceAndPreservesTheOrientationReserve() = runTest {
        val generator = BackendTripGenerator(post = { body ->
            assertEquals("Dash", Json.decodeFromString<BackendGenerationRequest>(body).walkingPreference)
            response(elapsed = 634, buffer = 34)
        })
        val trip = assertIs<TripGenerationResult.Success>(generator.generate(input.copy(walkingPreference = "Dash"), modes, emptySet())).trip
        assertEquals("Dash", trip.walkingPreference)
        assertEquals(34L, trip.orientationBufferSeconds)
        assertEquals(634L, trip.routedDurationSeconds)
        assertEquals(trip, trip.toDto().toDomainOrNull())
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
