package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.data.fake.FakeLocationSource
import com.example.rnd_transit_mtl.data.fake.FakeRoutingService
import com.example.rnd_transit_mtl.data.generation.MockTripGenerator
import com.example.rnd_transit_mtl.data.generation.TripGenerationInput
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.data.routing.RoutingResult
import com.example.rnd_transit_mtl.data.routing.RoutingService
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.model.trip.TripRequest
import com.example.rnd_transit_mtl.data.assets.toSampleMapPlace
import com.example.rnd_transit_mtl.testDestination
import com.example.rnd_transit_mtl.testOrigin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RoutingAndLocationTest {
    private val walk = TransportId("walk")
    private val lachineLighthouse = Place("Lachine lighthouse", GeoPoint(45.4316, -73.6745))

    private fun request(destination: Place? = testDestination, origin: Place = testOrigin) = TripRequest(
        ownerId = null,
        origin = origin,
        destination = destination,
        plannedMinutes = 30,
        modes = listOf(walk)
    )

    /** Runs one generation with the given router; the request itself is always valid. */
    private suspend fun generateWith(router: RoutingService): TripGenerationResult =
        MockTripGenerator(routingService = router, random = Random(5), idFactory = { "routed" }, nowEpochMillis = { 1_000L })
            .generate(
                input = TripGenerationInput(
                    title = "Routed",
                    description = "Routing test.",
                    imageUrl = "",
                    plannedMinutes = 30,
                    selectedTransportIds = listOf(walk),
                    selectedRouteIds = emptyList(),
                    attractionIntensity = 50f
                ),
                transportTypes = listOf(TransportType(walk, "Walk", usesRoutes = false)),
                transportRoutes = emptyList(),
                existingTripIds = emptySet()
            )

    @Test
    fun fakeRouterDrawsOneUnroutedLegForThePlannedTime() = runTest {
        val legs = assertIs<RoutingResult.Success>(FakeRoutingService().route(request())).legs

        val leg = legs.single()
        assertEquals(testOrigin.point, leg.start)
        assertEquals(testDestination.point, leg.end)
        assertEquals(walk, leg.transportId)
        assertEquals(30 * 60L, leg.durationSeconds)
        assertFalse(leg.isRouted)
    }

    @Test
    fun fakeRouterRejectsPointsOutsideTheSampleMap() = runTest {
        val result = FakeRoutingService().route(request(destination = lachineLighthouse))

        assertEquals(RoutingResult.OutsideServiceArea(lachineLighthouse.point), result)
    }

    @Test
    fun fakeRouterAcceptsPointsPickedOnTheMapEdge() = runTest {
        val corner = TripPoint("Corner", 0f, 1f).toSampleMapPlace()

        assertIs<RoutingResult.Success>(FakeRoutingService().route(request(destination = corner)))
    }

    @Test
    fun fakeRouterNeedsADestination() = runTest {
        assertEquals(RoutingResult.NoRouteFound, FakeRoutingService().route(request(destination = null)))
    }

    @Test
    fun generatorUsesTheRoutersLegs() = runTest {
        val twoLegRouter = object : RoutingService {
            override suspend fun route(request: TripRequest): RoutingResult {
                val middle = GeoPoint(45.51, -73.59)
                val end = request.destination!!.point
                return RoutingResult.Success(listOf(
                    RouteLeg(walk, geometry = listOf(request.origin.point, middle), distanceMeters = 500.0, durationSeconds = 600, isRouted = true),
                    RouteLeg(walk, geometry = listOf(middle, end), distanceMeters = 700.0, durationSeconds = 900, isRouted = true)
                ))
            }
        }

        val trip = assertIs<TripGenerationResult.Success>(generateWith(twoLegRouter)).trip

        assertEquals(2, trip.legs.size)
        assertEquals(1.2, trip.distanceKm)
    }

    @Test
    fun routingFailuresBecomeGenerationResults() = runTest {
        val noRoute = object : RoutingService {
            override suspend fun route(request: TripRequest) = RoutingResult.NoRouteFound
        }
        val outside = object : RoutingService {
            override suspend fun route(request: TripRequest) = RoutingResult.OutsideServiceArea(request.origin.point)
        }
        val down = object : RoutingService {
            override suspend fun route(request: TripRequest) = RoutingResult.Unavailable("Server offline")
        }

        assertEquals(TripGenerationResult.NoRoute, generateWith(noRoute))
        assertEquals(TripGenerationResult.NoRoute, generateWith(outside))
        assertEquals(TripGenerationResult.RoutingUnavailable("Server offline"), generateWith(down))
    }

    @Test
    fun legsThatMissTheEndpointsAreTreatedAsAProviderFault() = runTest {
        val wrongEnd = object : RoutingService {
            override suspend fun route(request: TripRequest) = RoutingResult.Success(listOf(
                RouteLeg.straightLine(walk, request.origin.point, GeoPoint(45.50, -73.60), 600)
            ))
        }

        assertIs<TripGenerationResult.RoutingUnavailable>(generateWith(wrongEnd))
    }

    @Test
    fun fakeLocationReplaysItsEventsInOrder() = runTest {
        val events = listOf(
            LocationUpdate.Fix(testOrigin.point, accuracyMeters = 12.0),
            LocationUpdate.PermissionDenied,
            LocationUpdate.Unavailable
        )

        assertEquals(events, FakeLocationSource(events, intervalMillis = 500).updates.toList())
    }
}
