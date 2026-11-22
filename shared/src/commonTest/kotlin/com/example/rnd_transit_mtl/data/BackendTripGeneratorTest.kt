package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.data.generation.*
import com.example.rnd_transit_mtl.data.remote.dto.toDto
import com.example.rnd_transit_mtl.data.remote.dto.toDomainOrNull
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
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
    fun fixedDestinationIsSentAndValidatedWithoutChangingTheInputOrigin() = runTest {
        val fixed = input.copy(destinationPlace = destination, compareModes = true)
        var failure: Exception? = null
        val result = BackendTripGenerator(post = { body, _ ->
            val request = Json.decodeFromString<BackendGenerationRequest>(body)
            assertEquals(origin, request.origin)
            assertEquals(destination, request.destination)
            assertTrue(request.compareModes)
            response()
        }, reportFailure = { failure = it }).generate(fixed, modes, emptySet())
        val trip = assertIs<TripGenerationResult.Success>(result, failure?.stackTraceToString()).trip
        assertEquals(origin, trip.origin)
        assertEquals(destination, trip.destination)
        assertTrue(trip.legs.any { it.isRouted })

        val wrongDestination = Place("Changed target", GeoPoint(45.52, -73.6))
        val wrong = JsonObject(Json.parseToJsonElement(response()).jsonObject +
            ("destination" to Json.encodeToJsonElement(wrongDestination))).toString()
        assertIs<TripGenerationResult.RoutingUnavailable>(BackendTripGenerator(post = { _, _ -> wrong })
            .generate(fixed, modes, emptySet()))
    }

    @Test
    fun returnRoutingFailureAndCancellationNeverProduceATrip() = runTest {
        val fixed = input.copy(destinationPlace = destination)
        assertIs<TripGenerationResult.NoRoute>(BackendTripGenerator(post = { _, _ -> """{"status":"no_trip"}""" })
            .generate(fixed, modes, emptySet()))
        assertFailsWith<CancellationException> {
            BackendTripGenerator(post = { _, _ -> throw CancellationException("cancel return") })
                .generate(fixed, modes, emptySet())
        }
    }

    @Test
    fun repeatingRestoredBusOnlyTripsKeepsWalkingAsAccessRatherThanAnAlternative() = runTest {
        val bus = TransportId("bus")
        val walk = TransportId("walk")
        val zone = ZoneId("A")
        fun geometry(from: GeoPoint, to: GeoPoint): JsonObject {
            var lat = 0; var lon = 0
            fun delta(number: Int): String {
                var value = if (number < 0) (number shl 1).inv() else number shl 1
                return buildString {
                    while (value >= 32) { append(((32 or (value and 31)) + 63).toChar()); value = value ushr 5 }
                    append((value + 63).toChar())
                }
            }
            val encoded = listOf(from, to).joinToString("") { point ->
                val a = kotlin.math.round(point.latitude * 1e5).toInt()
                val b = kotlin.math.round(point.longitude * 1e5).toInt()
                val text = delta(a - lat) + delta(b - lon)
                lat = a; lon = b; text
            }
            return buildJsonObject { put("length", 2); put("points", encoded) }
        }
        val middle = GeoPoint(45.502, -73.6)
        val original = Json.parseToJsonElement(response()).jsonObject
        val node = original.getValue("otp").jsonObject.getValue("data").jsonObject
            .getValue("planConnection").jsonObject.getValue("edges").jsonArray.single().jsonObject.getValue("node").jsonObject
        val walking = node.getValue("legs").jsonArray.single().jsonObject
        val access = JsonObject(walking + mapOf("duration" to JsonPrimitive(300), "legGeometry" to geometry(origin.point, middle)))
        val transit = JsonObject(walking + mapOf(
            "mode" to JsonPrimitive("BUS"), "duration" to JsonPrimitive(300), "steps" to JsonArray(emptyList()),
            "legGeometry" to geometry(middle, destination.point),
            "route" to buildJsonObject { put("gtfsId", "STM:24"); put("shortName", "24") },
            "start" to buildJsonObject { put("scheduledTime", "2026-11-13T10:00:00-05:00") },
            "end" to buildJsonObject { put("scheduledTime", "2026-11-13T10:05:00-05:00") }
        ))
        val updated = JsonObject(node + ("legs" to JsonArray(listOf(access, transit))))
        val body = JsonObject(original + ("otp" to buildJsonObject { put("data", buildJsonObject {
            put("planConnection", buildJsonObject {
                put("routingErrors", JsonArray(emptyList()))
                put("edges", buildJsonArray { add(buildJsonObject { put("node", updated) }) })
            })
        }) }))
        val busInput = input.copy(selectedTransportIds = listOf(bus), selectedZoneIds = listOf(zone),
            selectedZonesByTransport = mapOf(bus to listOf(zone)))
        val catalogue = listOf(TransportType(bus, "Bus", true), TransportType(walk, "Walk", false))
        val trip = assertIs<TripGenerationResult.Success>(BackendTripGenerator(post = { _, _ -> body.toString() })
            .generate(busInput, catalogue, emptySet())).trip
        assertEquals(setOf(bus, walk), trip.selectedTransports.map { it.id }.toSet())
        for (restored in listOf(Json.decodeFromString<Trip>(Json.encodeToString(trip)), assertNotNull(trip.toDto().toDomainOrNull()))) {
            val planner = MainPlannerViewModel()
            planner.copyTripPreferences(restored, liveMap = true)
            assertEquals(listOf(bus), planner.uiState.value.selectedTransportIds)
            BackendTripGenerator(post = { request, _ ->
                assertEquals(listOf("bus"), Json.decodeFromString<BackendGenerationRequest>(request).modes)
                """{"status":"no_trip"}"""
            }).generate(busInput.copy(selectedTransportIds = planner.uiState.value.selectedTransportIds), catalogue, emptySet())
        }
        val legacy = Json.decodeFromString<Trip>(JsonObject(Json.encodeToJsonElement(trip).jsonObject - "requestedTransportIds").toString())
        assertNull(legacy.requestedTransportIds)
        val legacyPlanner = MainPlannerViewModel()
        legacyPlanner.copyTripPreferences(legacy)
        assertEquals(legacy.selectedTransports.map { it.id }, legacyPlanner.uiState.value.selectedTransportIds)
    }

    @Test
    fun oneBackendCallPreservesExactPinGeometryBudgetAndSavedTiming() = runTest {
        var calls = 0
        val generator = BackendTripGenerator(post = { body, _ ->
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
        val trip = assertIs<TripGenerationResult.Success>(BackendTripGenerator(post = { _, _ -> body.toString() })
            .generate(input.copy(selectedTransportIds = listOf(bus)), listOf(TransportType(bus, "Bus", true)), emptySet())).trip
        val navigation = assertNotNull(trip.legs.first { it.isRouted }.navigation)
        assertEquals("24", navigation.routeShortName)
        assertEquals("East", navigation.headsign)
        assertEquals("Sherbrooke", trip.selectedRoutes.single().label)
    }

    @Test
    fun rejectsChangedOriginAndOverBudgetResponse() = runTest {
        for (body in listOf(response(origin.copy(label = "Changed")), response(elapsed = 1801))) {
            val generator = BackendTripGenerator(post = { _, _ -> body })
            assertIs<TripGenerationResult.RoutingUnavailable>(generator.generate(input, modes, emptySet()))
        }
    }

    @Test
    fun sendsTheChosenPaceAndPreservesTheOrientationReserve() = runTest {
        val generator = BackendTripGenerator(post = { body, _ ->
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
        assertIs<TripGenerationResult.NoRoute>(BackendTripGenerator(post = { _, _ -> """{"status":"no_trip"}""" })
            .generate(input, modes, emptySet()))
        assertIs<TripGenerationResult.RoutingUnavailable>(BackendTripGenerator(post = { _, _ -> error("Offline") })
            .generate(input, modes, emptySet()))
        assertFailsWith<CancellationException> {
            BackendTripGenerator(post = { _, _ -> throw CancellationException("Cancelled") }).generate(input, modes, emptySet())
        }
    }
    @Test fun bikeSpeedAndRequestIdReachBackendAndBikeRemainsBike()=runTest {
        val bike=TransportId("bike")
        val trip=assertIs<TripGenerationResult.Success>(BackendTripGenerator(post={ body, requestId ->
            val request=Json.decodeFromString<BackendGenerationRequest>(body)
            assertEquals(26,request.bikeSpeedKmh)
            assertTrue(requestId.isNotBlank())
            assertFalse(Json.parseToJsonElement(body).jsonObject.containsKey("requestId"))
            response().replace("WALK","BICYCLE")
        }).generate(input.copy(selectedTransportIds=listOf(bike),bikeSpeedKmh=26),
            listOf(TransportType(bike,"Bike",false)),emptySet())).trip
        assertTrue(trip.legs.all { it.transportId==bike })
        assertEquals(listOf(bike),trip.requestedTransportIds)
    }

    @Test fun diagnosticsIdentifyFailureStageWithoutLeakingExceptionMessages()=runTest {
        val events=mutableListOf<String>()
        val result=BackendTripGenerator(post={ _, _ -> throw IllegalStateException("Bearer SECRET origin=-73.6") },
            diagnostic={events+=it}).generate(input,modes,emptySet())
        assertIs<TripGenerationResult.RoutingUnavailable>(result)
        assertTrue(events.any { "stage=http" in it && "IllegalStateException" in it })
        assertFalse(events.joinToString().contains("SECRET"))
        assertFalse(events.joinToString().contains("-73.6"))
        events.clear()
        BackendTripGenerator(post={ _, _ -> "<html>tunnel unavailable</html>" },diagnostic={events+=it})
            .generate(input,modes,emptySet())
        assertTrue(events.any { "stage=decode" in it })
        events.clear()
        BackendTripGenerator(post={ _, _ -> response(elapsed=1801) },diagnostic={events+=it})
            .generate(input,modes,emptySet())
        assertTrue(events.any { "stage=validate" in it })
    }

    @Test fun httpFailuresAndSearchTimeoutHaveActionableMessages()=runTest {
        for((status,expected) in mapOf(401 to "Sign in",403 to "Sign in",429 to "requests",503 to "unavailable")) {
            val result=assertIs<TripGenerationResult.RoutingUnavailable>(BackendTripGenerator(post={ _, _ -> throw GenerationHttpFailure(status) })
                .generate(input,modes,emptySet()))
            assertTrue(result.message.contains(expected))
        }
        val result=BackendTripGenerator(post={ _, _ -> """{"status":"timeout"}""" }).generate(input,modes,emptySet())
        assertTrue(assertIs<TripGenerationResult.RoutingUnavailable>(result).message.contains("timed out"))
    }

    @Test fun socketTimeoutHasATimeoutMessageInsteadOfAGenericProviderError()=runTest {
        val result=BackendTripGenerator(post={ _, _ -> throw io.ktor.client.network.sockets.SocketTimeoutException("fixture") })
            .generate(input,modes,emptySet())
        assertTrue(assertIs<TripGenerationResult.RoutingUnavailable>(result).message.contains("timed out"))
    }

    @Test fun busyActiveAndCooldownResponsesHaveDistinctActionableMessages()=runTest {
        for((status,reason,expected) in listOf(
            Triple(503,"capacity","planner is busy"), Triple(429,"capacity","planner is busy"),
            Triple(429,"active","already being generated"), Triple(429,"cooldown","wait 2 seconds")
        )) {
            val result=BackendTripGenerator(post={ _, _ -> throw GenerationHttpFailure(status,2,reason) })
                .generate(input,modes,emptySet())
            val message=assertIs<TripGenerationResult.RoutingUnavailable>(result).message
            assertTrue(message.contains(expected),message)
            if(reason=="capacity") assertFalse(message.contains("already"))
        }
    }

    @Test fun walkBikeRejectsSingleModeResponsesEvenFromAnOldBackend()=runTest {
        val bike=TransportId("bike"); val walk=TransportId("walk")
        val request=input.copy(selectedTransportIds=listOf(walk,bike))
        val catalogue=listOf(TransportType(walk,"Walk",false),TransportType(bike,"Bike",false))
        for(body in listOf(response(),response().replace("WALK","BICYCLE"))) {
            assertIs<TripGenerationResult.RoutingUnavailable>(BackendTripGenerator(post={ _, _ ->body}).generate(request,catalogue,emptySet()))
        }
    }

    @Test fun walkBikeAcceptsAndRestoresWalkingApproachWithDominantCycling()=runTest {
        fun shape(points: List<GeoPoint>): JsonObject {
            var lat=0; var lon=0
            fun delta(n: Int): String {
                var v=if(n<0) (n shl 1).inv() else n shl 1
                return buildString { while(v>=32) { append(((32 or (v and 31))+63).toChar()); v=v ushr 5 }; append((v+63).toChar()) }
            }
            val encoded=points.joinToString("") {
                val a=kotlin.math.round(it.latitude*1e5).toInt(); val b=kotlin.math.round(it.longitude*1e5).toInt()
                val text=delta(a-lat)+delta(b-lon);lat=a;lon=b;text
            }
            return buildJsonObject { put("length",points.size);put("points",encoded) }
        }
        val raw=Json.parseToJsonElement(response(elapsed=214,buffer=34)).jsonObject
        val otp=raw.getValue("otp").jsonObject;val data=otp.getValue("data").jsonObject
        val plan=data.getValue("planConnection").jsonObject
        val node=plan.getValue("edges").jsonArray.single().jsonObject.getValue("node").jsonObject
        val leg=node.getValue("legs").jsonArray.single().jsonObject
        val middle=GeoPoint(45.5005,-73.6)
        val walking=JsonObject(leg+mapOf("duration" to JsonPrimitive(60),"distance" to JsonPrimitive(56),
            "steps" to JsonArray(emptyList()),"legGeometry" to shape(listOf(origin.point,middle))))
        val cycling=JsonObject(leg+mapOf("mode" to JsonPrimitive("BICYCLE"),"duration" to JsonPrimitive(120),
            "distance" to JsonPrimitive(504),"steps" to JsonArray(emptyList()),"legGeometry" to shape(listOf(middle,destination.point))))
        val combined=JsonObject(node+mapOf("duration" to JsonPrimitive(180),"legs" to JsonArray(listOf(walking,cycling))))
        val updatedPlan=JsonObject(plan+("edges" to buildJsonArray { add(buildJsonObject {put("node",combined)}) }))
        val body=JsonObject(raw+("otp" to JsonObject(otp+("data" to JsonObject(data+("planConnection" to updatedPlan))))))
        val walk=TransportId("walk");val bike=TransportId("bike")
        val trip=assertIs<TripGenerationResult.Success>(BackendTripGenerator(post={ _, _ ->body.toString()}).generate(
            input.copy(selectedTransportIds=listOf(walk,bike)),listOf(TransportType(walk,"Walk",false),TransportType(bike,"Bike",false)),emptySet())).trip
        assertEquals(listOf(walk,bike),trip.legs.filter {it.durationSeconds>0}.map {it.transportId})
        assertEquals(trip,Json.decodeFromString<Trip>(Json.encodeToString(trip)))
        assertEquals(trip,trip.toDto().toDomainOrNull())
        assertEquals(listOf(walk,bike),trip.requestedTransportIds)
    }

}
