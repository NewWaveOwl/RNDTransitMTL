package com.example.rnd_transit_mtl.data.group

import com.example.rnd_transit_mtl.data.remote.dto.TripDto
import com.example.rnd_transit_mtl.data.remote.dto.toDto
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.group.GroupJoinCode
import com.example.rnd_transit_mtl.model.ids.GroupId
import com.example.rnd_transit_mtl.model.ids.GroupTripId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import java.net.InetSocketAddress
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SupabaseGroupRepositoryTest {
    private val me = UserId("member-user")
    private val groupId = GroupId("group-1")
    private val sharedId = GroupTripId("shared-route")
    private val walk = TransportId("walk")
    private val origin = Place("Origin", GeoPoint(45.5, -73.6))
    private val destination = Place("Destination", GeoPoint(45.6, -73.5))
    private val hostTrip = Trip(
        id = TripId("host-trip"), ownerId = UserId("host-user"), status = TripStatus.Active,
        title = "Old Port walk", description = "Shared route", imageUrl = "",
        origin = origin, destination = destination,
        legs = listOf(RouteLeg.straightLine(walk, origin.point, destination.point, 600)),
        plannedMinutes = 10,
        selectedTransports = listOf(TripTransportSnapshot(walk, "Walk", false)),
        selectedRoutes = emptyList(), attractionIntensity = 50f,
        createdAt = 1_000L,
    )
    private val groupRow = """{"id":"group-1","join_code":"K7MQ2X","host_id":"host-user",""" +
        """"current_group_trip_id":null,"members":[""" +
        """{"user_id":"member-user","display_name":"Ben","joined_at":"2026-11-27T10:05:00Z"},""" +
        """{"user_id":"host-user","display_name":"Ana","joined_at":"2026-11-27T10:00:00Z"}]}"""

    @Test
    fun everyServerErrorCodeMapsToItsFailure() {
        val expected: Map<String, KClass<out GroupFailure>> = mapOf(
            "GROUP_CODE_INVALID" to GroupFailure.InvalidCode::class,
            "GROUP_ALREADY_JOINED" to GroupFailure.AlreadyInGroup::class,
            "GROUP_NOT_HOST" to GroupFailure.NotHost::class,
            "GROUP_NOT_MEMBER" to GroupFailure.NotMember::class,
            "GROUP_TRIP_INVALID" to GroupFailure.TripUnavailable::class,
            "GROUP_CODE_GENERATION_FAILED" to GroupFailure.CodeGenerationFailed::class,
            "permission denied for table groups" to GroupFailure.Unexpected::class,
        )
        expected.forEach { (text, failure) ->
            assertEquals(failure, SupabaseGroupRepository.failureFor(text)::class, text)
        }
    }

    @Test
    fun anExpiredCodeIsRejectedWithAClearMessage() = runTest {
        val server = FakeServer().apply {
            respond("/rest/v1/rpc/join_group", 400,
                """{"code":"P0001","message":"GROUP_CODE_INVALID","details":null,"hint":null}""")
        }
        server.use { repository ->
            val failure = assertFailsWith<GroupFailure.InvalidCode> {
                repository.joinGroup(GroupJoinCode.parse("K7MQ2X"))
            }
            assertTrue("expired" in failure.message.orEmpty())
        }
    }

    @Test
    fun joinSendsTheNormalizedCodeAndReturnsMembersInJoinOrder() = runTest {
        val server = FakeServer().apply {
            respond("/rest/v1/rpc/join_group", 200, groupRow)
            respond("/rest/v1/groups", 200, "[$groupRow]")
        }
        server.use { repository ->
            val group = repository.joinGroup(GroupJoinCode.parse(" k7mq2x "))

            assertEquals(listOf("Ana", "Ben"), group.members.map { it.displayName })
            assertEquals(2, group.partySize)
            assertTrue(group.isHost(UserId("host-user")))
        }
        assertEquals(JsonPrimitive("K7MQ2X"), server.lastBody("/rest/v1/rpc/join_group")["p_code"])
    }

    @Test
    fun sharingSendsOnlyTheRouteAndPlanningDetails() = runTest {
        val server = FakeServer().apply { respond("/rest/v1/rpc/share_group_trip", 200, """{"id":"shared-route"}""") }
        server.use { repository ->
            assertEquals(sharedId, repository.shareTrip(groupId, hostTrip))
        }
        val trip = server.lastBody("/rest/v1/rpc/share_group_trip")["p_trip"]!!.jsonObject
        assertTrue(trip.keys.all { it in SupabaseGroupRepository.SHARED_TRIP_FIELDS }, "Sent ${trip.keys}")
        assertTrue(trip.keys.containsAll(listOf("title", "origin", "legs", "transports")))
    }

    @Test
    fun startingAGroupTripReturnsTheMembersOwnGeneratedCopy() = runTest {
        val sharedRow = Json.encodeToJsonElement(TripDto.serializer(), hostTrip.toDto()).jsonObject
            .filterKeys { it in SupabaseGroupRepository.SHARED_TRIP_FIELDS }
            .let { JsonObject(it + mapOf("id" to JsonPrimitive(sharedId.value), "group_id" to JsonPrimitive("group-1"))) }
        val server = FakeServer().apply { respond("/rest/v1/rpc/start_group_trip", 200, sharedRow.toString()) }
        server.use(newTripId = { "my-copy" }, now = { 5_000L }) { repository ->
            val mine = repository.startGroupTrip(sharedId)

            assertEquals(TripId("my-copy"), mine.id)
            assertEquals(me, mine.ownerId)
            assertEquals(TripStatus.Generated, mine.status)
            assertEquals(sharedId, mine.groupTripId)
            assertEquals(5_000L, mine.createdAt)
            assertEquals(hostTrip.legs, mine.legs)
            assertEquals(hostTrip.title, mine.title)
        }
    }

    @Test
    fun liveUpdatesRefetchOnEverySignalAndReportReconnectsAndClosure() = runTest {
        val server = FakeServer().apply { respond("/rest/v1/groups", 200, "[$groupRow]") }
        val signals = flowOf(GroupChange.Connected, GroupChange.Disconnected, GroupChange.Connected, GroupChange.Changed)
        val emitted = mutableListOf<GroupSync>()
        server.use(changes = { signals }) { repository ->
            repository.observeGroup(groupId).collect { sync ->
                emitted += sync
                // The host leaves after the reconnect: the next read finds no open group.
                if (emitted.size == 3) server.respond("/rest/v1/groups", 200, "[]")
            }
        }

        assertIs<GroupSync.Live>(emitted[0])
        assertEquals(GroupSync.Reconnecting, emitted[1])
        assertIs<GroupSync.Live>(emitted[2])
        assertEquals(GroupSync.Closed, emitted[3])
    }

    @Test
    fun aFailedLiveReadReportsReconnectingInsteadOfStopping() = runTest {
        val server = FakeServer().apply { respond("/rest/v1/groups", 500, """{"message":"unavailable"}""") }
        server.use(changes = { flowOf(GroupChange.Connected) }) { repository ->
            assertEquals(listOf<GroupSync>(GroupSync.Reconnecting), repository.observeGroup(groupId).toList())
        }
    }

    @Test
    fun anUnreachableServerIsANetworkFailure() = runTest {
        val client = createSupabaseClient("http://127.0.0.1:1", "test-key") { install(Postgrest) }
        try {
            val repository = SupabaseGroupRepository(me, client, changes = { flowOf() })
            assertFailsWith<GroupFailure.Network> { repository.createGroup() }
        } finally {
            client.close()
        }
    }

    /** Local PostgREST stand-in: fixed responses per path, and the last request body per path. */
    private inner class FakeServer {
        private val responses = mutableMapOf<String, Pair<Int, String>>()
        private val bodies = mutableMapOf<String, JsonObject>()

        fun respond(path: String, status: Int, body: String) {
            responses[path] = status to body
        }

        fun lastBody(path: String): JsonObject = checkNotNull(bodies[path]) { "No request to $path" }

        suspend fun use(
            changes: GroupChangeSource = GroupChangeSource { flowOf() },
            newTripId: () -> String = { "trip-id" },
            now: () -> Long = { 0L },
            block: suspend (SupabaseGroupRepository) -> Unit,
        ) {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/rest/v1/") { exchange ->
                val path = exchange.requestURI.path
                val request = exchange.requestBody.use { it.readBytes() }.decodeToString()
                if (request.isNotBlank()) bodies[path] = Json.parseToJsonElement(request).jsonObject
                val (status, body) = responses[path] ?: (404 to """{"message":"no fake for $path"}""")
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
            server.start()
            val client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "test-key") {
                install(Postgrest)
            }
            try {
                block(SupabaseGroupRepository(me, client, changes, newTripId, now))
            } finally {
                client.close()
                server.stop(0)
            }
        }
    }
}
