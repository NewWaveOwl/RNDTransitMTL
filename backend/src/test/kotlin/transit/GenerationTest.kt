package transit

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
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
    @Test fun validatesWholeMinutesPaceModesAndZones() {
        listOf(5,6,7,37,359,360).forEach { minutes ->
            assertEquals(minutes,parseRequest(JsonObject(input() + ("plannedMinutes" to JsonPrimitive(minutes)))).minutes)
        }
        listOf(0.0,4.0,5.5,360.5,361.0).forEach { minutes ->
            try { parseRequest(JsonObject(input() + ("plannedMinutes" to JsonPrimitive(minutes)))); fail("Invalid minutes accepted: $minutes") }
            catch(_: InputError) { }
        }
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
        assertEquals("timeout",slow.generate(parseRequest(input()))["status"].text())
        try { generator { throw CancellationException("cancelled") }.generate(parseRequest(input())); fail() } catch(_: CancellationException) { }
    }
    @Test fun apiDistinguishesInvalidAuthAndProviderFailures()=runBlocking {
        val api=Api(generator { response() })
        assertEquals(400,api.handle("application/json",null) { "{" }.first)
        assertEquals(400,api.handle("text/plain",null) { input().toString() }.first)
        assertEquals(400,api.handle("application/json",null) { "x".repeat(16385) }.first)
        assertEquals(401,Api(generator { response() },{null}).handle("application/json",null) { input().toString() }.first)
        assertEquals(503,Api(generator { throw ProviderError("offline") }).handle("application/json",null) { input().toString() }.first)
        assertEquals(200,api.handle("application/json",null) { input().toString() }.first)
    }
    @Test fun apiCapsConcurrentGeneration()=runBlocking {
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); var count=0
        var identity=0
        val api=Api(generator { if(++count==4) entered.complete(Unit); release.await(); response() },authorize={ "user-${identity++}" })
        val jobs=List(4) { async { api.handle("application/json",null) { input().toString() } } }
        entered.await()
        val busy=api.handle("application/json",null) { input().toString() }
        assertEquals(503,busy.first)
        assertEquals("capacity",busy.second["reason"].text())
        assertEquals(1.0,busy.second["retryAfterSeconds"].number()!!,0.0)
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

    @Test fun networkCandidatesRespectLinesZonesAndExclusions()=runBlocking {
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

    @Test fun candidateFailuresCanRecoverButRepeatedFailuresAreBounded()=runBlocking {
        val destinations=listOf(end,Point(45.52,-73.6),Point(45.53,-73.6))
        val failures=listOf(
            obj("errors" to listOf(obj("message" to "Temporary planning failure"))),
            obj("data" to obj("planConnection" to obj("edges" to emptyList<Any>(),
                "routingErrors" to listOf(obj("code" to "UNEXPECTED_CANDIDATE_ERROR"))))),
            obj("data" to emptyMap<String,Any>())
        )
        for(failure in failures) {
            var calls=0
            val generator=Generator(emptyList(),zones(),{ query ->
                if(++calls==1) failure else {
                    val target=Point(query.field("variables").field("destinationLat").number()!!,
                        query.field("variables").field("destinationLon").number()!!)
                    response(28.0,points=listOf(start,target))
                }
            },{now},{destinations})
            assertEquals("success",generator.generate(parseRequest(input()))["status"].text())
            assertEquals(2,calls)
        }
        var calls=0
        val unavailable=Generator(emptyList(),zones(),{ calls++;failures.first() },{now},{destinations})
        try { unavailable.generate(parseRequest(input())); fail("Provider failure was hidden as no trip") }
        catch(_: ProviderError) { }
        assertEquals(2,calls)
    }

    @Test fun systemicGraphqlErrorsStopImmediately()=runBlocking {
        for(error in listOf(
            obj("message" to "Invalid schema", "extensions" to obj("code" to "GRAPHQL_VALIDATION_FAILED")),
            obj("message" to "Cannot query field planConnection on type Query")
        )) {
            var calls=0
            val generator=Generator(emptyList(),zones(),{ calls++;obj("errors" to listOf(error)) },{now},
                {listOf(end,Point(45.52,-73.6))})
            try { generator.generate(parseRequest(input())); fail("Systemic query error accepted") }
            catch(_: ProviderError) { }
            assertEquals(1,calls)
        }
    }

    @Test fun responseFailureBudgetPreservesBestAndIncludesTransportErrors()=runBlocking {
        var calls=0
        val destinations=listOf(end,Point(45.52,-73.6),Point(45.53,-73.6),Point(45.54,-73.6))
        val generator=Generator(emptyList(),zones(),{
            when(++calls) {
                1 -> response()
                2 -> throw java.io.IOException("upstream unavailable")
                else -> obj("errors" to listOf(obj("message" to "Temporary failure")))
            }
        },{now},{destinations})
        assertEquals("success",generator.generate(parseRequest(input()))["status"].text())
        assertEquals(3,calls)
    }

    @Test fun aBadCandidateAfterSuccessDoesNotPreventABetterMatch()=runBlocking {
        var calls=0
        val generator=Generator(emptyList(),zones(),{ query ->
            if(++calls==2) obj("errors" to listOf(obj("message" to "Temporary candidate failure"))) else {
                val target=Point(query.field("variables").field("destinationLat").number()!!,
                    query.field("variables").field("destinationLon").number()!!)
                response(if(calls==1) 10.0 else 28.0,points=listOf(start,target))
            }
        },{now},{listOf(end,Point(45.52,-73.6),Point(45.53,-73.6))})
        val result=generator.generate(parseRequest(input()))
        assertEquals("success",result["status"].text())
        assertEquals(1740.0,result["elapsedSeconds"].number()!!,0.0)
        assertEquals(3,calls)
    }

    @Test fun apiLogsSanitizedFailureWithCorrelationIdAndDoesNotLogCancellation()=runBlocking {
        val entries=mutableListOf<Pair<String,Throwable>>()
        val api=Api(generator { response() },authorize={
            throw NullPointerException("Bearer private-token precise-origin").apply {
                initCause(IllegalStateException("secret upstream payload"))
            }
        },logFailure={ id,error -> entries+=id to error })
        val result=api.handle("application/json","Bearer private-token") { input().toString() }
        assertEquals(503,result.first)
        assertEquals(entries.single().first,result.second["requestId"].text())
        val trace=entries.single().second.stackTraceToString()
        assertTrue(trace.contains("NullPointerException"))
        assertTrue(trace.contains("IllegalStateException"))
        assertTrue(trace.contains("GenerationTest"))
        assertFalse(trace.contains("private-token"))
        assertFalse(trace.contains("precise-origin"))
        assertFalse(trace.contains("secret upstream payload"))
        assertFalse(result.second.toString().contains("private-token"))
        val cancelled=Api(generator { throw CancellationException("cancelled") },logFailure={ id,error -> entries+=id to error })
        try { cancelled.handle("application/json",null) { input().toString() }; fail("Cancellation swallowed") }
        catch(_: CancellationException) { }
        assertEquals(1,entries.size)
    }

    @Test fun bikeUsesPedestrianGraphAndSelectedSpeedWithoutChangingGeometry()=runBlocking {
        for(speed in listOf(8,18,32)) {
            val request=parseRequest(JsonObject(input(modes=listOf("bike")) + ("bikeSpeedKmh" to JsonPrimitive(speed))))
            val result=generator { query ->
                assertEquals(listOf("WALK"),query.field("variables").field("modes").field("direct").strings())
                assertEquals(speed/3.6,query.field("variables").field("walkSpeed").number()!!,1e-9)
                response()
            }.generate(request)
            assertEquals("success",result["status"].text())
            val leg=result.field("otp").field("data").field("planConnection").field("edges").items().single()
                .field("node").field("legs").items().single()
            assertEquals("BICYCLE",leg.field("mode").text())
            assertEquals(response().field("data").field("planConnection").field("edges").items().single()
                .field("node").field("legs").items().single().field("legGeometry"),leg.field("legGeometry"))
        }
        for(speed in listOf(JsonPrimitive(7),JsonPrimitive(33),JsonPrimitive(18.5),JsonPrimitive("18"))) {
            try { parseRequest(JsonObject(input() + ("bikeSpeedKmh" to speed))); fail("Invalid speed accepted") }
            catch(_: InputError) { }
        }
    }

    @Test fun bikeTransitAccessKeepsWalkingPace()=runBlocking {
        val request=parseRequest(input("Dash",listOf("bike","bus"))).copy(bikeSpeedKmh=32)
        val query=otpRequest(request,end,now,null)
        assertEquals(6.8/3.6,query.field("variables").field("walkSpeed").number()!!,1e-9)
        assertEquals(emptyList<String>(),query.field("variables").field("modes").field("direct").strings())
        val result=generator { q ->
            if(q.field("variables").field("modes").field("directOnly")==JsonPrimitive(true)) response() else response(mode="BUS")
        }.generate(request)
        assertEquals("success",result["status"].text())
    }

    @Test fun explicitWalkingFallbackSurvivesTransitFailureAndDeadline()=runBlocking {
        for(slow in listOf(false,true)) {
            val log=mutableListOf<String>()
            val g=Generator(emptyList(),zones(),{ query ->
                if(query.field("variables").field("modes").field("directOnly")==JsonPrimitive(true)) response()
                else if(slow) { delay(10_000); response(mode="BUS") }
                else throw ProviderError("offline")
            },{now},{listOf(end)},200,diagnostic={log+=it})
            val result=g.generate(parseRequest(input(modes=listOf("walk","bus"))),"fixture-77")
            assertEquals("success",result["status"].text())
            assertEquals(1260.0,result["elapsedSeconds"].number()!!,0.0)
            assertTrue(log.any { "request=fixture-77" in it })
            assertFalse(log.any { "-73.6" in it })
        }
        try { generator { throw ProviderError("offline") }.generate(parseRequest(input(modes=listOf("bus")))); fail() }
        catch(_: ProviderError) { }
    }

    @Test fun fallbackStillHonoursExclusionsBudgetAndParentCancellation()=runBlocking {
        val request=parseRequest(input(modes=listOf("walk","bus")))
        var calls=0
        val excluded=Generator(emptyList(),zones(),{ calls++; response() },{now},{listOf(end)})
        assertEquals("no_trip",excluded.generate(request.copy(excluded=listOf(end)))["status"].text())
        assertEquals(0,calls)
        assertEquals("no_trip",generator { response(29.5) }.generate(request)["status"].text())
        assertEquals(1800.0,generator { response(29.0) }.generate(request)["elapsedSeconds"].number()!!,0.0)
        val entered=CompletableDeferred<Unit>()
        val g=generator { entered.complete(Unit); awaitCancellation() }
        val job=async { g.generate(request) }
        entered.await(); job.cancel()
        try { job.await(); fail("Cancelled fallback returned") } catch(_: CancellationException) { }
    }

    @Test fun laterTransitMatchCanImproveWalkingFallbackWithoutLosingLineFilter()=runBlocking {
        val request=parseRequest(input(modes=listOf("walk","bus"))).copy(lines=listOf("bus:55"))
        val routes=listOf(obj("gtfsId" to "STM:55","mode" to "BUS","shortName" to "55"))
        val g=Generator(routes,zones(),{ query ->
            if(query.field("variables").field("modes").field("directOnly")==JsonPrimitive(true)) response(10.0)
            else {
                assertEquals(listOf("STM:55"),query.field("variables").field("filters").items().single()
                    .field("include").items().single().field("routes").strings())
                response(28.0,mode="BUS")
            }
        },{now},{listOf(end)})
        val result=g.generate(request)
        assertEquals(1740.0,result["elapsedSeconds"].number()!!,0.0)
        assertEquals("BUS",result.field("otp").field("data").field("planConnection").field("edges").items().single()
            .field("node").field("legs").items().single().field("mode").text())
    }

    @Test fun directPhasesAndTransitShareTheAttemptLimit()=runBlocking {
        var calls=0
        val points=(1..130).map { Point(45.5+it*0.001,-73.6) }
        val g=Generator(emptyList(),zones(),{ calls++
            obj("data" to obj("planConnection" to obj("edges" to emptyList<Any>(),"routingErrors" to emptyList<Any>())))
        },{now},{points},diagnostic={})
        val result=g.generate(parseRequest(input(modes=listOf("walk","bike","bus"))))
        assertEquals("no_trip",result["status"].text())
        assertEquals(128,calls)
        assertEquals(128.0,result["attempts"].number()!!,0.0)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun walkBikeBusReservesTimeForTransitAndUsesOnlyOneDirectFallback()=runTest {
        var directCalls=0
        val preparedModes=mutableListOf<List<String>>()
        val request=parseRequest(input(modes=listOf("walk","bike","bus"))).copy(lines=listOf("bus:55"))
        val routes=listOf(obj("gtfsId" to "STM:55","mode" to "BUS","shortName" to "55"))
        val g=Generator(routes,zones(),{ query ->
            if(query.field("variables").field("modes").field("directOnly")==JsonPrimitive(true)) {
                directCalls++
                delay(4500)
                response(10.0)
            } else {
                assertEquals(listOf("STM:55"),query.field("variables").field("filters").items().single()
                    .field("include").items().single().field("routes").strings())
                delay(12000)
                response(28.0,mode="BUS")
            }
        },{now},sample={ preparedModes+=it.modes; listOf(end) },diagnostic={},
            preparationDispatcher=StandardTestDispatcher(testScheduler))
        val result=g.generate(request)
        assertEquals("success",result["status"].text())
        assertEquals(1,directCalls)
        assertEquals(listOf(listOf("walk"),request.modes),preparedModes)
        assertEquals(16500L,testScheduler.currentTime)
        assertEquals("BUS",result.field("otp").field("data").field("planConnection").field("edges").items().single()
            .field("node").field("legs").items().single().field("mode").text())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun stalledMixedFallbackUsesAtMostOneQuarterOfTheDeadline()=runTest {
        var directCalls=0
        val g=Generator(emptyList(),zones(),{ query ->
            if(query.field("variables").field("modes").field("directOnly")==JsonPrimitive(true)) {
                directCalls++
                awaitCancellation()
            } else {
                assertEquals(5000L,testScheduler.currentTime)
                delay(12000)
                response(28.0,mode="BUS")
            }
        },{now},{listOf(end)},diagnostic={},preparationDispatcher=StandardTestDispatcher(testScheduler))
        val result=g.generate(parseRequest(input(modes=listOf("walk","bike","bus"))))
        assertEquals("success",result["status"].text())
        assertEquals(1,directCalls)
        assertEquals(17000L,testScheduler.currentTime)
    }

    @Test fun userCooldownHasExactBoundaryAndRejectionsDoNotExtendIt() {
        var time=0L
        val limit=UserGenerationLimit { time }
        assertNull(limit.acquire("user-a")); limit.release("user-a")
        assertEquals(3L,limit.acquire("user-a")!!.retryAfterSeconds)
        time=2001
        assertEquals(1L,limit.acquire("user-a")!!.retryAfterSeconds)
        assertNull(limit.acquire("user-b"))
        time=3000
        assertNull(limit.acquire("user-a"))
        time=30_000
        assertEquals("active",limit.acquire("user-a")!!.reason)
        limit.release("user-a")
        assertNull(limit.acquire("user-a"))
    }

    @Test fun oneActiveRequestPerVerifiedUserSurvivesTokenRefreshAndReleasesOnCancel()=runBlocking {
        var time=0L; var calls=0
        val entered=CompletableDeferred<Unit>(); val retried=CompletableDeferred<Unit>()
        val api=Api(generator { calls++; if(calls==1) entered.complete(Unit) else retried.complete(Unit); awaitCancellation() },
            authorize={ if(it=="invalid") null else "verified-user" },userLimit=UserGenerationLimit { time })
        val job=async { api.handle("application/json","old-token") { input().toString() } }
        entered.await(); time=4000
        val rejected=api.handle("application/json","refreshed-token") { input().toString() }
        assertEquals(429,rejected.first); assertEquals("active",rejected.second["reason"].text())
        assertEquals(1,calls)
        assertEquals(401,api.handle("application/json","invalid") { input().toString() }.first)
        job.cancelAndJoin()
        val retry=async { api.handle("application/json","refreshed-token") { input().toString() } }
        withTimeout(5000) { retried.await() }; assertEquals(2,calls); retry.cancelAndJoin()
    }

    @Test fun failureReleasesUserSlotAfterStartBasedCooldown()=runBlocking {
        var time=0L; var calls=0
        val api=Api(generator { calls++; throw ProviderError("offline") },authorize={"a"},
            userLimit=UserGenerationLimit { time },logFailure={_,_->})
        assertEquals(503,api.handle("application/json",null) { input().toString() }.first)
        assertEquals(429,api.handle("application/json",null) { input().toString() }.first)
        time=3000
        assertEquals(503,api.handle("application/json",null) { input().toString() }.first)
        assertEquals(2,calls)
    }

    @Test fun duplicateStopsAreZoneCheckedOncePerModeAndRetainZoneAlternatives()=runBlocking {
        val stop=obj("lat" to end.latitude,"lon" to end.longitude)
        val routes=List(100) { obj("gtfsId" to "STM:$it","mode" to "BUS","stops" to List(100) {stop}) }
        var checks=0
        val result=destinationCandidates(parseRequest(input(modes=listOf("bus"))),routes,zones(),null,
            zoneCheck={_,_-> checks++; true })
        assertEquals(listOf(end),result); assertEquals(1,checks)
        val mixed=parseRequest(input(modes=listOf("bus"))).copy(modes=listOf("bus","train"),
            zones=mapOf("bus" to listOf("A"),"train" to listOf("B")))
        val train=obj("gtfsId" to "EXO:1","mode" to "RAIL","stops" to listOf(stop))
        assertEquals(listOf(end),destinationCandidates(mixed,routes+train,zones(),null,zoneCheck={_,selected-> selected==listOf("B") }))
    }

    @Test fun preparationDeadlineReturnsSavedWalkingFallbackAndParentCancellationPropagates()=runBlocking {
        val request=parseRequest(input(modes=listOf("walk","bus")))
        val entered=CompletableDeferred<Unit>()
        val g=Generator(emptyList(),zones(),{ response() },{now},sample={ candidate ->
            if(candidate.modes==listOf("walk")) listOf(end) else { entered.complete(Unit); awaitCancellation() }
        },timeoutMillis=200,diagnostic={})
        val result=g.generate(request)
        assertTrue(entered.isCompleted)
        assertEquals("success",result["status"].text())
        assertEquals(1260.0,result["elapsedSeconds"].number()!!,0.0)
        var checks=0
        val route=obj("gtfsId" to "STM:55","mode" to "BUS","stops" to List(500) {
            obj("lat" to end.latitude,"lon" to end.longitude)
        })
        val job=async {
            destinationCandidates(request,listOf(route),zones(),null,checkpoint={
                if(++checks==2) currentCoroutineContext().cancel()
                yield()
            })
        }
        try { job.await(); fail("Preparation ignored cancellation") } catch(_: CancellationException) { }
        assertEquals(2,checks)
        try {
            accepts(response().field("data").field("planConnection").field("edges").items().single().field("node")!!,
                parseRequest(input()),end,zones(),null) { throw CancellationException("cancel") }
            fail("Validation swallowed cancellation")
        } catch(_: CancellationException) { }
    }

    private fun streetResponse(query: JsonObject, extraSeconds: Long=0): JsonObject {
        val variables=query.field("variables")
        val origin=Point(variables.field("originLat").number()!!,variables.field("originLon").number()!!)
        val target=Point(variables.field("destinationLat").number()!!,variables.field("destinationLon").number()!!)
        val departure=Instant.parse(variables.field("departure").text())
        val meters=distance(origin,target)
        val seconds=kotlin.math.ceil(meters/variables.field("walkSpeed").number()!!).toLong()+extraSeconds
        val leg=obj("mode" to "WALK","distance" to meters,"duration" to seconds,
            "start" to obj("scheduledTime" to departure.toString()),
            "end" to obj("scheduledTime" to departure.plusSeconds(seconds).toString()),
            "legGeometry" to obj("length" to 2,"points" to encode(listOf(origin,target))))
        return obj("data" to obj("planConnection" to obj("routingErrors" to emptyList<Any>(),"edges" to
            listOf(obj("node" to obj("duration" to seconds,"waitingTime" to 0,"legs" to listOf(leg)))))))
    }

    @Test fun walkBikeRoutesBothSectionsAtTheirOwnSpeedAndKeepsCyclingMain()=runBlocking {
        val queries=mutableListOf<JsonObject>()
        val request=parseRequest(input(modes=listOf("walk","bike")))
        val g=generator { query -> queries+=query; streetResponse(query) }
        val result=g.generate(request)
        assertEquals("success",result["status"].text()); assertEquals(3,queries.size)
        assertEquals(request.bikeSpeedKmh/3.6,queries[0].field("variables").field("walkSpeed").number()!!,1e-9)
        assertEquals(request.speed,queries[1].field("variables").field("walkSpeed").number()!!,1e-9)
        assertEquals(request.bikeSpeedKmh/3.6,queries[2].field("variables").field("walkSpeed").number()!!,1e-9)
        val node=result.field("otp").field("data").field("planConnection").field("edges").items().single().field("node")!!
        val legs=node.field("legs").items()
        assertEquals(listOf("WALK","BICYCLE"),legs.map { it.field("mode").text() })
        assertTrue(legs[1].field("distance").number()!!>legs[0].field("distance").number()!!)
        assertTrue(legs[1].field("duration").number()!!>legs[0].field("duration").number()!!)
        assertEquals(legs[0].field("end").field("scheduledTime"),legs[1].field("start").field("scheduledTime"))
        assertEquals(input()["origin"],result["origin"])
        val walking=response().field("data").field("planConnection").field("edges").items().single().field("node")!!
        assertFalse(accepts(walking,request,end,zones(),null))
        assertFalse(accepts(bikeResponse(response(),request.copy(modes=listOf("bike"))).field("data").field("planConnection")
            .field("edges").items().single().field("node")!!,request,end,zones(),null))
    }

    @Test fun walkBikeDoesNotInventMissingSectionsOrExceedAttemptCap()=runBlocking {
        val request=parseRequest(input(modes=listOf("walk","bike")))
        for(failedCall in listOf(2,3)) {
            var calls=0
            val result=generator { query -> if(++calls==failedCall) emptyPlanningResponse() else streetResponse(query) }.generate(request)
            assertEquals("no_trip",result["status"].text())
        }
        var calls=0
        val limited=Generator(emptyList(),zones(),{ q-> calls++; streetResponse(q) },{now},{listOf(end)},maxAttempts=2)
        val result=limited.generate(request)
        assertEquals("no_trip",result["status"].text()); assertEquals(2,calls)
        val entered=CompletableDeferred<Unit>()
        val cancellable=generator { query ->
            if(query.field("variables").field("walkSpeed").number()==request.speed) { entered.complete(Unit); awaitCancellation() }
            else streetResponse(query)
        }
        val job=async { cancellable.generate(request) }; entered.await(); job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test fun walkBikeBudgetIncludesBothSectionsAndOrientationAtExactBoundary()=runBlocking {
        val target=Point(45.504,-73.6)
        for(over in listOf(0L,1L)) {
            var calls=0; var approachSeconds=0L
            val g=Generator(emptyList(),zones(),{ query ->
                calls++
                val base=streetResponse(query)
                val leg=base.field("data").field("planConnection").field("edges").items().single().field("node")!!
                if(calls==2) approachSeconds=leg.field("duration").number()!!.toLong()
                if(calls==3) streetResponse(query,300-orientationSeconds(distance(start,target))-approachSeconds-
                    leg.field("duration").number()!!.toLong()+over) else base
            },{now},{listOf(target)})
            val result=g.generate(parseRequest(input(modes=listOf("walk","bike"))).copy(minutes=5))
            if(over==0L) assertEquals(300.0,result["elapsedSeconds"].number()!!,0.0)
            else assertEquals("no_trip",result["status"].text())
        }
    }

}
