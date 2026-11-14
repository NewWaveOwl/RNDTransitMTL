package transit.launcher

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

/** Requires OS loopback access; run on the target host/CI in addition to the unit checks. */
class LauncherHttpIntegrationTest {
    @Test fun `real loopback readiness accepts success rejects errors and sends GraphQL JSON`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ready") { exchange ->
            val accepted = exchange.requestMethod == "POST" && exchange.requestHeaders.getFirst("Content-Type") == "application/json" &&
                exchange.requestBody.use { it.readAllBytes().toString(Charsets.UTF_8) } == "{ query }"
            val body = "ready".toByteArray()
            exchange.sendResponseHeaders(if (accepted) 200 else 503, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}"
            assertTrue(Readiness().check("$url/ready", "{ query }", valid = { it == "ready" }))
            assertFalse(Readiness().check("$url/ready"))
            assertFalse(Readiness().check("$url/missing"))
        } finally { server.stop(0) }
    }
}
