package transit

import kotlinx.serialization.json.*
import kotlin.math.*
import kotlin.random.Random

internal val json = Json { ignoreUnknownKeys = true }
internal fun value(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to value(it.value) })
    is Iterable<*> -> JsonArray(v.map(::value))
    else -> error("Unsupported JSON value")
}
internal fun obj(vararg pairs: Pair<String, Any?>) = value(mapOf(*pairs)).jsonObject
internal fun JsonElement?.field(key: String): JsonElement? = (this as? JsonObject)?.get(key)
internal fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonElement?.number(): Double? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
internal fun JsonElement?.items(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()
internal fun JsonElement?.strings() = items().map { it.text() ?: throw InputError("Expected text selections.") }
internal class InputError(message: String) : IllegalArgumentException(message)
internal class ProviderError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
internal fun checkInput(condition: Boolean, message: String) { if (!condition) throw InputError(message) }

internal data class Point(val latitude: Double, val longitude: Double) {
    init { checkInput(latitude.isFinite() && longitude.isFinite() && abs(latitude) <= 90 && abs(longitude) <= 180, "Invalid coordinates.") }
    fun toJson() = obj("latitude" to latitude, "longitude" to longitude)
}
internal fun point(v: JsonElement?) = Point(v.field("latitude").number() ?: throw InputError("Choose a valid starting point."),
    v.field("longitude").number() ?: throw InputError("Choose a valid starting point."))
private const val earthRadius = 6_371_008.8
internal fun distance(a: Point, b: Point): Double {
    val h = sin(Math.toRadians(b.latitude-a.latitude)/2).pow(2) + cos(Math.toRadians(a.latitude))*cos(Math.toRadians(b.latitude))*sin(Math.toRadians(b.longitude-a.longitude)/2).pow(2)
    return 2*earthRadius*asin(sqrt(h.coerceIn(0.0, 1.0)))
}
private val center = Point(45.5019,-73.5673)
private val serviceRadius = distance(center,Point(45.98474970,-73.18629259))+5000
internal fun inServiceArea(p: Point) = distance(center,p) <= serviceRadius
internal fun destination(origin: Point, meters: Double, bearing: Double): Point {
    val a=meters/earthRadius; val lat=Math.toRadians(origin.latitude); val lon=Math.toRadians(origin.longitude)
    val y=asin(sin(lat)*cos(a)+cos(lat)*sin(a)*cos(bearing))
    val x=lon+atan2(sin(bearing)*sin(a)*cos(lat),cos(a)-sin(lat)*sin(y))
    return Point(Math.toDegrees(y), (Math.toDegrees(x)+540)%360-180)
}
/** Polygon rings include boundaries, with holes excluded. */
internal fun inPolygon(p: Point, geometry: JsonElement): Boolean {
    fun ring(r: JsonElement): Boolean {
        val coords=r.items().map { it.items().map { n -> n.number() ?: error("Invalid polygon") } }
        var inside=false
        for (i in coords.indices) {
            val (xi,yi)=coords[i]; val (xj,yj)=coords[(i+coords.size-1)%coords.size]
            val cross=(p.longitude-xi)*(yj-yi)-(p.latitude-yi)*(xj-xi)
            if(abs(cross)<1e-12 && p.longitude in min(xi,xj)..max(xi,xj) && p.latitude in min(yi,yj)..max(yi,yj)) return true
            if((yi>p.latitude)!=(yj>p.latitude) && p.longitude<(xj-xi)*(p.latitude-yi)/(yj-yi)+xi) inside=!inside
        }
        return inside
    }
    val c=geometry.field("coordinates") ?: error("Missing polygon")
    val polygons=if(geometry.field("type").text()=="Polygon") listOf(c) else c.items()
    return polygons.any { polygon -> val rings=polygon.items(); rings.isNotEmpty() && ring(rings.first()) && rings.drop(1).none(::ring) }
}
internal val modeNames=mapOf("walk" to "WALK", "bike" to "BICYCLE", "bus" to "BUS", "metro" to "SUBWAY", "train" to "RAIL")
internal val walkingSpeeds=mapOf("Amble" to 3.0,"Walk" to 4.8,"Dash" to 6.8)
internal fun orientationSeconds(meters: Double)=ceil(meters*60/1000).toLong()
internal data class Request(val origin: JsonObject, val minutes: Int, val modes: List<String>, val pace: String,
    val zones: Map<String,List<String>>, val lines: List<String>, val excluded: List<Point>) {
    val start get()=point(origin["point"])
    val speed get()=walkingSpeeds.getValue(pace)/3.6
}
internal fun parseRequest(v: JsonElement): Request {
    val origin=v.field("origin") as? JsonObject ?: throw InputError("Choose a starting point.")
    checkInput(inServiceArea(point(origin["point"])),"Starting point is outside the supported area.")
    checkInput(origin["label"].text()?.let { it.isNotBlank() && it.length<=160 } == true,"Choose a named starting point.")
    val minutes=v.field("plannedMinutes").number() ?: throw InputError("Choose 5-360 minutes.")
    checkInput(minutes in 5.0..360.0 && minutes%5==0.0,"Choose 5-360 minutes in five-minute steps.")
    val modes=v.field("modes").strings()
    checkInput(modes.isNotEmpty() && modes.distinct()==modes && modes.all { it in modeNames },"Choose supported transport modes.")
    val pace=v.field("walkingPreference")?.text() ?: if(v.field("walkingPreference")==null) "Amble" else throw InputError("Choose Amble, Walk or Dash.")
    checkInput(pace in walkingSpeeds,"Choose Amble, Walk or Dash.")
    val rawZones=v.field("zonesByTransport")
    checkInput(rawZones==null || rawZones is JsonObject,"Invalid transit zones.")
    val zones=(rawZones as? JsonObject)?.mapValues { (_,z) -> z.strings() } ?: emptyMap()
    val transit=modes.filter { it !in listOf("walk","bike") }
    checkInput(zones.keys.all { it in transit },"Invalid transport-zone selections.")
    transit.forEach { mode -> val z=zones[mode].orEmpty(); checkInput(z.isNotEmpty() && z.distinct()==z && z.all { it in listOf("A","B","C","D") },"Choose valid transit zones for $mode.") }
    val rawLines=v.field("selectedRouteIds")
    checkInput(rawLines==null || rawLines is JsonArray,"Invalid selected lines.")
    val lines=rawLines.strings(); checkInput(lines.size<=50 && lines.all { it.length<=120 },"Invalid selected lines.")
    val rawExcluded=v.field("excludedDestinations")
    checkInput(rawExcluded==null || rawExcluded is JsonArray,"Invalid previous destinations.")
    val excluded=rawExcluded.items().map(::point); checkInput(excluded.size<=10,"Too many previous destinations.")
    origin["osmReference"]?.takeUnless { it==JsonNull }?.let { ref ->
        val id=ref.field("id").number()
        checkInput(ref.field("type").text() in listOf("Node","Way","Relation") && id!=null && id>0 && id<=9_007_199_254_740_991.0 && id%1==0.0,"Invalid origin reference.")
    }
    val clean=JsonObject(origin.filterKeys { it in listOf("label","point","osmReference") })
    return Request(clean,minutes.toInt(),modes,pace,zones,lines,excluded)
}
/** Uniform-area arbitrary coordinates, independent of stops or a POI catalogue. */
internal fun candidates(input: Request, random: Random=Random.Default): List<Point> {
    val speed=when { input.modes.any { it in listOf("train","metro") } -> 30.0; "bus" in input.modes -> 15.0; "bike" in input.modes -> 8.0; else -> input.speed }
    val radius=min(100_000.0,input.minutes*60/(1/speed+0.06)); val inner=cos(min(250.0,radius/4)/earthRadius); val outer=cos(radius/earthRadius)
    val result=mutableListOf<Point>()
    repeat(16*64) {
        if(result.size==16) return result
        val meters=earthRadius*acos((inner-random.nextDouble()*(inner-outer)).coerceIn(-1.0,1.0))
        val p=destination(input.start,meters,random.nextDouble()*2*PI)
        if(inServiceArea(p) && input.excluded.none { distance(p,it)<100 } && result.none { distance(p,it)<100 }) result+=p
    }
    return result
}
internal fun decodePolyline(text: String): List<Point> {
    require(text.length<=1_000_000)
    var index=0; var lat=0; var lon=0
    fun delta(): Int {
        var bits=0; var shift=0; var chunk: Int
        do { require(index<text.length && shift<=25); chunk=text[index++].code-63; require(chunk in 0..63); bits=bits or ((chunk and 31) shl shift); shift+=5 } while(chunk>=32)
        return (bits ushr 1) xor -(bits and 1)
    }
    return buildList { while(index<text.length) { lat+=delta(); lon+=delta(); add(Point(lat/100_000.0,lon/100_000.0)) } }
}
