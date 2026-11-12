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

/** Reuses one cancellable client. Tokens are read at request time after session refresh. */
@Composable
internal fun rememberBackendTripGenerator(endpoint: String?): BackendTripGenerator? {
    val client = remember(endpoint) {
        if (endpoint.isNullOrBlank()) null else HttpClient {
            install(HttpTimeout) { requestTimeoutMillis = 25_000; connectTimeoutMillis = 5_000 }
        }
    }
    DisposableEffect(client) { onDispose { client?.close() } }
    return remember(endpoint, client) {
        client?.let {
            BackendTripGenerator(post = { body ->
                val response = it.post(requireNotNull(endpoint)) {
                    contentType(ContentType.Application.Json)
                    SupabaseBackend.client.auth.currentSessionOrNull()?.accessToken?.let { token -> bearerAuth(token) }
                    setBody(body)
                }
                response.bodyAsText().also { text -> require(text.length <= 2_000_000) }
            })
        }
    }
}
