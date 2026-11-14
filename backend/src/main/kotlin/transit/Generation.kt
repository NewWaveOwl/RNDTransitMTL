package transit

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.math.*

internal class FareZones(collection: JsonElement) {
    val features: List<Pair<String,JsonElement>>
    init {
        require(collection.field("type").text()=="FeatureCollection") { "Fare zones must be GeoJSON." }
        features=collection.field("features").items().map { feature ->
            val names=(feature.field("properties") as? JsonObject)?.values.orEmpty().mapNotNull { it.text() }
                .mapNotNull { Regex("^(?:zone\\s*)?([ABCD])$",RegexOption.IGNORE_CASE).matchEntire(it.trim())?.groupValues?.get(1)?.uppercase() }.distinct()
            val geometry=feature.field("geometry") ?: error("Missing fare geometry")
            require(names.size==1 && geometry.field("type").text() in listOf("Polygon","MultiPolygon")) { "Unknown ARTM zone or geometry." }
            names.single() to geometry
        }
        require(features.map { it.first }.toSet()==setOf("A","B","C","D")) { "Expected all four ARTM zones." }
    }
    fun allows(point: Point, selected: List<String>)=features.any { (zone,geometry) -> zone in selected && inPolygon(point,geometry) }
}
internal fun allowedLines(input: Request, routes: List<JsonElement>): Set<String>? {
    if(input.lines.isEmpty()) return null
    val colors=mapOf("Orange" to "2","Green" to "1","Blue" to "5","Yellow" to "4")
    val allowed=mutableSetOf<String>(); val limited=mutableSetOf<String>()
    input.lines.forEach { id ->
        val mode=id.substringBefore(':'); val label=id.substringAfter(':',"")
        checkInput(mode in input.modes && mode !in listOf("walk","bike"),"Selected line requires its transport mode.")
        val matches=routes.filter { it.field("mode").text()==modeNames[mode] &&
            (it.field("gtfsId").text()==id || it.field("shortName").text()==(colors[label] ?: label) ||
            (mode=="train" && it.field("longName").text()?.startsWith("$label -")==true)) }
        checkInput(matches.isNotEmpty(),"Selected line is unavailable: $id.")
        allowed+=matches.mapNotNull { it.field("gtfsId").text() }; limited+=mode
    }
    allowed+=routes.filter { r -> input.modes.any { it !in limited && modeNames[it]==r.field("mode").text() } }.mapNotNull { it.field("gtfsId").text() }
    return allowed
}
internal val planQuery=object {}.javaClass.getResource("/plan.graphql")!!.readText()
internal fun otpRequest(input: Request, end: Point, departure: Instant, allowed: Set<String>?): JsonObject {
    val transit=input.modes.filter { it !in listOf("walk","bike") }
    val direct=input.modes.filter { it in listOf("walk","bike") }.map { modeNames.getValue(it) }
    val modes=mutableMapOf<String,Any?>("direct" to direct,"directOnly" to transit.isEmpty(),"transitOnly" to direct.isEmpty())
    if(transit.isNotEmpty()) modes["transit"]=mapOf("access" to listOf("WALK"),"egress" to listOf("WALK"),"transfer" to listOf("WALK"),"transit" to transit.map { mapOf("mode" to modeNames.getValue(it)) })
    return obj("query" to planQuery,"variables" to obj("originLat" to input.start.latitude,"originLon" to input.start.longitude,
        "destinationLat" to end.latitude,"destinationLon" to end.longitude,"departure" to departure.toString(),"walkSpeed" to input.speed,
        "modes" to modes,"filters" to allowed?.let { listOf(mapOf("include" to listOf(mapOf("routes" to it.toList())))) },"excludeRealtime" to transit.isEmpty()),
        "extensions" to obj("realtimeFeedsFromItinerary" to true,
            "realtimeFeeds" to buildList { if(transit.any { it in listOf("bus","metro") }) add("STM"); if("train" in transit) add("EXO_TRAINS") }))
}
internal fun accepts(itinerary: JsonElement,input: Request,end: Point,zones: FareZones,allowed: Set<String>?): Boolean = try {
    var previous=input.start; var meters=0.0
    val legs=itinerary.field("legs").items()
    require(legs.isNotEmpty())
    legs.forEach { leg ->
        val mode=modeNames.entries.firstOrNull { it.value==leg.field("mode").text() }?.key ?: error("Unknown mode")
        val access=mode=="walk" && input.modes.any { it !in listOf("walk","bike") }
        require(mode in input.modes || access)
        val distance=leg.field("distance").number() ?: error("Missing distance")
        val duration=leg.field("duration").number() ?: error("Missing duration")
        require(distance.isFinite() && distance>=0 && duration.isFinite() && duration>=0); meters+=distance
        val points=decodePolyline(leg.field("legGeometry").field("points").text() ?: error("Missing geometry"))
        require(points.size>=2 && points.size.toDouble()==leg.field("legGeometry").field("length").number() && distance(previous,points.first())<=35)
        require(points.all(::inServiceArea))
        if(mode !in listOf("walk","bike")) {
            val route=leg.field("route").field("gtfsId").text() ?: error("Missing route")
            require(allowed==null || route in allowed)
            val selected=input.zones.getValue(mode)
            points.forEachIndexed { i,p ->
                require(zones.allows(p,selected))
                if(i>0) {
                    val a=points[i-1]; val steps=ceil(distance(a,p)/100).toInt()
                    for(step in 1 until steps) require(zones.allows(Point(a.latitude+(p.latitude-a.latitude)*step/steps,a.longitude+(p.longitude-a.longitude)*step/steps),selected))
                }
            }
        }
        previous=points.last()
    }
    meters>0 && distance(previous,end)<=35
} catch (_: IllegalArgumentException) { false } catch (_: IllegalStateException) { false } catch (_: NoSuchElementException) { false }
internal fun elapsed(itinerary: JsonElement,start: Instant): Long? {
    val duration=itinerary.field("duration").number() ?: return null
    if(!duration.isFinite() || duration<=0) return null
    val last=itinerary.field("legs").items().lastOrNull() ?: return null
    val end=last.field("end")
    val arrival=if(last.field("realTime")==JsonPrimitive(true)) end.field("estimated").field("time").text() ?: end.field("scheduledTime").text() else end.field("scheduledTime").text()
    val seconds=runCatching { (Instant.parse(arrival).toEpochMilli()-start.toEpochMilli())/1000.0 }.getOrNull() ?: return null
    return if(seconds>0) ceil(max(seconds,duration)).toLong() else null
}
/** Bounded on-demand search; timeouts and cancellation propagate through the HTTP client. */
internal class Generator(private val routes: List<JsonElement>, private val zones: FareZones,
    private val route: suspend (JsonObject)->JsonObject, private val now: ()->Instant = Instant::now,
    private val sample: (Request)->List<Point> = { candidates(it) }, private val timeoutMillis: Long=15_000) {
    suspend fun generate(input: Request): JsonObject {
        val start=now(); var attempts=0
        val allowed=allowedLines(input,routes)
        val result=withTimeoutOrNull(timeoutMillis) {
            var failures=0
            for(end in sample(input).take(16)) {
                currentCoroutineContext().ensureActive(); attempts++
                val body=try { route(otpRequest(input,end,start,allowed)) }
                    catch(e: CancellationException) { throw e }
                    catch(e: Exception) { if(++failures>=2) throw ProviderError("Routing service unavailable.",e); continue }
                val plan=body.field("data").field("planConnection")
                val noRoute=setOf("LOCATION_NOT_FOUND","NO_DIRECT_MODE_CONNECTION","NO_STOPS_IN_RANGE","NO_TRANSIT_CONNECTION","NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW","OUTSIDE_SERVICE_PERIOD","WALKING_BETTER_THAN_TRANSIT","OUTSIDE_BOUNDS")
                if(body["errors"].items().isNotEmpty() || plan.field("edges") !is JsonArray || plan.field("routingErrors").items().any { it.field("code").text() !in noRoute }) throw ProviderError("OTP returned an invalid planning response.")
                for(edge in plan.field("edges").items()) {
                    val itinerary=edge.field("node") ?: continue
                    val travel=elapsed(itinerary,start) ?: continue
                    val meters=itinerary.field("legs").items().sumOf { it.field("distance").number() ?: Double.NaN }
                    if(!meters.isFinite() || meters<=0) continue
                    val buffer=orientationSeconds(meters); val total=travel+buffer
                    if(total>input.minutes*60 || !accepts(itinerary,input,end,zones,allowed)) continue
                    return@withTimeoutOrNull obj("status" to "success","origin" to input.origin,"destination" to obj("label" to "Random destination","point" to end.toJson()),
                        "elapsedSeconds" to total,"travelTimeSeconds" to travel,"orientationBufferSeconds" to buffer,"walkingPreference" to input.pace,
                        "walkingSpeedKmh" to walkingSpeeds.getValue(input.pace),"departure" to start.toString(),"attempts" to attempts,
                        "otp" to JsonObject(body+ ("data" to obj("planConnection" to JsonObject(plan!!.jsonObject+mapOf("routingErrors" to JsonArray(emptyList()),"edges" to JsonArray(listOf(edge))))))))
                }
            }
            if(failures>0) throw ProviderError("Routing service unavailable.")
            obj("status" to "no_trip","message" to "No reachable trip fits these choices. Try again or increase the time.","attempts" to attempts)
        }
        return result ?: obj("status" to "no_trip","message" to "Trip search timed out. Try again.","attempts" to attempts)
    }
}
