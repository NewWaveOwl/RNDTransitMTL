package transit

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddressLookupTest {
    private val address = """{"display_name":"Montreal City Hall, Montréal","lat":"45.5088","lon":"-73.554"}"""

    @Test fun searchesAndReverseShareOneProviderSlotPerSecond() = runTest {
        val starts = mutableListOf<Long>()
        val provider = NominatimAddresses({ path, _ ->
            starts += testScheduler.currentTime
            NominatimAddresses.Response(200, if (path == "search") "[$address]" else address)
        }, { testScheduler.currentTime })
        val tasks = listOf(async { provider.search("Montreal City Hall") }, async { provider.reverse(45.5088, -73.554) },
            async { provider.search("Notre Dame Montreal") })
        tasks.awaitAll().forEach { assertEquals(200, it.first) }
        assertEquals(listOf(0L, 1000L, 2000L), starts)
        assertEquals(0, provider.queued)
    }

    @Test fun identicalQueriesAreCachedAcrossUsersAndWhitespace() = runTest {
        var calls = 0
        val provider = NominatimAddresses({ _, parameters ->
            calls++
            assertEquals("Montreal City Hall", parameters["q"])
            assertEquals("jsonv2", parameters["format"])
            assertFalse(parameters.containsKey("Authorization"))
            NominatimAddresses.Response(200, "[$address]")
        }, { testScheduler.currentTime })
        val first = provider.search(" Montreal  City Hall ")
        assertEquals(first, provider.search("montreal city hall"))
        assertEquals(1, calls)
        assertEquals(1, provider.cacheHits.get())
    }

    @Test fun concurrentDuplicatesRecheckCacheBeforeSending() = runTest {
        var calls = 0
        val provider = NominatimAddresses({ _, _ -> calls++; delay(50); NominatimAddresses.Response(200, "[$address]") }, { testScheduler.currentTime })
        awaitAll(async { provider.search("City Hall") }, async { provider.search("City Hall") })
        assertEquals(1, calls)
    }

    @Test fun cancelledQueuedLookupDoesNotContactProviderOrHoldCapacity() = runTest {
        var calls = 0
        val provider = NominatimAddresses({ path, _ -> calls++; NominatimAddresses.Response(200, if (path == "search") "[$address]" else address) }, { testScheduler.currentTime })
        provider.search("City Hall")
        val waiting = launch { provider.reverse(45.5, -73.5) }
        runCurrent()
        assertEquals(1, provider.queued)
        waiting.cancelAndJoin()
        assertEquals(1, calls)
        assertEquals(0, provider.queued)
        provider.reverse(45.6, -73.6)
        assertEquals(2, calls)
        assertEquals(1000L, testScheduler.currentTime)
    }

    @Test fun failedRequestsStillConsumeTheirRateLimitSlot() = runTest {
        val starts = mutableListOf<Long>()
        val provider = NominatimAddresses({ _, _ ->
            starts += testScheduler.currentTime
            if (starts.size == 1) throw ProviderError("fixture")
            NominatimAddresses.Response(200, "[]")
        }, { testScheduler.currentTime })
        try { provider.search("City Hall"); fail() } catch (_: ProviderError) { }
        provider.search("City Hall")
        assertEquals(listOf(0L, 1000L), starts)
    }

    @Test fun providerRateLimitHonorsRetryAfterWithoutRetries() = runTest {
        var calls = 0
        val provider = NominatimAddresses({ _, _ -> calls++; NominatimAddresses.Response(429, "private upstream diagnostics", "30") }, { testScheduler.currentTime })
        assertEquals(503, provider.search("City Hall").first)
        val waiting = provider.reverse(45.5, -73.5)
        assertEquals(503, waiting.first)
        assertEquals(30.0, waiting.second["retryAfterSeconds"].number()!!, 0.0)
        assertFalse(waiting.second.toString().contains("diagnostics"))
        assertEquals(1, calls)
        advanceTimeBy(30_000)
        provider.search("City Hall")
        assertEquals(2, calls)
    }

    @Test fun noMatchIsExplicitAndCached() = runTest {
        var calls = 0
        val provider = NominatimAddresses({ _, _ -> calls++; NominatimAddresses.Response(404, "unavailable address") })
        val result = provider.reverse(45.5, -73.5)
        assertEquals("no_match", result.second["status"].text())
        assertTrue(result.second["candidates"].items().isEmpty())
        assertEquals(result, provider.reverse(45.5, -73.5))
        assertEquals(1, calls)
    }

    @Test fun cacheExpiresWithoutBackgroundRequests() = runTest {
        var calls = 0
        val provider = NominatimAddresses({ _, _ -> calls++; NominatimAddresses.Response(200, "[]") }, { testScheduler.currentTime })
        provider.search("Unmatched location")
        advanceTimeBy(300_001)
        assertEquals(1, calls)
        provider.search("Unmatched location")
        assertEquals(2, calls)
    }

    @Test fun invalidQueriesAndCoordinatesNeverContactProvider() = runTest {
        val provider = NominatimAddresses({ _, _ -> error("Provider must not be called") })
        try { provider.search("ab"); fail() } catch (_: InputError) { }
        try { provider.search("City\nHall"); fail() } catch (_: InputError) { }
        try { provider.reverse(Double.NaN, -73.0); fail() } catch (_: InputError) { }
        try { provider.reverse(91.0, -73.0); fail() } catch (_: InputError) { }
        assertEquals(0, provider.requests.get())
    }

    @Test fun overloadedQueueFailsPromptlyAndCancelledWaitersReleaseSlots() = runTest {
        val entered = CompletableDeferred<Unit>()
        val provider = NominatimAddresses({ _, _ -> entered.complete(Unit); awaitCancellation() }, { testScheduler.currentTime })
        val active = launch { provider.search("City Hall") }
        entered.await()
        val waiters = (1..3).map { launch { provider.search("Public place $it") } }
        runCurrent()
        assertEquals(503, provider.search("Fifth request").first)
        active.cancelAndJoin()
        waiters.forEach { it.cancelAndJoin() }
        assertEquals(0, provider.queued)
    }

    @Test fun malformedProviderCoordinatesAreNotUsableSuggestions() = runTest {
        val provider = NominatimAddresses({ _, _ -> NominatimAddresses.Response(200, """[{"display_name":"Hall","lat":"NaN","lon":"-73"}]""") })
        try { provider.search("City Hall"); fail() } catch (_: ProviderError) { }
    }
}
