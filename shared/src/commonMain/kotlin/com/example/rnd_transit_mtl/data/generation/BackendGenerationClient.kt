package com.example.rnd_transit_mtl.data.generation

import androidx.compose.runtime.*
import com.example.rnd_transit_mtl.data.supabase.SupabaseBackend
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.*
import kotlin.time.TimeSource

/** Reuses one cancellable client. Tokens are read at request time after session refresh. */
@Composable
internal fun rememberBackendTripGenerator(endpoint: String?): BackendTripGenerator? {
    val client = remember(endpoint) {
        if (endpoint.isNullOrBlank()) null else HttpClient {
            install(HttpTimeout) { requestTimeoutMillis = 45_000; connectTimeoutMillis = 5_000 }
        }
    }
    DisposableEffect(client) { onDispose { client?.close() } }
    return remember(endpoint, client) {
        client?.let {
            BackendTripGenerator(post = { body ->
                val requestId = Json.parseToJsonElement(body).jsonObject["requestId"]!!.jsonPrimitive.content
                val started = TimeSource.Monotonic.markNow()
                println("RndTransitGeneration request=$requestId http=start")
                val response = it.post(requireNotNull(endpoint)) {
                    header("X-Request-ID", requestId)
                    contentType(ContentType.Application.Json)
                    SupabaseBackend.client.auth.currentSessionOrNull()?.accessToken?.let { token -> bearerAuth(token) }
                    setBody(body)
                }
                println("RndTransitGeneration request=$requestId http=${response.status.value} elapsedMs=${started.elapsedNow().inWholeMilliseconds}")
                if(response.status.value !in 200..299) throw GenerationHttpFailure(response.status.value)
                response.bodyAsText()
            }, diagnostic = { event -> println("RndTransitGeneration $event") })
        }
    }
}
