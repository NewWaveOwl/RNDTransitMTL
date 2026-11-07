package com.example.rnd_transit_mtl.data.routing

import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.TripRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OtpRoutingServiceTest {
    @Test
    fun mapsTheCapturedStmExoRouteThroughTheSharedContract() = runTest {
        val demo = OtpTripDemo.StmExo
        val router: RoutingService = OtpRoutingService(demo, { mixedResponse }, { departure })

        val route = assertIs<RoutingResult.Success>(router.route(demo.request))
        val routedLegs = route.legs.filter { it.isRouted }
        assertEquals(demo.request.modes, routedLegs.map { it.transportId })
        assertEquals(listOf(3, 22, 30), routedLegs.map { it.geometry.size })
        assertEquals(6033.57, routedLegs.sumOf { it.distanceMeters }, 0.001)
        assertEquals(listOf(180L, 330L, 315L), routedLegs.map { it.durationSeconds })
        assertEquals(1215L, route.durationSeconds)
        assertEquals(390L, route.waitingTimeSeconds)
        assertEquals(listOf("STM:2", "EXO_TRAINS:1"), route.routes.map { it.id.value })
        assertEquals("2026-11-07T09:40:15-05:00", routedLegs.last().transitTiming?.scheduledArrival)
        assertEquals(demo.request.origin.point, route.legs.first().start)
        assertEquals(demo.request.destination?.point, route.legs.last().end)
        assertTrue(route.legs.zipWithNext().all { (a, b) -> a.end == b.start })
    }

    @Test
    fun onlySelectedStreetModesCanProduceARoutedFallback() = runTest {
        val request = OtpTripDemo.StmBus.request.copy(
            origin = Place("Notre-Dame Basilica", GeoPoint(45.50455, -73.55610)),
            destination = Place("Bonsecours Market", GeoPoint(45.50905, -73.55118))
        )

        for ((mode, fixture, expectedPoints) in listOf(
            Triple(OtpMode.Walk, walkResponse, 72),
            Triple(OtpMode.Bike, bikeResponse, 42)
        )) {
            val streetRouter = OtpRoutingService(OtpTripDemo.StmBus, { body ->
                val variables = Json.parseToJsonElement(body).jsonObject.getValue("variables").jsonObject
                val modes = variables.getValue("modes").jsonObject
                assertEquals("true", modes.getValue("directOnly").jsonPrimitive.content)
                assertEquals(mode.otp, modes.getValue("direct").jsonArray.single().jsonPrimitive.content)
                assertEquals("45.50455", variables.getValue("originLat").jsonPrimitive.content)
                assertEquals("-73.55118", variables.getValue("destinationLon").jsonPrimitive.content)
                fixture
            }, { error("A street route needs no departure time.") })
            val router = routingService { input ->
                if (input.modes == request.modes) RoutingResult.NoRouteFound else streetRouter.route(input)
            }

            val result = assertIs<TripGenerationResult.Success>(loadOtpDebugTrip(request, listOf(mode.id), router))
            val leg = result.trip.legs.single { it.isRouted }
            assertEquals(mode.id, leg.transportId)
            assertEquals(expectedPoints, leg.geometry.size)
            assertTrue(leg.distanceMeters > 0 && leg.durationSeconds > 0)
            assertEquals(listOf(mode.id), result.trip.selectedTransports.map { it.id })
        }
    }

    @Test
    fun distinguishesInvalidRequestNoItineraryAndProviderTimeout() = runTest {
        val demo = OtpTripDemo.StmBus
        val noRoute = OtpRoutingService(demo, {
            """{"data":{"planConnection":{"routingErrors":[{"code":"NO_TRANSIT_CONNECTION"}],"edges":[]}}}"""
        }, { departure })
        val offline = OtpRoutingService(demo, { error("OTP connection timed out") }, { departure })

        assertIs<TripGenerationResult.InvalidInput>(
            loadOtpDebugTrip(demo.request.copy(destination = demo.request.origin), emptyList(),
                routingService { throw AssertionError("An invalid request must never call OTP.") })
        )
        assertEquals(TripGenerationResult.NoRoute, loadOtpDebugTrip(demo.request, emptyList(), noRoute))
        assertIs<TripGenerationResult.RoutingUnavailable>(loadOtpDebugTrip(demo.request, emptyList(), offline))
    }

    private fun routingService(resolve: suspend (TripRequest) -> RoutingResult): RoutingService =
        object : RoutingService {
            override suspend fun route(request: TripRequest): RoutingResult = resolve(request)
        }

    private val departure = "2026-11-07T09:19:00-05:00"

    // Captured OTP shapes and timings. Source requests are listed in the issue #21 ADR.
    private val walkResponse = """
        {"data":{"planConnection":{"routingErrors":[],"edges":[{"node":{
          "duration":594,"legs":[
            {"mode":"WALK",
             "distance":750.43,
             "legGeometry":{"length":72,"points":"osvtG`l}_MBMNu@GAECy@_@o@[q@[?ATkABSDSX{A@?AACCGE?ECCm@[A@AAKGMGA??AAC[O??ECCAA??AACKGKGe@_@AAA@?AECECA??A_As@CCGEWQMIGCGCGC?ACAaA_@k@UBG@GX_AECCAYMCACCe@Sy@_@AAAAGEEAAAoD_B"}}
          ]
        }}]}}}
    """.trimIndent()

    private val bikeResponse = """
        {"data":{"planConnection":{"routingErrors":[],"edges":[{"node":{
          "duration":348,"legs":[
            {"mode":"BICYCLE",
             "distance":1066.52,
             "legGeometry":{"length":42,"points":"osvtG`l}_MBMNu@GACLm@jDANKIcBoA{BcBCCMGMGCAGEe@_@DOjA_EFSDOBIb@{ABI@GXuAFUi@Oa@OqCgBc@S_A_@SIECa@QEAcBw@GEECyG{CCR@?dBt@"}}
          ]
        }}]}}}
    """.trimIndent()

    private val mixedResponse = """
        {"data":{"planConnection":{"routingErrors":[],"edges":[{"node":{
          "duration":1215,"waitingTime":390,"legs":[
            {"mode":"SUBWAY",
             "distance":2144,
             "duration":180,
             "realTime":false,
             "route":{"gtfsId":"STM:2","shortName":"2","longName":"Ligne 2 - Orange"},
             "start":{"scheduledTime":"2026-11-07T09:20:00-05:00","estimated":null},
             "end":{"scheduledTime":"2026-11-07T09:23:00-05:00","estimated":null},
             "legGeometry":{"length":3,"points":"shrtGbcb`Mj_@hg@bSviB"},
             "alerts":[]},
            {"mode":"WALK",
             "distance":139.57,
             "duration":330,
             "realTime":false,
             "route":null,
             "start":{"scheduledTime":"2026-11-07T09:23:00-05:00","estimated":null},
             "end":{"scheduledTime":"2026-11-07T09:28:30-05:00","estimated":null},
             "legGeometry":{"length":22,"points":"{rptGpvf`MAEA?Oq@S]EIACAAISDGTMFC??PKV[????EICDDJTd@EF"},
             "alerts":[]},
            {"mode":"RAIL",
             "distance":3750,
             "duration":315,
             "realTime":false,
             "route":{"gtfsId":"EXO_TRAINS:1","shortName":"VH","longName":"11 - Vaudreuil/Hudson"},
             "start":{"scheduledTime":"2026-11-07T09:35:00-05:00","estimated":null},
             "end":{"scheduledTime":"2026-11-07T09:40:15-05:00","estimated":null},
             "legGeometry":{"length":30,"points":"_rptGjrf`MVh@tCvGZt@f@hAn@|ADF`@dADFv@lB^|@t@dBbAfCTh@FNnCrGvJpUzAnDbPf`@dLbXtC`H\\x@lEhK`GnNnJbUfG|NvD`JtEzK~CtHXt@"},
             "alerts":[]}
          ]
        }}]}}}
    """.trimIndent()
}
