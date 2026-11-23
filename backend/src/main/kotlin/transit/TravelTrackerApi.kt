package transit

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.HttpRequestLifecycle
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream

/** Protected, app-specific lookup and route operations; provider suggestions never write saved data.
 * @param addresses One shared public-Nominatim queue for the entire process.
 * @param planner Independent point-to-point OTP planner.
 * @param authorize Resolves identities through the configured TravelTracker authentication project.
 */
internal class TravelTrackerApi(
    private val addresses: NominatimAddresses,
    private val planner: TravelTrackerPlanner,
    private val authorize: suspend (String?) -> String?,
) {
    private val routeCapacity = Semaphore(2)
    private val requestCapacity = Semaphore(8)

    /** @return Aggregate operational counts without public addresses, coordinates or user identifiers. */
    fun health() = obj("status" to "ready", "addressProvider" to "Public Nominatim", "addressRequests" to addresses.requests.get(),
        "addressCacheHits" to addresses.cacheHits.get(), "addressQueued" to addresses.queued, "routeRequests" to planner.requests.get())

    /**
     * Authorizes before reading or calling a provider and returns sanitized dependency failures.
     * @param authorization User bearer header, sent only to the configured Auth project.
     * @param work Validated app operation, never a generic proxy URL.
     * @return Public status and response body.
     */
    private suspend fun authorized(authorization: String?, work: suspend () -> Pair<Int, JsonObject>): Pair<Int, JsonObject> {
        if (!requestCapacity.tryAcquire()) return 503 to obj("status" to "unavailable", "retryAfterSeconds" to 1,
            "message" to "The service is busy. Keep your manual entry and try again shortly.")
        return try {
            withTimeout(7000) {
                if (authorize(authorization) == null) 401 to obj("status" to "unauthorized") else work()
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            503 to obj("status" to "unavailable", "message" to "The service timed out. Keep your manual entry and try again.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (configuration: TravelTrackerAuthConfigurationException) {
            503 to obj("status" to "authentication_unavailable", "message" to configuration.message)
        }
        catch (invalid: InputError) { 400 to obj("status" to "invalid_input", "message" to invalid.message) }
        catch (_: Exception) { 503 to obj("status" to "unavailable", "message" to "The service is unavailable. Keep your manual entry and try again.") }
        finally { requestCapacity.release() }
    }

    /** @param token Bearer header. @param query Explicitly submitted address. @return Candidate response. */
    suspend fun search(token: String?, query: String) = authorized(token) { addresses.search(query) }

    /** @param token Bearer header. @param lat Selected latitude. @param lon Selected longitude. @return Editable suggestion. */
    suspend fun reverse(token: String?, lat: String?, lon: String?) = authorized(token) {
        addresses.reverse(lat?.toDoubleOrNull() ?: throw InputError("Invalid latitude."), lon?.toDoubleOrNull() ?: throw InputError("Invalid longitude."))
    }

    /** @param token Bearer header. @param contentType Expected JSON. @param read Bounded caller body. @return Preview candidates only. */
    suspend fun plan(token: String?, contentType: String?, read: suspend () -> String) = authorized(token) {
        checkInput(contentType?.substringBefore(';')?.trim() == "application/json", "Send application/json.")
        if (!routeCapacity.tryAcquire()) return@authorized 503 to obj("status" to "unavailable", "message" to "Route planning is busy. Try again shortly.")
        try {
            val body = try { json.parseToJsonElement(read()) as? JsonObject } catch (_: kotlinx.serialization.SerializationException) { null }
                ?: throw InputError("Send a valid route request.")
            200 to planner.plan(body)
        } finally { routeCapacity.release() }
    }
}

/**
 * Adds app-specific protected operations alongside the existing random generation API.
 * @param api Single shared queue and planner, never constructed per request.
 */
internal fun Application.travelTrackerRoutes(api: TravelTrackerApi) {
    routing {
        get("/health/traveltracker") { call.travelTrackerResponse(200 to api.health()) }
        route("/v1/places") {
            install(HttpRequestLifecycle) { cancelCallOnClose = true }
            get("/search") {
                val query = call.request.queryParameters
                if (query.names() != setOf("q") || query.getAll("q")?.size != 1)
                    call.travelTrackerResponse(400 to obj("status" to "invalid_input", "message" to "Supply one address query."))
                else call.travelTrackerResponse(api.search(call.request.headers[HttpHeaders.Authorization], query["q"].orEmpty()))
            }
            get("/reverse") {
                val query = call.request.queryParameters
                if (query.names() != setOf("lat", "lon") || query.getAll("lat")?.size != 1 || query.getAll("lon")?.size != 1)
                    call.travelTrackerResponse(400 to obj("status" to "invalid_input", "message" to "Supply one latitude and longitude."))
                else call.travelTrackerResponse(api.reverse(call.request.headers[HttpHeaders.Authorization], query["lat"], query["lon"]))
            }
        }
        route("/v1/routes") {
            install(HttpRequestLifecycle) { cancelCallOnClose = true }
            post("/plan") {
                call.travelTrackerResponse(api.plan(call.request.headers[HttpHeaders.Authorization], call.request.headers[HttpHeaders.ContentType]) {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(2048)
                    val input = call.receiveChannel()
                    while (true) {
                        val size = input.readAvailable(buffer, 0, buffer.size)
                        if (size < 0) break
                        checkInput(output.size() + size <= 4096, "Request is too large.")
                        output.write(buffer, 0, size)
                    }
                    output.toString(Charsets.UTF_8.name())
                })
            }
        }
    }
}

private suspend fun ApplicationCall.travelTrackerResponse(response: Pair<Int, JsonObject>) {
    this.response.headers.append(HttpHeaders.CacheControl, "no-store")
    response.second["retryAfterSeconds"].number()?.toLong()?.let { this.response.headers.append(HttpHeaders.RetryAfter, it.toString()) }
    respondText(response.second.toString(), ContentType.Application.Json, HttpStatusCode.fromValue(response.first))
}
