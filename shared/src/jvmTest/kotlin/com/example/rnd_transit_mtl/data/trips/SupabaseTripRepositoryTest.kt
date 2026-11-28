package com.example.rnd_transit_mtl.data.trips

import com.example.rnd_transit_mtl.data.authentication.AccountSession
import com.example.rnd_transit_mtl.data.authentication.SignupRejected
import com.example.rnd_transit_mtl.data.authentication.SupabaseAuthRepository
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
import com.example.rnd_transit_mtl.state.trip.TripsStore
import com.russhwolf.settings.PropertiesSettings
import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import java.net.InetSocketAddress
import java.util.Properties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun signupWithNoGuestSavesCanClaimLaterSaves() {
        val transfers = GuestTripTransfers(ChunkedTripSettings(PropertiesSettings(Properties())))
        val guest = TripsStore()
        val email = "guest@example.com"
        val token = transfers.stage(email, guest)
        val saved = pending.copy(ownerId = null)
        guest.restoreFinalTrip(saved)

        val session = AccountSession.SignedIn(owner.value, email, token)
        assertEquals(listOf(saved), transfers.forAccount(session, guest).single().trips)
    }

    @Test
    fun rejectedSignupReleasesSavesForCorrectedEmail() = runTest {
        val storage = ChunkedTripSettings(PropertiesSettings(Properties()))
        val saved = pending.copy(ownerId = null)
        val guest = TripsStore().apply { restoreFinalTrip(saved) }
        val transfers = GuestTripTransfers(storage)
        assertFailsWith<SignupRejected> {
            transfers.signUp("wrong@example.com", guest) {
                throw SignupRejected(IllegalArgumentException("Invalid email"))
            }
        }

        val reopened = GuestTripTransfers(storage)
        val email = "guest@example.com"
        val token = reopened.stage(email, guest)
        val session = AccountSession.SignedIn(owner.value, email, token)
        assertEquals(listOf(saved), reopened.forAccount(session, guest).single().trips)
    }

    @Test
    fun acceptedSignupWithTypoIsReplacedByCorrectedEmail() = runTest {
        val storage = ChunkedTripSettings(PropertiesSettings(Properties()))
        val saved = pending.copy(ownerId = null)
        val guest = TripsStore().apply { restoreFinalTrip(saved) }
        val transfers = GuestTripTransfers(storage)
        val typo = "jim@gmial.com"
        val email = "jim@gmail.com"
        var typoToken = ""
        var correctedToken = ""
        assertTrue(transfers.signUp(typo, guest) {
            typoToken = it
            true
        })
        assertTrue(transfers.signUp(email, guest) {
            correctedToken = it
            true
        })

        val reopened = GuestTripTransfers(storage)
        val restored = TripsStore().also(reopened::restoreGuest)
        assertTrue(reopened.forAccount(AccountSession.SignedIn("other-owner", typo, typoToken), restored).isEmpty())
        val session = AccountSession.SignedIn(owner.value, email, correctedToken)
        assertEquals(listOf(saved), reopened.forAccount(session, restored).single().trips)
    }

    @Test
    fun unreadableGuestJournalAllowsNewSaves() {
        val storage = ChunkedTripSettings(PropertiesSettings(Properties()))
        storage.write("transit_guest_trip_transfers", """{"guest":[{}]}""")
        val transfers = GuestTripTransfers(storage)
        val guest = TripsStore().also(transfers::restoreGuest)
        assertTrue(guest.completedTrips.isEmpty())

        val saved = pending.copy(ownerId = null)
        transfers.saveGuest(listOf(saved))
        GuestTripTransfers(storage).restoreGuest(guest)
        assertEquals(saved, guest.findInHistory(saved.id))
    }

    @Test
    fun signupCarriesTheTransferTokenThroughAuthentication() = runTest {
        val email = "guest@example.com"
        val token = "20000000-0000-4000-8000-000000000001"
        val user = """{"id":"owner","aud":"authenticated","email":"$email","user_metadata":{"$GUEST_TRANSFER_TOKEN":"$token"}}"""
        val session = """{"access_token":"test-access","refresh_token":"test-refresh","expires_in":3600,"token_type":"bearer","user":$user}"""
        for (confirmEmail in listOf(false, true)) {
            var signupBody = ""
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/auth/v1/signup") { exchange ->
                signupBody = exchange.requestBody.bufferedReader().use { it.readText() }
                val body = (if (confirmEmail) user else session).toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.createContext("/auth/v1/token") { exchange ->
                val body = session.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
            val client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "test-key") {
                install(Auth) {
                    autoLoadFromStorage = false
                    autoSaveToStorage = false
                    alwaysAutoRefresh = false
                }
            }
            try {
                val auth = SupabaseAuthRepository(client.auth)
                assertEquals(confirmEmail, auth.signUp(email, "guest-password", token))
                val data = Json.parseToJsonElement(signupBody).jsonObject.getValue("data").jsonObject
                assertEquals(token, data.getValue(GUEST_TRANSFER_TOKEN).jsonPrimitive.content)
                if (confirmEmail) {
                    assertFalse(auth.session.first() is AccountSession.SignedIn)
                    auth.signIn(email, "guest-password")
                }
                assertEquals(AccountSession.SignedIn(owner.value, email, token), auth.session.first())
            } finally {
                client.close()
                server.stop(0)
            }
        }
    }

    @Test
    fun emailConfirmationImportsCurrentSavedReviewsAndOmitsDeletedTrips() {
        val storage = ChunkedTripSettings(PropertiesSettings(Properties()))
        val original = pending.copy(ownerId = null, review = TripReview(4, comment = "Original"))
        val deleted = original.copy(id = TripId("deleted"))
        val guest = TripsStore().apply { restoreGuestHistory(listOf(original, deleted)) }
        val transfers = GuestTripTransfers(storage)
        val email = "guest@example.com"
        val token = assertNotNull(transfers.stage(email, guest))
        val edited = original.copy(review = TripReview(5, comment = "Edited"))
        val later = original.copy(id = TripId("saved-later"))
        guest.restoreGuestHistory(listOf(edited, later))

        val session = AccountSession.SignedIn(owner.value, email, token)
        val claimed = transfers.forAccount(session, guest).single()
        assertEquals(listOf(edited, later), claimed.trips)

        val reopened = GuestTripTransfers(storage)
        val restored = TripsStore().also(reopened::restoreGuest)
        val otherEmail = "another@example.com"
        val otherToken = reopened.stage(otherEmail, restored)
        val otherSession = AccountSession.SignedIn("other-owner", otherEmail, otherToken)
        assertTrue(reopened.forAccount(otherSession, restored).isEmpty())
        assertEquals(claimed, reopened.forAccount(session, restored).single())
    }

    @Test
    fun signupTransferRetriesAfterReopeningAndOnlyForTheOriginalAccount() = runTest {
        val storage = ChunkedTripSettings(PropertiesSettings(Properties()))
        val reviewed = pending.copy(ownerId = null, review = TripReview(5, comment = "Guest"))
        val guest = TripsStore().apply { restoreFinalTrip(reviewed) }
        val transfers = GuestTripTransfers(storage)
        val email = "guest@example.com"
        val token = assertNotNull(transfers.stage(email, guest))
        assertEquals(token, transfers.stage(email, guest))
        assertEquals(reviewed, guest.findInHistory(reviewed.id))

        val restarted = GuestTripTransfers(storage)
        val restored = TripsStore().also(restarted::restoreGuest)
        val signedIn = AccountSession.SignedIn(owner.value, email, token)
        assertTrue(restarted.forAccount(signedIn.copy(guestTransferToken = null), restored).isEmpty())
        assertTrue(restarted.forAccount(signedIn.copy(email = "another@example.com"), restored).isEmpty())
        val transfer = restarted.forAccount(signedIn, restored).single()
        assertNull(restored.findInHistory(reviewed.id))

        val requestBodies = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/v1/rpc/import_guest_trips") { exchange ->
            requestBodies += exchange.requestBody.bufferedReader().use { it.readText() }
            exchange.sendResponseHeaders(if (requestBodies.size == 1) 503 else 204, -1)
            exchange.close()
        }
        server.start()
        val client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "test-key") {
            install(Postgrest)
        }
        try {
            val repository = SupabaseTripRepository(owner, client)
            assertFailsWith<Exception> { repository.importGuestTrips(transfer) }
            val reopened = GuestTripTransfers(storage)
            assertTrue(reopened.forAccount(signedIn.copy(userId = "other-owner"), restored).isEmpty())
            val retry = reopened.forAccount(signedIn.copy(guestTransferToken = null), restored).single()
            assertEquals(transfer, retry)
            repository.importGuestTrips(retry)
            reopened.acknowledge(retry)
            assertTrue(GuestTripTransfers(storage).forAccount(signedIn, restored).isEmpty())
            assertEquals(2, requestBodies.size)
            assertEquals(requestBodies[0], requestBodies[1])
            val payload = Json.parseToJsonElement(requestBodies.last()).jsonObject
            assertEquals(owner.value, payload.getValue("p_owner").jsonPrimitive.content)
            assertEquals(token, payload.getValue("p_transfer").jsonPrimitive.content)
            val trip = payload.getValue("p_trips").jsonArray.single().jsonObject
            assertEquals(reviewed.id.value, trip.getValue("id").jsonPrimitive.content)
            assertEquals("Guest", trip.getValue("review").jsonObject.getValue("comment").jsonPrimitive.content)
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
