package transit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Capability evidence deliberately separates running-router metadata from successful sample journeys. */
class OtpCapabilitiesTest {
    private fun feed(id: String, vararg modes: String) = obj("feedId" to id, "version" to null,
        "publisher" to obj("name" to "Fixture publisher"), "agencies" to listOf(obj("routes" to modes.map { obj("mode" to it) })))
    private fun metadata(feeds: List<JsonObject> = listOf(feed("STM", "BUS", "SUBWAY"), feed("EXO_TRAINS", "RAIL")),
        direct: List<String>? = listOf("WALK", "BICYCLE", "CAR")) = obj("data" to obj(
        "directModes" to direct?.let { obj("enumValues" to it.map { mode -> obj("name" to mode) }) },
        "serviceTimeRange" to obj("start" to 1787544000L, "end" to 1799038800L), "feeds" to feeds))
    private fun info() = obj("data" to obj("serverInfo" to obj("version" to "2.10.0", "internalTransitModelTimeZone" to "America/Toronto")))

    @Test fun metadataReportsActualVersionAndDifferentSupportEvidenceWithoutInventingCoverage() = runBlocking {
        val result = OtpCapabilities({ metadata() }, { info() }).read()
        assertEquals("2.10.0", result["router"].field("version").text())
        val modes = result["modes"].items().associateBy { it.field("code").text() }
        assertEquals("router_schema", modes["car"].field("basis").text())
        assertEquals("loaded_routes", modes["train"].field("basis").text())
        assertEquals("available", modes["train"].field("status").text())
        assertEquals(JsonNull, result["coverage"].field("bounds"))
        assertTrue(result["coverage"].field("feeds").items().all { it.field("startDate") == JsonNull && it.field("endDate") == JsonNull })
    }

    @Test fun globalRangeUsesGraphTimezoneAndExclusiveEndDoesNotPromiseAnotherDayOfService() = runBlocking {
        val coverage = OtpCapabilities({ metadata() }, { info() }).read()["coverage"]
        assertEquals("America/Toronto", coverage.field("timeZone").text())
        assertEquals("2026-08-24", coverage.field("startDate").text())
        assertEquals("2027-01-03", coverage.field("endDate").text())
        assertEquals("2027-01-04T05:00:00Z", coverage.field("serviceEndExclusive").text())
        assertEquals("global_union", coverage.field("serviceRangeBasis").text())
    }

    @Test fun missingRailDatasetIsExplicitRatherThanAnInventedTrainService() = runBlocking {
        val result = OtpCapabilities({ metadata(listOf(feed("STM", "BUS"))) }, { info() }).read()
        val train = result["modes"].items().single { it.field("code").text() == "train" }
        assertEquals("unsupported", train.field("status").text())
        assertEquals("mode_not_in_dataset", train.field("reason").text())
    }

    @Test fun routerWithoutCarEnumDoesNotPromiseCarRouting() = runBlocking {
        val result = OtpCapabilities({ metadata(direct = listOf("WALK", "BICYCLE")) }, { info() }).read()
        assertEquals("unsupported", result["modes"].items().single { it.field("code").text() == "car" }.field("status").text())
    }

    @Test fun disabledIntrospectionMeansUnknownDirectModesNotUnsupported() = runBlocking {
        val result = OtpCapabilities({ metadata(direct = null) }, { info() }).read()
        assertEquals("unknown", result["modes"].items().single { it.field("code").text() == "bike" }.field("status").text())
    }

    @Test fun incompleteFeedCatalogueMeansUnknownTransitSupport() = runBlocking {
        val result = OtpCapabilities({ metadata(listOf(obj("feedId" to "feed-without-route-metadata"))) }, { info() }).read()
        assertEquals("unknown", result["modes"].items().single { it.field("code").text() == "train" }.field("status").text())
    }

    @Test fun absentServerInfoPreservesUnknownVersionAndCalendarDates() = runBlocking {
        val result = OtpCapabilities({ metadata() }, { error("Private server diagnostic") }).read()
        assertEquals(JsonNull, result["router"].field("version"))
        assertEquals(JsonNull, result["coverage"].field("timeZone"))
        assertEquals(JsonNull, result["coverage"].field("startDate"))
        assertNotNull(result["coverage"].field("serviceStart").text())
        assertFalse(result.toString().contains("Private server diagnostic"))
    }

    @Test fun badGraphqlResponseIsAnOutageNotAnEmptySupportedDataset() = runBlocking {
        try { OtpCapabilities({ obj("errors" to listOf(obj("message" to "private diagnostic"))) }, { info() }).read(); fail() }
        catch (failure: ProviderError) { assertFalse(failure.message!!.contains("private diagnostic")) }
    }

    @Test fun cancellationOfMetadataWorkDoesNotBecomePartialSuccess() = runBlocking {
        try { OtpCapabilities({ metadata() }, { throw CancellationException("closed view") }).read(); fail() }
        catch (_: CancellationException) { }
    }

    @Test fun briefSharedCacheAvoidsRepeatedCatalogueRequestsAndRefreshesAfterExpiry() = runBlocking {
        var now = 0L
        var calls = 0
        val capabilities = OtpCapabilities({ calls++; metadata() }, { info() }, { now })
        capabilities.read()
        capabilities.read()
        assertEquals(1, calls)
        now = 31_000_000_000L
        capabilities.read()
        assertEquals(2, calls)
    }

    @Test fun optionalServerInfoHasABoundedWaitAndCannotHideValidGtfsMetadata() = runTest {
        val result = OtpCapabilities({ metadata() }, { awaitCancellation() }).read()
        assertEquals(JsonNull, result["router"].field("version"))
        assertEquals("available", result["modes"].items().single { it.field("code").text() == "bus" }.field("status").text())
    }

    @Test fun invalidRangeRemainsUnknownInsteadOfInventingCalendarDates() = runBlocking {
        val invalid = metadata()
        val changed = JsonObject(invalid["data"]!!.jsonObject + ("serviceTimeRange" to obj("start" to 2000, "end" to 1000)))
        val coverage = OtpCapabilities({ obj("data" to changed) }, { info() }).read()["coverage"]
        assertEquals(JsonNull, coverage.field("startDate"))
        assertEquals(JsonNull, coverage.field("endDate"))
        assertEquals(JsonNull, coverage.field("serviceStart"))
    }
}
