package transit

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Real HTTP coverage proves selected-point input, authorization dispatch and safe preview failure. */
class WalkingCyclingHttpTest {
    @Test fun protectedWalkingPreviewAcceptsTripZoneAndRejectsInvalidInputBeforeProviderIo() = runBlocking {
        val calls = AtomicInteger()
        var unavailable = false
        val planner = TravelTrackerPlanner { request ->
            calls.incrementAndGet()
            if (unavailable) error("private provider diagnostic")
            assertEquals("WALK", request["variables"].field("modes").field("direct").items().single().text())
            obj("data" to obj("planConnection" to obj("routingErrors" to emptyList<Any>(),
                "edges" to listOf(obj("node" to obj("duration" to 600, "legs" to listOf(obj(
                    "mode" to "WALK", "distance" to 1400, "duration" to 600, "realTime" to false,
                    "start" to obj("scheduledTime" to "2026-11-23T15:00:00Z"),
                    "end" to obj("scheduledTime" to "2026-11-23T15:10:00Z"),
                    "legGeometry" to obj("length" to 2, "points" to "_mwtGvz_\u0060Mo}@n}@")))))))))
        }
        val api = TravelTrackerApi(NominatimAddresses({ _, _ -> error("No geocoding request.") }), planner,
            { if (it == "Bearer fixture") "fixture-user" else null })
        val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") { travelTrackerRoutes(api) }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().first().port
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            suspend fun send(zone: String, signedIn: Boolean): HttpResponse<String> = withContext(Dispatchers.IO) {
                val body = obj("origin" to obj("lat" to 45.5088, "lon" to -73.5686), "destination" to obj("lat" to 45.5188, "lon" to -73.5786),
                    "mode" to "pedestrian", "departureTime" to "2026-11-23T10:00:00-05:00", "timeZone" to zone)
                val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/v1/routes/plan")).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                if (signedIn) request.header("Authorization", "Bearer fixture")
                client.send(request.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString())
            }
            assertEquals(401, send("America/Toronto", false).statusCode())
            assertEquals(400, send("Unknown/Zone", true).statusCode())
            assertEquals(0, calls.get())
            val response = send("America/Toronto", true)
            assertEquals(200, response.statusCode())
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            val preview = json.parseToJsonElement(response.body())
            assertEquals("America/Toronto", preview.field("timeZone").text())
            assertEquals("WALK", preview.field("itineraries").items().single().field("legs").items().single().field("mode").text())
            unavailable = true
            val failure = send("America/Toronto", true)
            assertEquals(503, failure.statusCode())
            assertFalse(failure.body().contains("private provider diagnostic"))
            assertEquals("unavailable", json.parseToJsonElement(failure.body()).field("status").text())
        } finally { server.stop(0, 1000) }
    }
}
