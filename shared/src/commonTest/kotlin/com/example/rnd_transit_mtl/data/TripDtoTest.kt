package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.data.remote.dto.LineStringDto
import com.example.rnd_transit_mtl.data.remote.dto.PlaceDto
import com.example.rnd_transit_mtl.data.remote.dto.RouteLegDto
import com.example.rnd_transit_mtl.data.remote.dto.TripRequestDto
import com.example.rnd_transit_mtl.data.remote.dto.toDomainOrNull
import com.example.rnd_transit_mtl.data.remote.dto.toDto
import com.example.rnd_transit_mtl.data.remote.dto.tripStatusFromDtoOrNull
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.trip.OsmElementType
import com.example.rnd_transit_mtl.model.trip.OsmReference
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.TripRequest
import com.example.rnd_transit_mtl.model.trip.TripStatus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.example.rnd_transit_mtl.data.remote.dto.TripDto
import com.example.rnd_transit_mtl.data.remote.dto.TripReviewDto
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.review.TripReview
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TripDtoTest {
    private val berriUqam = Place(
        "Berri-UQAM",
        GeoPoint(45.51510, -73.56113),
        OsmReference(OsmElementType.Node, 123)
    )
    private val sainteJustine = Place("CHU Sainte-Justine", GeoPoint(45.502880, -73.624792))

    @Test
    fun placeUsesSnakeCaseAndConvertsBack() {
        val json = Json.encodeToString(PlaceDto.serializer(), berriUqam.toDto())

        assertEquals(
            """{"label":"Berri-UQAM","latitude":45.5151,"longitude":-73.56113,"osm_type":"node","osm_id":123}""",
            json
        )
        assertEquals(berriUqam, Json.decodeFromString(PlaceDto.serializer(), json).toDomainOrNull())
        assertEquals(sainteJustine, sainteJustine.toDto().toDomainOrNull())
    }

    @Test
    fun invalidPlaceRowsBecomeNull() {
        assertNull(PlaceDto("Bad", latitude = 91.0, longitude = 0.0).toDomainOrNull())
        assertNull(PlaceDto(" ", latitude = 45.0, longitude = -73.0).toDomainOrNull())
        assertNull(PlaceDto("Half", 45.0, -73.0, osmType = "node").toDomainOrNull())
        assertNull(PlaceDto("Unknown", 45.0, -73.0, osmType = "area", osmId = 1).toDomainOrNull())
    }

    @Test
    fun legGeometryIsGeoJsonWithLongitudeFirst() {
        val leg = RouteLeg(
            transportId = TransportId("bus"),
            routeId = RouteId("24"),
            geometry = listOf(berriUqam.point, sainteJustine.point),
            distanceMeters = 5200.5, // Not a whole number: JS would print 5200.0 as 5200.
            durationSeconds = 1200,
            isRouted = true
        )
        val json = Json.encodeToString(RouteLegDto.serializer(), leg.toDto())

        assertEquals(
            """{"transport_id":"bus","route_id":"24","geometry":{"type":"LineString",""" +
                """"coordinates":[[-73.56113,45.5151],[-73.624792,45.50288]]},""" +
                """"distance_meters":5200.5,"duration_seconds":1200,"is_routed":true}""",
            json
        )
        assertEquals(leg, Json.decodeFromString(RouteLegDto.serializer(), json).toDomainOrNull())
    }

    @Test
    fun invalidLegRowsBecomeNull() {
        fun row(type: String = "LineString", coordinates: List<List<Double>>) = RouteLegDto(
            transportId = "walk",
            geometry = LineStringDto(type, coordinates),
            distanceMeters = 1.0,
            durationSeconds = 1,
            isRouted = false
        )

        assertNull(row(type = "Point", coordinates = listOf(listOf(-73.5, 45.5), listOf(-73.6, 45.6))).toDomainOrNull())
        assertNull(row(coordinates = listOf(listOf(-73.5, 45.5))).toDomainOrNull())
        assertNull(row(coordinates = listOf(listOf(-73.5), listOf(-73.6, 45.6))).toDomainOrNull())
    }

    @Test
    fun requestConvertsBothWays() {
        val request = TripRequest(
            ownerId = UserId("user-1"),
            origin = berriUqam,
            destination = null,
            plannedMinutes = 30,
            modes = listOf(TransportId("walk"), TransportId("metro")),
            zonesByTransport = mapOf(TransportId("metro") to listOf(ZoneId("A")))
        )
        val json = Json.encodeToString(TripRequestDto.serializer(), request.toDto())

        assertEquals(request, Json.decodeFromString(TripRequestDto.serializer(), json).toDomainOrNull())
        assertNull(request.toDto().copy(ownerId = " ").toDomainOrNull())
        assertNull(request.toDto().copy(origin = PlaceDto("Bad", 91.0, 0.0)).toDomainOrNull())
    }

    private fun completedTrip(): Trip = Trip(
        id = TripId("dto-trip"),
        ownerId = UserId("user-1"),
        status = TripStatus.Completed,
        title = "DTO trip",
        description = "Round trip through the backend shape.",
        imageUrl = "",
        origin = berriUqam,
        destination = sainteJustine,
        legs = listOf(RouteLeg.straightLine(TransportId("bus"), berriUqam.point, sainteJustine.point, 1_800, RouteId("24"))),
        plannedMinutes = 30,
        selectedTransports = listOf(TripTransportSnapshot(TransportId("bus"), "Bus", usesRoutes = true)),
        selectedRoutes = listOf(TripRouteSnapshot(RouteId("24"), TransportId("bus"), "24 Sherbrooke")),
        attractionIntensity = 40f,
        createdAt = 1_793_000_000_000L,
        completedAt = 1_793_000_600_000L,
        review = TripReview(overall = 4, `fun` = 5, comment = "Nice"),
        selectedZoneIds = listOf(ZoneId("A")),
        selectedZonesByTransport = mapOf(TransportId("bus") to listOf(ZoneId("A")))
    )

    @Test
    fun tripConvertsBothWaysThroughJson() {
        val trip = completedTrip()
        val json = Json.encodeToString(TripDto.serializer(), trip.toDto())

        assertEquals(trip, Json.decodeFromString(TripDto.serializer(), json).toDomainOrNull())
    }

    @Test
    fun tripUsesBackendNamesAndIsoTimestamps() {
        val dto = completedTrip().toDto()
        val json = Json.parseToJsonElement(Json.encodeToString(TripDto.serializer(), dto)).jsonObject

        assertEquals("completed", dto.status)
        assertEquals("2026-10-26T07:33:20Z", dto.createdAt)
        assertEquals("2026-10-26T07:43:20Z", dto.completedAt)
        assertTrue(
            json.keys.containsAll(
                listOf("owner_id", "image_url", "planned_minutes", "attraction_intensity", "created_at", "completed_at", "zones_by_transport")
            )
        )
        assertFalse("distanceKm" in json.keys || "distance_km" in json.keys)
        assertEquals(5, dto.review?.funRating)
        assertTrue("\"fun\":5" in Json.encodeToString(TripReviewDto.serializer(), assertNotNull(dto.review)))
    }

    @Test
    fun cancelledTripKeepsItsCancellationTime() {
        val cancelled = completedTrip().copy(
            status = TripStatus.Cancelled,
            completedAt = null,
            cancelledAt = 1_793_000_300_000L,
            review = null
        )
        val dto = cancelled.toDto()

        assertEquals("cancelled", dto.status)
        assertEquals("2026-10-26T07:38:20Z", dto.cancelledAt)
        assertEquals(cancelled, Json.decodeFromString(TripDto.serializer(), Json.encodeToString(TripDto.serializer(), dto)).toDomainOrNull())
        assertNull(dto.copy(cancelledAt = null).toDomainOrNull())
    }

    @Test
    fun invalidTripRowsBecomeNull() {
        val dto = completedTrip().toDto()

        assertNull(dto.copy(status = "paused").toDomainOrNull())
        assertNull(dto.copy(createdAt = "yesterday").toDomainOrNull())
        assertNull(dto.copy(completedAt = null).toDomainOrNull())
        assertNull(dto.copy(legs = emptyList()).toDomainOrNull())
        assertNull(dto.copy(origin = PlaceDto("Bad", 91.0, 0.0)).toDomainOrNull())
        assertNull(dto.copy(review = dto.review?.copy(overall = 9)).toDomainOrNull())
    }

    @Test
    fun statusIsStoredAsLowercaseText() {
        assertEquals("cancelled", TripStatus.Cancelled.toDto())
        TripStatus.entries.forEach { assertEquals(it, tripStatusFromDtoOrNull(it.toDto())) }
        assertNull(tripStatusFromDtoOrNull("paused"))
    }
}
