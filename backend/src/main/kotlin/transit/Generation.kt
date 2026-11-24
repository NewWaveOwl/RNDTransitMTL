package transit

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.math.*
import kotlin.time.TimeSource

internal class FareZones(collection: JsonElement) {
    private val features: List<Pair<String,PreparedGeometry>>
    init {
        require(collection.field("type").text()=="FeatureCollection") { "Fare zones must be GeoJSON." }
        features=collection.field("features").items().map { feature ->
            val names=(feature.field("properties") as? JsonObject)?.values.orEmpty().mapNotNull { it.text() }
                .mapNotNull { Regex("^(?:zone\\s*)?([ABCD])$",RegexOption.IGNORE_CASE).matchEntire(it.trim())?.groupValues?.get(1)?.uppercase() }.distinct()
            val geometry=feature.field("geometry") ?: error("Missing fare geometry")
            require(names.size==1 && geometry.field("type").text() in listOf("Polygon","MultiPolygon")) { "Unknown ARTM zone or geometry." }
            names.single() to PreparedGeometry(geometry)
        }
        require(features.map { it.first }.toSet()==setOf("A","B","C","D")) { "Expected all four ARTM zones." }
    }
    fun allows(point: Point, selected: List<String>, check: ()->Unit = {})=features.any { (zone,geometry) -> zone in selected && geometry.contains(point,check) }
}
internal fun allowedLines(input: Request, routes: List<JsonElement>, check: ()->Unit = {}): Set<String>? {
    if(input.lines.isEmpty()) return null
    val colors=mapOf("Orange" to "2","Green" to "1","Blue" to "5","Yellow" to "4")
    val allowed=mutableSetOf<String>(); val limited=mutableSetOf<String>()
    input.lines.forEach { id ->
        check()
        val mode=id.substringBefore(':'); val label=id.substringAfter(':',"")
        checkInput(mode in input.modes && mode !in listOf("walk","bike"),"Selected line requires its transport mode.")
        val matches=routes.filter { check(); it.field("mode").text()==modeNames[mode] &&
            (it.field("gtfsId").text()==id || it.field("shortName").text()==(colors[label] ?: label) ||
            (mode=="train" && it.field("longName").text()?.startsWith("$label -")==true)) }
        checkInput(matches.isNotEmpty(),"Selected line is unavailable: $id.")
        allowed+=matches.mapNotNull { it.field("gtfsId").text() }; limited+=mode
    }
    allowed+=routes.filter { r -> check(); input.modes.any { it !in limited && modeNames[it]==r.field("mode").text() } }.mapNotNull { it.field("gtfsId").text() }
    return allowed
}
internal val planQuery=object {}.javaClass.getResource("/plan.graphql")!!.readText()
internal fun otpRequest(input: Request, end: Point, departure: Instant, allowed: Set<String>?): JsonObject {
    val transit=input.modes.filter { it !in listOf("walk","bike") }
    // Bike deliberately uses the pedestrian graph at the chosen cycling speed.
    // Combined requests keep transit access at walking pace; a direct fallback is bounded separately.
    val direct=if(input.bikeOnly || "walk" in input.modes) listOf("WALK") else emptyList()
    val modes=mutableMapOf<String,Any?>("direct" to direct,"directOnly" to transit.isEmpty(),"transitOnly" to direct.isEmpty())
    if(transit.isNotEmpty()) modes["transit"]=mapOf("access" to listOf("WALK"),"egress" to listOf("WALK"),"transfer" to listOf("WALK"),"transit" to transit.map { mapOf("mode" to modeNames.getValue(it)) })
    return obj("query" to planQuery,"variables" to obj("originLat" to input.start.latitude,"originLon" to input.start.longitude,
        "destinationLat" to end.latitude,"destinationLon" to end.longitude,"departure" to departure.toString(),"walkSpeed" to input.directSpeed,
        "modes" to modes,"filters" to allowed?.let { listOf(mapOf("include" to listOf(mapOf("routes" to it.toList())))) },"excludeRealtime" to transit.isEmpty()),
        "extensions" to obj("realtimeFeedsFromItinerary" to true,
            "realtimeFeeds" to buildList { if(transit.any { it in listOf("bus","metro") }) add("STM"); if("train" in transit) add("EXO_TRAINS") }))
}
/** Preserve OTP geometry/timing but identify the accelerated pedestrian route as Bike. */
internal fun bikeResponse(body: JsonObject, input: Request): JsonObject {
    if(!input.bikeOnly) return body
    val data=body.field("data") as? JsonObject ?: return body
    val plan=data["planConnection"] as? JsonObject ?: return body
    val edges=plan["edges"] as? JsonArray ?: return body
    val converted=edges.mapNotNull { edge ->
        val node=edge.field("node") as? JsonObject ?: return@mapNotNull null
        val legs=node["legs"].items()
        if(legs.isEmpty() || legs.any { it.field("mode").text()!="WALK" }) return@mapNotNull null
        val bikeLegs=legs.map { JsonObject(it.jsonObject + ("mode" to JsonPrimitive("BICYCLE"))) }
        JsonObject(edge.jsonObject + ("node" to JsonObject(node + ("legs" to JsonArray(bikeLegs)))))
    }
    return JsonObject(body + ("data" to JsonObject(data + ("planConnection" to JsonObject(plan + ("edges" to JsonArray(converted)))))))
}
internal fun accepts(itinerary: JsonElement,input: Request,end: Point,zones: FareZones,allowed: Set<String>?, check: ()->Unit = {}): Boolean = try {
    var previous=input.start; var meters=0.0
    val legs=itinerary.field("legs").items()
    require(legs.isNotEmpty())
    require("walk" in input.modes || legs.any { it.field("mode").text() != "WALK" })
    if(input.walkBike) {
        fun amount(mode: String,key: String)=legs.filter { it.field("mode").text()==mode }.sumOf { it.field(key).number() ?: 0.0 }
        val walking=amount("WALK","distance"); val cycling=amount("BICYCLE","distance")
        val walkingTime=amount("WALK","duration"); val cyclingTime=amount("BICYCLE","duration")
        require(walking>0 && cycling>walking && walkingTime>0 && cyclingTime>walkingTime)
    }
    legs.forEach { leg ->
        check()
        val mode=modeNames.entries.firstOrNull { it.value==leg.field("mode").text() }?.key ?: error("Unknown mode")
        val access=mode=="walk" && input.modes.any { it !in listOf("walk","bike") }
        require(mode in input.modes || access)
        val distance=leg.field("distance").number() ?: error("Missing distance")
        val duration=leg.field("duration").number() ?: error("Missing duration")
        require(distance.isFinite() && distance>=0 && duration.isFinite() && duration>=0); meters+=distance
        val points=decodePolyline(leg.field("legGeometry").field("points").text() ?: error("Missing geometry"),check)
        require(points.size>=2 && points.size.toDouble()==leg.field("legGeometry").field("length").number() && distance(previous,points.first())<=35)
        require(points.all(::inServiceArea))
        if(mode !in listOf("walk","bike")) {
            val route=leg.field("route").field("gtfsId").text() ?: error("Missing route")
            require(allowed==null || route in allowed)
            val selected=input.zones.getValue(mode)
            points.forEachIndexed { i,p ->
                require(zones.allows(p,selected,check))
                if(i>0) {
                    val a=points[i-1]; val steps=ceil(distance(a,p)/100).toInt()
                    for(step in 1 until steps) { check(); require(zones.allows(Point(a.latitude+(p.latitude-a.latitude)*step/steps,a.longitude+(p.longitude-a.longitude)*step/steps),selected,check)) }
                }
            }
        }
        previous=points.last()
    }
    meters>0 && distance(previous,end)<=35
} catch (cancelled: CancellationException) { throw cancelled } catch (_: IllegalArgumentException) { false } catch (_: IllegalStateException) { false } catch (_: NoSuchElementException) { false }
internal fun elapsed(itinerary: JsonElement,start: Instant): Long? {
    val duration=itinerary.field("duration").number() ?: return null
    if(!duration.isFinite() || duration<=0) return null
    val last=itinerary.field("legs").items().lastOrNull() ?: return null
    val end=last.field("end")
    val arrival=if(last.field("realTime")==JsonPrimitive(true)) end.field("estimated").field("time").text() ?: end.field("scheduledTime").text() else end.field("scheduledTime").text()
    val seconds=runCatching { (Instant.parse(arrival).toEpochMilli()-start.toEpochMilli())/1000.0 }.getOrNull() ?: return null
    return if(seconds>0) ceil(max(seconds,duration)).toLong() else null
}
/** Stops are linked network anchors; direct-mode alternatives retain geographic sampling. */
internal suspend fun destinationCandidates(input: Request, routes: List<JsonElement>, zones: FareZones, allowed: Set<String>?,
    checkpoint: suspend ()->Unit = { yield() }, zoneCheck: ((Point,List<String>)->Boolean)? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default, random: kotlin.random.Random = kotlin.random.Random.Default): List<Point> = withContext(dispatcher) {
    val context=currentCoroutineContext()
    val transitSelected=input.modes.any { it !in listOf("walk","bike") }
    val seen=mutableSetOf<Pair<String,Point>>()
    val stops=linkedSetOf<Point>()
    val origin=input.start
    var examined=0
    for(route in routes) {
        checkpoint(); context.ensureActive()
        val mode=modeNames.entries.firstOrNull { it.value==route.field("mode").text() }?.key ?: continue
        if(mode in listOf("walk","bike") || transitSelected &&
            (mode !in input.modes || allowed!=null && route.field("gtfsId").text() !in allowed)) continue
        for(stop in route.field("stops").items()) {
            if(examined++%128==0) { checkpoint(); context.ensureActive() }
            val lat=stop.field("lat").number(); val lon=stop.field("lon").number()
            if(lat==null || lon==null || !lat.isFinite() || !lon.isFinite() || abs(lat)>90 || abs(lon)>180) continue
            val point=Point(lat,lon)
            // A rejected bus-zone point must still be considered for another selected mode/zone.
            if(!seen.add(mode to point) || point in stops) continue
            if(!inServiceArea(point) || distance(origin,point)<=100 || input.excluded.any { distance(point,it)<100 }) continue
            if(transitSelected) {
                val selected=input.zones.getValue(mode)
                if(!(zoneCheck?.invoke(point,selected) ?: zones.allows(point,selected) { context.ensureActive() })) continue
            }
            stops+=point
        }
    }
    checkpoint(); context.ensureActive()
    val speed=if(transitSelected) 8.0 else input.directSpeed
    val target=input.minutes*60.0/(1/speed+0.06)
    val ranked=stops.shuffled(random).mapIndexed { index,point ->
        if(index%128==0) context.ensureActive()
        point to abs(distance(origin,point)-target)
    }
    val anchors=ranked.sortedBy { it.second }.take(96).map { it.first }
    checkpoint(); context.ensureActive()
    val geographic=candidates(input,random,check={ context.ensureActive() })
    if(anchors.isEmpty()) geographic else if(input.modes.any { it in listOf("walk","bike") })
        buildList { for(i in 0 until 64) { anchors.getOrNull(i)?.let(::add); geographic.getOrNull(i)?.let(::add) } } else anchors
}

