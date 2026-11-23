package transit

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Real HTTP checks prove dispatch, authentication-before-provider and cancellation of queued work. */
class TravelTrackerHttpTest {
    @Test fun explicitTravelTrackerProjectIsNeverMixedWithRndFallbackKey() {
        assertEquals("rnd-url" to "rnd-key", travelTrackerProject(null, "", "rnd-url", "rnd-key"))
        assertEquals("tracker-url" to "tracker-key", travelTrackerProject("tracker-url", "tracker-key", "rnd-url", "rnd-key"))
        try { travelTrackerProject("tracker-url", null, "rnd-url", "rnd-key"); fail() } catch (_: IllegalArgumentException) { }
        try { travelTrackerProject(null, "tracker-key", "rnd-url", "rnd-key"); fail() } catch (_: IllegalArgumentException) { }
    }

    @Test fun excessRequestsAreRejectedBeforeOpeningMoreAuthConnections() = runBlocking {
        val entered = AtomicInteger()
        val allEntered = CompletableDeferred<Unit>()
        val addresses = NominatimAddresses({ _, _ -> error("No provider request") })
        val api = TravelTrackerApi(addresses, TravelTrackerPlanner { error("No route request") }, {
            if (entered.incrementAndGet() == 8) allEntered.complete(Unit)
            awaitCancellation()
        })
        val pending = (1..8).map { async { api.search("Bearer fixture", "City Hall") } }
        allEntered.await()
        val rejected = api.search("Bearer excess", "City Hall")
        assertEquals(503, rejected.first)
        assertEquals(8, entered.get())
        pending.forEach { it.cancelAndJoin() }
        assertEquals(0, addresses.requests.get())
    }

    @Test fun protectedHttpRoutesReturnCandidatesAndSafeFailuresWithoutProviderTokens() = runBlocking {
        val calls = AtomicInteger()
        val addresses = NominatimAddresses({ path, parameters ->
            calls.incrementAndGet()
            assertEquals("search", path)
            assertEquals("City Hall", parameters["q"])
            assertFalse(parameters.keys.any { it.equals("authorization", true) })
            NominatimAddresses.Response(200, """[{"display_name":"City Hall, Montreal","lat":"45.5","lon":"-73.5"}]""")
        })
        val api = TravelTrackerApi(addresses, TravelTrackerPlanner { error("Unexpected routing request") }, { if (it == "Bearer fixture") "verified-fixture-user" else null })
        val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") { travelTrackerRoutes(api) }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().first().port
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            suspend fun send(path: String, token: Boolean = false, body: String? = null): HttpResponse<String> = withContext(Dispatchers.IO) {
                val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).timeout(Duration.ofSeconds(10))
                if (token) builder.header("Authorization", "Bearer fixture")
                if (body == null) builder.GET() else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
                client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            }
            assertEquals(401, send("/v1/places/search?q=City%20Hall").statusCode())
            assertEquals(0, calls.get())
            val result = send("/v1/places/search?q=City%20Hall", true)
            assertEquals(200, result.statusCode())
            assertEquals("no-store", result.headers().firstValue("Cache-Control").orElse(""))
            assertEquals("City Hall, Montreal", json.parseToJsonElement(result.body()).field("candidates").items().single().field("label").text())
            assertEquals(400, send("/v1/places/search?q=Hall&q=City", true).statusCode())
            assertEquals(400, send("/v1/places/reverse?lat=NaN&lon=-73", true).statusCode())
            assertEquals(400, send("/v1/routes/plan", true, "{invalid json").statusCode())
            assertEquals(1, calls.get())
            val health = send("/health/traveltracker")
            assertEquals(200, health.statusCode())
            assertFalse(health.body().contains("fixture"))
            assertFalse(health.body().contains("City Hall"))
        } finally { server.stop(0, 1000) }
    }

    @Test fun disconnectCancelsAddressLookupAndReleasesSharedQueue() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val addresses = NominatimAddresses({ _, _ ->
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        })
        val api = TravelTrackerApi(addresses, TravelTrackerPlanner { error("No route request") }, { "verified-fixture-user" })
        val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") { travelTrackerRoutes(api) }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().first().port
            withContext(Dispatchers.IO) {
                Socket("127.0.0.1", port).use { socket ->
                    socket.getOutputStream().apply {
                        write("GET /v1/places/search?q=City%20Hall HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                        flush()
                    }
                    withTimeout(5000) { entered.await() }
                    socket.setSoLinger(true, 0)
                }
            }
            withTimeout(5000) { cancelled.await() }
            assertEquals(0, addresses.queued)
        } finally { server.stop(0, 1000) }
    }

    @Test fun providerErrorsAreSanitizedAndAuthenticationFailureNeverLooksLikeNoMatch() = runBlocking {
        val addresses = NominatimAddresses({ _, _ -> error("private provider address and bearer secret") })
        val api = TravelTrackerApi(addresses, TravelTrackerPlanner { error("No route request") }, { "verified-user" })
        val failure = api.search("Bearer fixture", "City Hall")
        assertEquals(503, failure.first)
        assertFalse(failure.second.toString().contains("private"))
        val authUnavailable = TravelTrackerApi(addresses, TravelTrackerPlanner { error("No route request") }, { error("private auth diagnostic") })
        assertEquals(503, authUnavailable.search("Bearer fixture", "City Hall").first)
    }

    @Test fun unconfiguredAndPrivilegedAuthConfigurationFailClosed() = runBlocking {
        Upstream(false).use { upstream ->
            assertNull(travelTrackerSession(null, null, upstream)(null))
            try { travelTrackerSession(null, null, upstream)("Bearer unverified"); fail() }
            catch (_: TravelTrackerAuthConfigurationException) { }
            try { travelTrackerSession("https://example.supabase.co", "sb_secret_private", upstream); fail() } catch (_: IllegalArgumentException) { }
            try { travelTrackerSession("http://example.supabase.co", "sb_publishable_example", upstream); fail() } catch (_: IllegalArgumentException) { }
            val verifier = travelTrackerSession("https://example.supabase.co", "sb_publishable_example", upstream)
            assertNull(verifier(null))
            assertNull(verifier("invalid-header"))
        }
    }
}
