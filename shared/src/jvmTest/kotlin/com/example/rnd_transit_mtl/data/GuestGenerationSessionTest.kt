package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.data.authentication.AccountSession
import com.example.rnd_transit_mtl.data.authentication.SupabaseAuthRepository
import com.example.rnd_transit_mtl.data.generation.generationAccessToken
import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlin.test.*

/** Exercises the actual Supabase SDK against an isolated local anonymous-signup fixture. */
class GuestGenerationSessionTest {
    @Test fun concurrentGuestRequestsAndRestartReuseOneAnonymousIdentity() = runBlocking {
        val calls = AtomicInteger()
        val storage = MemorySessionManager()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/auth/v1/signup") { exchange ->
            calls.incrementAndGet()
            exchange.requestBody.use { it.readBytes() }
            val body = """{"access_token":"fixture-token","token_type":"bearer","refresh_token":"fixture-refresh",
                "expires_in":3600,"user":{"id":"63c21745-adfe-4b60-95c7-c121f409ed07","aud":"authenticated",
                "role":"authenticated","is_anonymous":true,"created_at":"2026-11-27T00:00:00Z"}}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        fun client() = createSupabaseClient("http://127.0.0.1:${server.address.port}", "fixture-key") {
            install(Auth) { sessionManager = storage }
        }
        try {
            val first = client()
            try {
                val mutex = Mutex()
                val tokens = coroutineScope { List(2) { async { generationAccessToken(first.auth, mutex) } }.awaitAll() }
                assertEquals(listOf("fixture-token", "fixture-token"), tokens)
                assertEquals(1, calls.get())
                assertEquals(AccountSession.Guest, SupabaseAuthRepository(first.auth).session.first())
            } finally { first.close() }
            val restored = client()
            try {
                assertEquals("fixture-token", generationAccessToken(restored.auth, Mutex()))
                assertEquals(1, calls.get())
                assertEquals(AccountSession.Guest, SupabaseAuthRepository(restored.auth).session.first())
            } finally { restored.close() }
        } finally { server.stop(0) }
    }
}