internal fun searchBudgetSeconds(minutes: Int): Long = 20L + minutes / 60

/** Query/schema/authentication failures affect all destinations and must not be retried per candidate.
 * @param body OTP GraphQL response, inspected without logging provider messages.
 */
internal fun systemicOtpError(body: JsonObject): Boolean = body["errors"].items().any { error ->
    val code = error.field("extensions").field("code").text()
    val message = error.field("message").text().orEmpty()
    code in setOf("GRAPHQL_VALIDATION_FAILED", "GRAPHQL_PARSE_FAILED", "UNAUTHENTICATED", "FORBIDDEN") ||
        message.startsWith("Validation error", ignoreCase = true) ||
        message.startsWith("Syntax Error", ignoreCase = true) ||
        message.startsWith("Cannot query field", ignoreCase = true) ||
        message.startsWith("Unknown argument", ignoreCase = true) ||
        message.startsWith("Unknown type", ignoreCase = true) ||
        (message.startsWith("Variable ") && ("was not provided" in message || "got invalid value" in message))
}

/** Bounded on-demand search; timeouts and cancellation propagate through the HTTP client. */
internal class Generator(private val routes: List<JsonElement>, private val zones: FareZones,
    private val route: suspend (JsonObject)->JsonObject, private val now: ()->Instant = Instant::now,
    private val sample: (suspend (Request)->List<Point>)? = null, private val timeoutMillis: Long?=null,
    private val maxAttempts: Int=128,
    private val diagnostic: (String)->Unit = { println("RndTransitGeneration $it") },
    private val preparationDispatcher: CoroutineDispatcher = Dispatchers.Default) {
    suspend fun generate(input: Request, requestId: String = "internal"): JsonObject {
        if (input.roundTrip) return generateRoundTrip(input, routes, zones, route, now(), sample,
            timeoutMillis ?: searchBudgetSeconds(input.minutes) * 1000, maxAttempts, preparationDispatcher, requestId, diagnostic)
        if (input.destination != null) return routeToDestination(input, routes, zones, route, now(),
            timeoutMillis ?: 30_000)
        val timer=TimeSource.Monotonic.markNow()
        fun log(event: String) = diagnostic("request=$requestId elapsedMs=${timer.elapsedNow().inWholeMilliseconds} $event")
        log("search=start modes=${input.modes.joinToString()} minutes=${input.minutes}")
        val start=now(); var attempts=0
        var best: JsonObject? = null
        var bestTotal = Long.MIN_VALUE
        val budget=timeoutMillis ?: searchBudgetSeconds(input.minutes)*1000
        val result=withTimeoutOrNull(budget) {
            val searchContext=currentCoroutineContext()
            val allowed=withContext(preparationDispatcher) { allowedLines(input,routes) { searchContext.ensureActive() } }
            // Reserve at least three quarters of the deadline for the main search.
            // One direct attempt uses geographic candidates, never another scan of transit stops.
            val alternative=when {
                "walk" in input.modes && input.modes.any { it !in listOf("walk","bike") } -> "walk"
                "bike" in input.modes && !input.bikeOnly && !input.walkBike -> "bike"
                else -> null
            }
            if(alternative!=null && maxAttempts>0) {
                val mode=alternative
                val directInput=input.copy(modes=listOf(mode),zones=emptyMap(),lines=emptyList())
                val fallback=try {
                    Generator(emptyList(),zones,{ query -> attempts++; route(query) },{start},
                        sample ?: { request -> candidates(request) },(budget/4).coerceAtLeast(1),1,
                        { event -> diagnostic("phase=$mode $event") },preparationDispatcher).generate(directInput,requestId)
                } catch(e: CancellationException) { throw e } catch(_: ProviderError) { null }
                val total=fallback?.field("elapsedSeconds")?.number()?.toLong()
                if(fallback?.field("status").text()=="success" && total!=null && total>bestTotal) {
                    best=fallback; bestTotal=total
                }
            }
            var failures=0
            log("candidates=start")
            val pending = ArrayDeque((sample?.invoke(input) ?: destinationCandidates(input,routes,zones,allowed,
                dispatcher=preparationDispatcher)).filter { p ->
                input.excluded.none { distance(p,it)<100 } && inServiceArea(p) &&
                    (input.modes.any { it in listOf("walk","bike") } || input.zones.values.any { zones.allows(p,it) { searchContext.ensureActive() } })
            })
            log("candidates=ready count=${pending.size}")
            val visited = mutableListOf<Point>()
            while(pending.isNotEmpty() && attempts<maxAttempts) {
                val end=pending.removeFirst()
                if(visited.any { distance(it,end)<50 }) continue
                visited+=end
                currentCoroutineContext().ensureActive(); attempts++
                val routeTimer=TimeSource.Monotonic.markNow()
                log("attempt=$attempts otp=start")
                val body=try {
                    val initial=route(otpRequest(input,end,start,allowed))
                    val planned=if(input.walkBike) walkBikeResponse(initial,input,end,start,zones) { query ->
                        if(attempts>=maxAttempts) emptyPlanningResponse() else {
                            attempts++
                            log("attempt=$attempts otp=walk-bike-section")
                            route(query)
                        }
                    } else bikeResponse(initial,input)
                    planned.also { log("attempt=$attempts otp=response latencyMs=${routeTimer.elapsedNow().inWholeMilliseconds}") }
                }
                    catch(e: CancellationException) { throw e }
                    catch(e: Exception) { log("attempt=$attempts otp=failed type=${e.javaClass.simpleName} http=${(e as? ProviderError)?.httpStatus ?: "none"}"); if(++failures>=2) { if(best!=null) break else throw ProviderError("Routing service unavailable.",e) }; continue }
                currentCoroutineContext().ensureActive()
                val plan=body.field("data").field("planConnection")
                val codes=plan.field("routingErrors").items().mapNotNull { it.field("code").text() }
                    .filter { it.matches(Regex("[A-Z_]{1,80}")) }.take(8)
                if(codes.isNotEmpty() || body["errors"].items().isNotEmpty())
                    log("attempt=$attempts routingCodes=${codes.joinToString()} graphqlErrors=${body["errors"].items().size}")
                val noRoute=setOf("LOCATION_NOT_FOUND","NO_DIRECT_MODE_CONNECTION","NO_STOPS_IN_RANGE","NO_TRANSIT_CONNECTION","NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW","OUTSIDE_SERVICE_PERIOD","WALKING_BETTER_THAN_TRANSIT","OUTSIDE_BOUNDS")
                if(body["errors"].items().isNotEmpty() || plan.field("edges") !is JsonArray || plan.field("routingErrors").items().any { it.field("code").text() !in noRoute }) {
                    // A malformed candidate response does not invalidate other destinations.
                    // Share the existing failure budget with transport errors to bound retries.
                    if(systemicOtpError(body) || ++failures>=2) {
                        if(best!=null) break
                        throw ProviderError("OTP returned an invalid planning response.")
                    }
                    continue
                }
                for(edge in plan.field("edges").items()) {
                    val itinerary=edge.field("node") ?: continue
                    val travel=elapsed(itinerary,start) ?: continue
                    val meters=itinerary.field("legs").items().sumOf { it.field("distance").number() ?: Double.NaN }
                    if(!meters.isFinite() || meters<=0) continue
                    val buffer=orientationSeconds(meters); val total=travel+buffer
                    // Reuse known linked geometry to try a shorter destination when a route overshoots.
                    if(total>input.minutes*60) {
                        val points=runCatching { itinerary.field("legs").items().flatMap { decodePolyline(it.field("legGeometry").field("points").text()!!) { searchContext.ensureActive() } } }.getOrElse { error ->
                            if(error is CancellationException) throw error
                            emptyList()
                        }
                        if(points.size>2) {
                            val index=((points.lastIndex*input.minutes*60.0/total)*0.95).toInt().coerceIn(1,points.lastIndex)
                            val linked=points[index]
                            if(inServiceArea(linked) && input.excluded.none { distance(linked,it)<100 } &&
                                (input.modes.any { it in listOf("walk","bike") } || input.zones.values.any { zones.allows(linked,it) })) pending.addFirst(linked)
                        }
                        continue
                    }
                    val context=currentCoroutineContext()
                    if(!accepts(itinerary,input,end,zones,allowed) { context.ensureActive() }) { log("attempt=$attempts rejected=geometry-mode-line-zone"); continue }
                    if(total<=bestTotal) continue
                    bestTotal=total
                    log("attempt=$attempts accepted totalSeconds=$total")
                    best = obj("status" to "success","origin" to input.origin,"destination" to obj("label" to "Random destination","point" to end.toJson()),
                        "elapsedSeconds" to total,"travelTimeSeconds" to travel,"orientationBufferSeconds" to buffer,"walkingPreference" to input.pace,
                        "walkingSpeedKmh" to walkingSpeeds.getValue(input.pace),"departure" to start.toString(),"attempts" to attempts,
                        "otp" to JsonObject(body+ ("data" to obj("planConnection" to JsonObject(plan!!.jsonObject+mapOf("routingErrors" to JsonArray(emptyList()),"edges" to JsonArray(listOf(edge))))))))
                    if(total>=input.minutes*60*0.95) return@withTimeoutOrNull best
                }
            }
            if(best!=null) return@withTimeoutOrNull best
            if(failures>0) throw ProviderError("Routing service unavailable.")
            obj("status" to "no_trip","message" to "No reachable trip fits these choices. Try again or increase the time.","attempts" to attempts)
        }
        currentCoroutineContext().ensureActive()
        val accepted=result ?: best
        log("search=end deadline=${result==null} attempts=$attempts status=${accepted?.field("status").text() ?: "timeout"}")
        return accepted?.let { JsonObject(it + ("attempts" to JsonPrimitive(attempts))) } ?: obj("status" to "timeout","message" to "Trip search timed out. Try again.","attempts" to attempts)
    }
}
