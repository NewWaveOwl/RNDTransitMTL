package transit

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.random.Random

class GenerationTest {
    private val start=Point(45.5,-73.6)
    private val end=Point(45.51,-73.6)
    private val now=Instant.parse("2026-11-13T15:00:00Z")
    private fun input(pace: String="Amble", modes: List<String> = listOf("walk"))=obj("origin" to obj("label" to "Confirmed pin","point" to start.toJson()),"plannedMinutes" to 30,"modes" to modes,"walkingPreference" to pace,
        "zonesByTransport" to if("bus" in modes) mapOf("bus" to listOf("A")) else emptyMap<String,Any>())
    private fun zones()=FareZones(obj("type" to "FeatureCollection","features" to listOf("A","B","C","D").map { zone -> obj("properties" to obj("zone" to zone),"geometry" to obj("type" to "Polygon","coordinates" to listOf(listOf(listOf(-74,45),listOf(-73,45),listOf(-73,46),listOf(-74,46),listOf(-74,45))))) }))
    private fun encode(points: List<Point>): String {
        var lat=0; var lon=0
        fun delta(n: Int): String { var v=if(n<0) (n shl 1).inv() else n shl 1; return buildString { while(v>=32) { append(((32 or (v and 31))+63).toChar()); v=v ushr 5 }; append((v+63).toChar()) } }
        return points.joinToString("") { val a=kotlin.math.round(it.latitude*1e5).toInt(); val b=kotlin.math.round(it.longitude*1e5).toInt(); val s=delta(a-lat)+delta(b-lon); lat=a;lon=b;s }
    }
    private fun response(minutes: Double=20.0, meters: Double=1000.0, mode: String="WALK", points: List<Point> = listOf(start,end)): JsonObject {
        val leg=obj("mode" to mode,"distance" to meters,"duration" to 600,
            "route" to obj("gtfsId" to "STM:55"),
            "end" to obj("scheduledTime" to now.plusMillis((minutes*60000).toLong()).toString()),
            "legGeometry" to obj("length" to points.size,"points" to encode(points)))
        val itinerary=obj("duration" to 600,"legs" to listOf(leg))
        val plan=obj("routingErrors" to emptyList<Any>(),"edges" to listOf(obj("node" to itinerary)))
        return obj("data" to obj("planConnection" to plan))
    }
    private fun generator(route: suspend (JsonObject)->JsonObject) = Generator(emptyList(),zones(),route,{now},{listOf(end)})
    @Test fun preservesOriginGeometryAndBudget()=runBlocking {
        val raw=input(); val result=generator { response() }.generate(parseRequest(raw))
        assertEquals("success",result["status"].text()); assertEquals(raw["origin"],result["origin"])
        assertEquals(1260.0,result["elapsedSeconds"].number()!!,0.0); assertEquals(60.0,result["orientationBufferSeconds"].number()!!,0.0)
        assertEquals(response().field("data").field("planConnection").field("edges"),result.field("otp").field("data").field("planConnection").field("edges"))
    }
    @Test fun budgetIncludesInitialWaitAndOrientation()=runBlocking {
        assertEquals("no_trip",generator { response(29.5) }.generate(parseRequest(input()))["status"].text())
        assertEquals(1800.0,generator { response(29.0) }.generate(parseRequest(input()))["elapsedSeconds"].number()!!,0.0)
        assertEquals("no_trip",generator { response(40.0) }.generate(parseRequest(input()))["status"].text())
    }
    @Test fun namesBindToSpeedsAndGraphqlUsesMetresPerSecond() {
        walkingSpeeds.forEach { (pace,kmh) ->
            val r=parseRequest(input(pace)); val request=otpRequest(r,end,now,null)
            assertEquals(kmh/3.6,request.field("variables").field("walkSpeed").number()!!,1e-9)
            assertTrue(request["query"].text()!!.contains("speed: $"+"walkSpeed"))
        }
    }
    @Test fun transitRetainsWalkingAccessAndRealtimeScope() {
        val r=otpRequest(parseRequest(input(modes=listOf("bus"))),end,now,null)
        assertEquals(listOf("STM"),r.field("extensions").field("realtimeFeeds").strings())
        assertEquals(JsonPrimitive(true),r.field("extensions").field("realtimeFeedsFromItinerary"))
        assertEquals(listOf("WALK"),r.field("variables").field("modes").field("transit").field("access").strings())
        assertEquals(JsonPrimitive(false),r.field("variables").field("excludeRealtime"))
    }
    @Test fun busGenerationPreservesExoSectorNamespaceAndFeedHealth()=runBlocking {
        val original=response(mode="BUS")
        val text=original.toString().replace("STM:55","EXO_BUS_CITSO:10")
        val health=obj("realtimeByFeed" to obj("EXO_BUS_CITSO" to obj("tripUpdates" to obj("state" to "fresh"))))
        val body=JsonObject(json.parseToJsonElement(text).jsonObject + ("extensions" to health))
        val result=generator { query ->
            assertEquals(JsonPrimitive(true),query.field("extensions").field("realtimeFeedsFromItinerary"))
            body
        }.generate(parseRequest(input(modes=listOf("bus"))))
        assertEquals("success",result["status"].text())
        assertEquals("EXO_BUS_CITSO:10",result.field("otp").field("data").field("planConnection").field("edges").items().single()
            .field("node").field("legs").items().single().field("route").field("gtfsId").text())
        assertEquals(health,result.field("otp").field("extensions"))
    }
    @Test fun randomCoordinatesNeedNoStopCatalogueAndExcludePrevious() {
        val r=parseRequest(input()); val a=candidates(r,Random(123)); assertEquals(a,candidates(r,Random(123)))
        assertTrue(a.isNotEmpty()); assertTrue(a.all(::inServiceArea))
        assertTrue(a.all { distance(start,it)>0 && distance(start,it)<1500 })
        val b=candidates(r.copy(excluded=a.take(10)),Random(123)); assertTrue(b.all { p -> a.take(10).none { distance(p,it)<100 } })
        assertTrue(candidates(r.copy(minutes=5)).isNotEmpty())
    }
    @Test fun polygonHolesAreExcluded() {
        val geometry=obj("type" to "Polygon","coordinates" to listOf(listOf(listOf(-74,45),listOf(-73,45),listOf(-73,46),listOf(-74,46),listOf(-74,45)),listOf(listOf(-73.7,45.4),listOf(-73.5,45.4),listOf(-73.5,45.6),listOf(-73.7,45.6),listOf(-73.7,45.4))))
        assertFalse(inPolygon(start,geometry)); assertTrue(inPolygon(Point(45.8,-73.8),geometry))
    }
    @Test fun rejectsUnknownPaceModesAndZones() {
        listOf(input("Sprint"),input(modes=listOf("REM")),JsonObject(input(modes=listOf("bus"))+ ("zonesByTransport" to obj("bus" to listOf("1"))))).forEach { raw ->
            try { parseRequest(raw); fail("Invalid request accepted") } catch(_: InputError) { }
        }
    }
    @Test fun rejectsDisconnectedGeometryAndUnselectedModes()=runBlocking {
        assertEquals("no_trip",generator { response(points=listOf(Point(45.6,-73.6),end)) }.generate(parseRequest(input()))["status"].text())
        assertEquals("no_trip",generator { response(mode="RAIL") }.generate(parseRequest(input()))["status"].text())
    }
    @Test fun unknownFareZonesFailClosed() { try { FareZones(obj("type" to "FeatureCollection","features" to emptyList<Any>())); fail() } catch(_: IllegalArgumentException) { } }
    @Test fun selectedLinesPreserveOtherModes() {
        val r=parseRequest(input(modes=listOf("walk","bus"))).copy(lines=listOf("bus:55"))
        val routes=listOf(obj("gtfsId" to "STM:55","mode" to "BUS","shortName" to "55"))
        assertEquals(setOf("STM:55"),allowedLines(r,routes))
        try { allowedLines(r.copy(lines=listOf("bus:999")),routes); fail() } catch(_: InputError) { }
    }
    @Test fun cancellationAndTimeoutDoNotFabricateRoutes()=runBlocking {
        val slow=Generator(emptyList(),zones(),{ delay(1000);response() },{now},{listOf(end)},10)
        assertEquals("no_trip",slow.generate(parseRequest(input()))["status"].text())
        try { generator { throw CancellationException("cancelled") }.generate(parseRequest(input())); fail() } catch(_: CancellationException) { }
    }
    @Test fun apiDistinguishesInvalidAuthAndProviderFailures()=runBlocking {
        val api=Api(generator { response() })
        assertEquals(400,api.handle("application/json",null) { "{" }.first)
        assertEquals(400,api.handle("text/plain",null) { input().toString() }.first)
        assertEquals(400,api.handle("application/json",null) { "x".repeat(16385) }.first)
        assertEquals(401,Api(generator { response() },{false}).handle("application/json",null) { input().toString() }.first)
        assertEquals(503,Api(generator { throw ProviderError("offline") }).handle("application/json",null) { input().toString() }.first)
        assertEquals(200,api.handle("application/json",null) { input().toString() }.first)
    }
    @Test fun apiCapsConcurrentGeneration()=runBlocking {
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); var count=0
        val api=Api(generator { if(++count==4) entered.complete(Unit); release.await(); response() })
        val jobs=List(4) { async { api.handle("application/json",null) { input().toString() } } }
        entered.await(); assertEquals(429,api.handle("application/json",null) { input().toString() }.first)
        release.complete(Unit); jobs.awaitAll()
        Unit
    }
    @Test fun orientationRoundsFractionalKilometres() { assertEquals(76L,orientationSeconds(1251.0)) }
    @Test fun zoneChecksIncludeIntermediateTransitGeometry()=runBlocking {
        fun square(low: Double,high: Double)=listOf(listOf(-73.7,low),listOf(-73.5,low),listOf(-73.5,high),listOf(-73.7,high),listOf(-73.7,low))
        val features=listOf("A","B","C","D").map { zone ->
            val geometry=if(zone=="A") obj("type" to "MultiPolygon","coordinates" to listOf(listOf(square(45.49,45.503)),listOf(square(45.507,45.52))))
                else obj("type" to "Polygon","coordinates" to listOf(square(45.503,45.507)))
            obj("properties" to obj("zone" to zone),"geometry" to geometry)
        }
        val z=FareZones(obj("type" to "FeatureCollection","features" to features))
        val generator=Generator(emptyList(),z,{ response(mode="BUS") },{now},{listOf(end)})
        assertEquals("no_trip",generator.generate(parseRequest(input(modes=listOf("bus"))))["status"].text())
    }

    @Test fun selectedModesAreAlternativesAndWalkMustBeExplicit()=runBlocking {
        assertEquals("success",generator { response() }.generate(parseRequest(input(modes=listOf("walk","bus"))))["status"].text())
        assertEquals("success",generator { response(mode="BUS") }.generate(parseRequest(input(modes=listOf("walk","bus"))))["status"].text())
        assertEquals("no_trip",generator { response() }.generate(parseRequest(input(modes=listOf("bus"))))["status"].text())
        val multipleTransit=parseRequest(input(modes=listOf("bus"))).copy(modes=listOf("bus","metro"),zones=mapOf("bus" to listOf("A"),"metro" to listOf("A")))
        assertEquals("success",generator { response(mode="BUS") }.generate(multipleTransit)["status"].text())
        val middle=Point(45.505,-73.6)
        val walk=response(points=listOf(start,middle)).field("data").field("planConnection").field("edges").items().single().field("node").field("legs").items().single()
        val bus=response(mode="BUS",points=listOf(middle,end)).field("data").field("planConnection").field("edges").items().single().field("node").field("legs").items().single()
        val itinerary=obj("legs" to listOf(walk,bus))
        assertTrue(accepts(itinerary,parseRequest(input(modes=listOf("bus"))),end,zones(),null))
    }
    @Test fun laterCloserCandidateWinsAndEqualDurationKeepsFirst()=runBlocking {
        val other=Point(45.52,-73.6); var calls=0
        val g=Generator(emptyList(),zones(),{ query ->
            calls++
            val target=Point(query.field("variables").field("destinationLat").number()!!,query.field("variables").field("destinationLon").number()!!)
            response(if(calls==1) 10.0 else 25.0,points=listOf(start,target))
        },{now},{listOf(end,other)})
        val result=g.generate(parseRequest(input()))
        assertEquals(1560.0,result["elapsedSeconds"].number()!!,0.0)
        assertEquals(other.toJson(),result.field("destination").field("point"))
        val tie=Generator(emptyList(),zones(),{ query ->
            response(points=listOf(start,Point(query.field("variables").field("destinationLat").number()!!,query.field("variables").field("destinationLon").number()!!)))
        },{now},{listOf(end,other)}).generate(parseRequest(input()))
        assertEquals(end.toJson(),tie.field("destination").field("point"))
    }
    @Test fun deadlineReturnsBestButParentCancellationStillWins()=runBlocking {
        var calls=0
        val g=Generator(emptyList(),zones(),{
            if(++calls==1) response() else { delay(10_000); response() }
        },{now},{listOf(end,Point(45.52,-73.6))},100)
        assertEquals("success",g.generate(parseRequest(input()))["status"].text())
        calls=0
        val entered=CompletableDeferred<Unit>()
        val cancellable=Generator(emptyList(),zones(),{
            if(++calls==1) response() else { entered.complete(Unit); delay(10_000); response() }
        },{now},{listOf(end,Point(45.52,-73.6))},5000)
        val job=async { cancellable.generate(parseRequest(input())) }
        entered.await(); job.cancel()
        try { job.await(); fail("Cancelled generation returned a result") } catch(_: CancellationException) { }
    }
    @Test fun searchBudgetUsesWholeHours() {
        mapOf(5 to 20L,30 to 20L,59 to 20L,60 to 21L,61 to 21L,119 to 21L,120 to 22L,360 to 26L).forEach { (minutes,seconds) ->
            assertEquals(seconds,searchBudgetSeconds(minutes))
        }
    }

    @Test fun networkCandidatesRespectLinesZonesAndExclusions() {
        val route=obj("gtfsId" to "STM:55","mode" to "BUS","stops" to listOf(obj("lat" to end.latitude,"lon" to end.longitude)))
        val r=parseRequest(input(modes=listOf("bus")))
        assertEquals(listOf(end),destinationCandidates(r,listOf(route),zones(),setOf("STM:55")))
        assertTrue(end in destinationCandidates(parseRequest(input()),listOf(route),zones(),null))
        assertFalse(end in destinationCandidates(r.copy(excluded=listOf(end)),listOf(route),zones(),setOf("STM:55")))
        assertFalse(end in destinationCandidates(r,listOf(route),zones(),setOf("STM:99")))
    }
    @Test fun overBudgetRouteFeedsLinkedGeometryBackIntoSearch()=runBlocking {
        val middle=Point(45.505,-73.6); var calls=0
        val g=Generator(emptyList(),zones(),{ query ->
            if(++calls==1) response(40.0,points=listOf(start,middle,end)) else {
                assertEquals(middle.latitude,query.field("variables").field("destinationLat").number()!!,0.0)
                response(25.0,points=listOf(start,middle))
            }
        },{now},{listOf(end)})
        val result=g.generate(parseRequest(input()))
        assertEquals(middle.toJson(),result.field("destination").field("point"))
        assertEquals(2,calls)
    }

}
