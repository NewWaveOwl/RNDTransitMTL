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
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real CIO lifecycle; cancelling Generator directly cannot verify a closed HTTP connection. */
class GenerationHttpTest {
    @Test fun disconnectCancelsTheSearchAndAnotherRequestCanFinish() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val zones = FareZones(obj("type" to "FeatureCollection", "features" to listOf("A", "B", "C", "D").map {
            obj("properties" to obj("zone" to it), "geometry" to obj("type" to "Polygon", "coordinates" to
                listOf(listOf(listOf(-74,45),listOf(-73,45),listOf(-73,46),listOf(-74,46),listOf(-74,45)))))
        }))
        val generator = Generator(emptyList(), zones, {
            if (calls.incrementAndGet() == 1) {
                entered.complete(Unit)
                try { awaitCancellation() }
                finally { cancelled.complete(Unit) }
            }
            obj("data" to obj("planConnection" to obj("routingErrors" to emptyList<Any>(), "edges" to emptyList<Any>())))
        }, sample = { listOf(Point(45.51, -73.6)) })
        val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") { generationRoutes(Api(generator)) }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().first().port
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            withContext(Dispatchers.IO) {
                val health = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/health"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString())
                assertEquals(200, health.statusCode())
            }
            val body = obj("origin" to obj("label" to "Confirmed fixture pin", "point" to Point(45.5, -73.6).toJson()),
                "plannedMinutes" to 30, "modes" to listOf("walk")).toString()
            withContext(Dispatchers.IO) {
                Socket("127.0.0.1", port).use { socket ->
                    socket.getOutputStream().apply {
                        write(("POST /v1/trips/generate HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${body.toByteArray().size}\r\n\r\n$body").toByteArray())
                        flush()
                    }
                    withTimeout(10_000) { entered.await() }
                    socket.setSoLinger(true, 0)
                }
            }
            withTimeout(5_000) { cancelled.await() }
            val next = withContext(Dispatchers.IO) {
                client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/v1/trips/generate"))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
            }
            assertEquals(200, next.statusCode())
            assertEquals("no_trip", json.parseToJsonElement(next.body()).field("status").text())
        } finally { server.stop(0, 1000) }
    }
}
