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

/** Exercises the real HTTP/auth dispatch while replacing only OTP transport with fixed metadata. */
class OtpCapabilitiesHttpTest {
    @Test fun capabilitiesRequireAuthenticationBeforeAnyProviderQueryAndRejectExtraParameters() = runBlocking {
        val calls = AtomicInteger()
        val source = OtpCapabilities({
            calls.incrementAndGet()
            obj("data" to obj("directModes" to obj("enumValues" to listOf(obj("name" to "WALK"))),
                "feeds" to emptyList<Any>(), "serviceTimeRange" to null))
        }, { obj("data" to obj("serverInfo" to obj("version" to "fixture", "internalTransitModelTimeZone" to "America/Toronto"))) })
        val api = TravelTrackerApi(NominatimAddresses({ _, _ -> error("No address request") }),
            TravelTrackerPlanner(source) { error("No route planning request") }, { if (it == "Bearer fixture") "fixture-user" else null })
        val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") { travelTrackerRoutes(api) }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().first().port
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            suspend fun send(path: String, token: Boolean): HttpResponse<String> = withContext(Dispatchers.IO) {
                val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).timeout(Duration.ofSeconds(10))
                if (token) request.header("Authorization", "Bearer fixture")
                client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString())
            }
            assertEquals(401, send("/v1/routes/capabilities", false).statusCode())
            assertEquals(0, calls.get())
            assertEquals(400, send("/v1/routes/capabilities?provider=somewhere", true).statusCode())
            assertEquals(0, calls.get())
            val response = send("/v1/routes/capabilities", true)
            assertEquals(200, response.statusCode())
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            assertEquals("fixture", json.parseToJsonElement(response.body()).field("router").field("version").text())
            assertEquals(1, calls.get())
        } finally { server.stop(0, 1000) }
    }
}
