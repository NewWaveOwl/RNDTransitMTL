package transit.launcher

import java.net.URI
import java.util.Base64
import kotlinx.serialization.json.*

internal const val defaultNominatimUrl = "https://nominatim.openstreetmap.org"
internal const val defaultNominatimUserAgent = "TravelTracker/1.0 (+https://github.com/NewWaveOwl/TravelTracker)"

/**
 * Validates operator-owned address settings without contacting a provider or changing routing data.
 * @return Completion when the optional TravelTracker auth pair and Nominatim destination are safe.
 */
internal fun ServerConfig.validateTravelTrackerSettings() {
    if (travelTrackerSupabaseUrl.isNotBlank() || travelTrackerSupabaseKey.isNotBlank()) {
        val origin = runCatching { URI(travelTrackerSupabaseUrl) }.getOrNull()
        require(origin != null && origin.scheme == "https" && !origin.host.isNullOrBlank() &&
            origin.userInfo == null && origin.rawQuery == null && origin.rawFragment == null && origin.path in listOf("", "/")) {
            "TravelTracker Supabase URL must be its HTTPS project origin, without /auth/v1."
        }
        requirePublicSupabaseKey(travelTrackerSupabaseKey)
    }
    val provider = runCatching { URI(nominatimUrl) }.getOrNull()
    require(provider != null && !provider.host.isNullOrBlank() && provider.userInfo == null &&
        provider.rawQuery == null && provider.rawFragment == null &&
        (provider.scheme == "https" || provider.scheme == "http" && provider.host in listOf("127.0.0.1", "localhost", "[::1]"))) {
        "Nominatim URL must use HTTPS, or HTTP on loopback for a local instance."
    }
    require(nominatimUserAgent.length in 10..200 && nominatimUserAgent.contains("TravelTracker") && nominatimUserAgent.all { it.code in 32..126 } &&
        !nominatimUserAgent.startsWith("Java/", true) && !nominatimUserAgent.startsWith("okhttp/", true)) {
        "Nominatim User-Agent must identify TravelTracker in 10-200 printable characters."
    }
}

/**
 * Prevents an operator from putting privileged project credentials into public client settings.
 * @param key A modern publishable key or a legacy anon JWT.
 */
private fun requirePublicSupabaseKey(key: String) {
    val legacyRole = runCatching {
        val payload = key.split('.').takeIf { it.size == 3 }?.get(1) ?: return@runCatching null
        Json.parseToJsonElement(Base64.getUrlDecoder().decode(payload).toString(Charsets.UTF_8))
            .jsonObject["role"]?.jsonPrimitive?.content
    }.getOrNull()
    require(key.startsWith("sb_publishable_") && key.length > "sb_publishable_".length || legacyRole == "anon") {
        "TravelTracker requires a publishable or legacy anon key; secret and service-role keys are not allowed."
    }
}

/**
 * Uses the existing project only when no separate TravelTracker project was configured.
 * @return Public identifiers and provider settings passed to the one shared Ktor process.
 */
internal fun ServerConfig.travelTrackerEnvironment(): Map<String, String> = mapOf(
    "TRAVELTRACKER_SUPABASE_URL" to travelTrackerSupabaseUrl.ifBlank { supabaseUrl },
    "TRAVELTRACKER_SUPABASE_KEY" to travelTrackerSupabaseKey.ifBlank { supabaseKey },
    "NOMINATIM_URL" to nominatimUrl,
    "NOMINATIM_USER_AGENT" to nominatimUserAgent,
)
