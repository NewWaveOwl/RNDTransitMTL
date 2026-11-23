package transit

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Verifies actual transport retries rather than assuming a fake provider proves HTTP engine behavior. */
class NominatimHttpTest {
    @Test fun http408DoesNotCauseAnUnqueuedRetry() = verifyOneRequest(408)
    @Test fun http503WithZeroRetryAfterDoesNotCauseAnUnqueuedRetry() = verifyOneRequest(503)

    private fun verifyOneRequest(status: Int) = runBlocking {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/search") { exchange ->
            calls.incrementAndGet()
            assertTrue(exchange.requestHeaders.getFirst("User-Agent").contains("TravelTracker"))
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Retry-After", "0")
            val body = "[]".toByteArray()
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            NominatimHttp("http://127.0.0.1:${server.address.port}", "TravelTracker/1.0 (+https://github.com/NewWaveOwl/TravelTracker)").use { client ->
                val result = client.fetch("search", mapOf("q" to "City Hall"))
                assertEquals(status, result.status)
                if (status == 503) assertEquals("1", result.retryAfter)
                assertEquals(1, calls.get())
            }
        } finally { server.stop(0) }
    }
}
