package transit

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

/** Verifies that an authenticated out-of-map car request remains a usable no-route HTTP result. */
class LongCarRoutingHttpTest {
    @Test fun outsideStreetMapReturnsCoverageAndAProviderOutageStillReturns503() = runBlocking {
        val providerOutage = AtomicBoolean()
        val calls = AtomicInteger()
        val planner = TravelTrackerPlanner { query ->
            calls.incrementAndGet()
            assertEquals("CAR", query["variables"].field("modes").field("direct").items().single().text())
            assertEquals(46.3432, query["variables"].field("destinationLat").number()!!, 0.0)
            if (providerOutage.get()) error("private provider diagnostic")
            obj("data" to obj("planConnection" to obj("routingErrors" to listOf(
                obj("code" to "OUTSIDE_BOUNDS", "inputField" to "TO")), "edges" to emptyList<Any>())))
        }
        val api = TravelTrackerApi(NominatimAddresses({ _, _ -> error("No geocoding request.") }), planner,
            { if (it == "Bearer fixture") "verified-fixture-user" else null })
        val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") { travelTrackerRoutes(api) }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().first().port
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            suspend fun send(signedIn: Boolean): HttpResponse<String> = withContext(Dispatchers.IO) {
                val body = obj("origin" to obj("lat" to 45.5088, "lon" to -73.5686),
                    "destination" to obj("lat" to 46.3432, "lon" to -72.5477), "mode" to "car",
                    "departureTime" to "2026-11-23T10:00:00-05:00", "timeZone" to "America/Toronto")
                val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/v1/routes/plan"))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                if (signedIn) request.header("Authorization", "Bearer fixture")
                client.send(request.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString())
            }
            assertEquals(401, send(false).statusCode())
            assertEquals(0, calls.get())
            val response = send(true)
            assertEquals(200, response.statusCode())
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            val result = json.parseToJsonElement(response.body())
            assertEquals("no_route", result.field("status").text())
            assertEquals("outside_coverage", result.field("reason").text())
            assertTrue(result.field("itineraries").items().isEmpty())
            providerOutage.set(true)
            val failure = send(true)
            assertEquals(503, failure.statusCode())
            assertEquals("unavailable", json.parseToJsonElement(failure.body()).field("status").text())
            assertFalse(failure.body().contains("private provider diagnostic"))
        } finally { server.stop(0, 1000) }
    }
}
