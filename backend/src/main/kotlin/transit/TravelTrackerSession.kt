package transit

import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import java.net.URI
import java.util.UUID
import java.util.Base64

/**
 * Selects one complete project pair; a partial override must never borrow another project's key.
 * @param project Explicit TravelTracker project or blank.
 * @param key Explicit TravelTracker public key or blank.
 * @param fallbackProject Existing RNDTransit project.
 * @param fallbackKey Existing RNDTransit public key.
 * @return A matching explicit pair, or the complete existing-project fallback.
 */
internal fun travelTrackerProject(project: String?, key: String?, fallbackProject: String?, fallbackKey: String?): Pair<String?, String?> {
    require(project.isNullOrBlank() == key.isNullOrBlank()) { "Configure both TravelTracker project URL and publishable key, or leave both blank." }
    return if (project.isNullOrBlank()) fallbackProject to fallbackKey else project to key
}

/**
 * Uses the application's selected Auth project rather than assuming RNDTransit tokens are compatible.
 * @param projectUrl TravelTracker project origin, with explicit fallback selected at composition.
 * @param publishableKey Public project key, never a service-role key.
 * @param upstream Redirect-disabled transport used only for verified Auth user lookups.
 * @return A verifier that fails closed when authentication is not configured.
 */
internal fun travelTrackerSession(projectUrl: String?, publishableKey: String?, upstream: Upstream): suspend (String?) -> String? {
    require(projectUrl.isNullOrBlank() == publishableKey.isNullOrBlank()) { "TravelTracker authentication needs both project URL and publishable key." }
    if (projectUrl.isNullOrBlank()) return { null }
    val origin = URI(projectUrl)
    require(origin.scheme == "https" && origin.host != null && origin.userInfo == null && origin.query == null && origin.fragment == null &&
        origin.path.orEmpty() in listOf("", "/")) { "TravelTracker authentication requires an HTTPS project origin." }
    val validPublicKey = publishableKey!!.startsWith("sb_publishable_") || runCatching {
        val parts = publishableKey.split('.')
        parts.size == 3 && json.parseToJsonElement(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).field("role").text() == "anon"
    }.getOrDefault(false)
    require(validPublicKey && publishableKey.length <= 8192 && publishableKey.none(Char::isISOControl)) { "Use a publishable authentication key, never a secret or service-role key." }
    return { token ->
        if (token?.startsWith("Bearer ") != true || token.length > 8192) null else {
            val response = withTimeout(4000) { upstream.request(origin.resolve("/auth/v1/user").toString(),
                headers = mapOf("Authorization" to token, "apikey" to publishableKey)) }
            when {
                response.status.value in listOf(401, 403) -> null
                !response.status.isSuccess() -> throw ProviderError("Authentication service unavailable.")
                else -> {
                    val id = json.parseToJsonElement(response.bodyAsText()).field("id").text()
                        ?: throw ProviderError("Invalid authentication response.")
                    UUID.fromString(id).toString()
                }
            }
        }
    }
}
