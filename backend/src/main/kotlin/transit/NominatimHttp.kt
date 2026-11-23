package transit

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.net.URI

/** Dedicated address transport never forwards traveller credentials or follows provider redirects.
 * @param baseUrl Operator-configured Nominatim instance, switchable without updating clients.
 * @param userAgent Application identification required by the public service's usage policy.
 */
internal class NominatimHttp(baseUrl: String, private val userAgent: String) : AutoCloseable {
    private val base = baseUrl.trimEnd('/')
    private val client = HttpClient(OkHttp) {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 4000; connectTimeoutMillis = 2000; socketTimeoutMillis = 4000 }
        engine {
            config {
                retryOnConnectionFailure(false)
                // OkHttp can follow 503 + Retry-After:0 even when connection retries are off.
                // Let the application queue apply every backoff, including this provider case.
                addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    if (response.code == 503 && response.header("Retry-After")?.toLongOrNull() == 0L)
                        response.newBuilder().header("Retry-After", "1").build()
                    else response
                }
            }
        }
    }

    init {
        val uri = URI(base)
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.scheme == "https" || uri.scheme == "http" && uri.host in listOf("localhost", "127.0.0.1", "[::1]"))) {
            "NOMINATIM_URL requires HTTPS or loopback HTTP, without credentials or query parameters."
        }
        require(userAgent.length in 10..200 && userAgent.all { it.code in 32..126 } && userAgent.contains("TravelTracker")) {
            "NOMINATIM_USER_AGENT must identify TravelTracker and contain no control characters."
        }
    }

    /**
     * Executes one bounded request; the shared queue owns spacing, caching and provider backoff.
     * @param path Fixed search or reverse operation.
     * @param parameters Validated public lookup fields, never authorization data.
     * @return Status, bounded response and Retry-After for the queue.
     */
    suspend fun fetch(path: String, parameters: Map<String, String>): NominatimAddresses.Response {
        require(path in listOf("search", "reverse"))
        val response = client.get("$base/$path") {
            header(HttpHeaders.UserAgent, userAgent)
            header(HttpHeaders.Accept, "application/json")
            url { parameters.forEach { (name, value) -> this.parameters.append(name, value) } }
        }
        val channel = response.bodyAsChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val size = channel.readAvailable(buffer, 0, buffer.size)
            if (size < 0) break
            if (output.size() + size > 131_072) throw ProviderError("Address response too large.")
            output.write(buffer, 0, size)
        }
        return NominatimAddresses.Response(response.status.value, output.toString(Charsets.UTF_8.name()), response.headers[HttpHeaders.RetryAfter])
    }

    override fun close() = client.close()
}
