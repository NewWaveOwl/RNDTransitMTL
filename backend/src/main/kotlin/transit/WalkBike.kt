package transit

import java.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import kotlin.math.*

internal fun emptyPlanningResponse()=obj("data" to obj("planConnection" to obj(
    "routingErrors" to emptyList<Any>(),"edges" to emptyList<Any>())))

private fun planningNodes(body: JsonObject): List<JsonObject> = body.field("data").field("planConnection")
    .field("edges").items().mapNotNull { it.field("node") as? JsonObject }

private fun planningFailed(body: JsonObject)=body["errors"].items().isNotEmpty() ||
    body.field("data").field("planConnection").field("edges") !is JsonArray ||
    body.field("data").field("planConnection").field("routingErrors").items().isNotEmpty()

/** Select a network point for a short walking approach, then route both sections at their real speeds.
 * Pedestrian graph cycling remains the documented simplified Bike behaviour.
 */
internal suspend fun walkBikeResponse(seed: JsonObject,input: Request,end: Point,start: Instant,zones: FareZones,
    route: suspend (JsonObject)->JsonObject): JsonObject {
    if(planningFailed(seed)) return seed
    val context=currentCoroutineContext()
    val walk=input.copy(modes=listOf("walk"),zones=emptyMap(),lines=emptyList())
    val guide=planningNodes(seed).firstOrNull { accepts(it,walk,end,zones,null) { context.ensureActive() } }
        ?: return emptyPlanningResponse()
    val points=guide.field("legs").items().flatMap {
        decodePolyline(it.field("legGeometry").field("points").text()!!) { context.ensureActive() }
    }
    val segments=points.zipWithNext().map { (a,b) -> distance(a,b) }
    val length=segments.sum()
    if(!length.isFinite() || length<100) return emptyPlanningResponse()
    val approach=min(100.0,max(10.0,length*0.05))
    var covered=0.0
    var transition: Point?=null
    for(i in segments.indices) {
        context.ensureActive()
        val meters=segments[i]
        if(meters>0 && covered+meters>=approach) {
            val fraction=(approach-covered)/meters
            val a=points[i]; val b=points[i+1]
            transition=Point(a.latitude+(b.latitude-a.latitude)*fraction,a.longitude+(b.longitude-a.longitude)*fraction)
            break
        }
        covered+=meters
    }
    val join=transition ?: return emptyPlanningResponse()
    val walkingBody=route(otpRequest(walk,join,start,null))
    context.ensureActive()
    if(planningFailed(walkingBody)) return walkingBody
    val walking=planningNodes(walkingBody).firstOrNull { accepts(it,walk,join,zones,null) { context.ensureActive() } }
        ?: return emptyPlanningResponse()
    val walkingSeconds=elapsed(walking,start) ?: return emptyPlanningResponse()
    if(walkingSeconds>=input.minutes*60L) return emptyPlanningResponse()
    val bike=input.copy(origin=obj("label" to "Start cycling","point" to join.toJson()),modes=listOf("bike"),zones=emptyMap(),lines=emptyList())
    val bikeDeparture=start.plusSeconds(walkingSeconds)
    val cyclingBody=bikeResponse(route(otpRequest(bike,end,bikeDeparture,null)),bike)
    context.ensureActive()
    if(planningFailed(cyclingBody)) return cyclingBody
    val cycling=planningNodes(cyclingBody).firstOrNull { accepts(it,bike,end,zones,null) { context.ensureActive() } }
        ?: return emptyPlanningResponse()
    val cyclingSeconds=elapsed(cycling,bikeDeparture) ?: return emptyPlanningResponse()
    val legs=walking.field("legs").items()+cycling.field("legs").items()
    val combined=JsonObject(cycling + mapOf(
        "legs" to JsonArray(legs),
        "duration" to JsonPrimitive(walkingSeconds+cyclingSeconds),
        "waitingTime" to JsonPrimitive((walking.field("waitingTime").number() ?: 0.0)+(cycling.field("waitingTime").number() ?: 0.0))))
    if(!accepts(combined,input,end,zones,null) { context.ensureActive() }) return emptyPlanningResponse()
    val data=cyclingBody.field("data")!!.jsonObject
    val plan=data["planConnection"]!!.jsonObject
    return JsonObject(cyclingBody + ("data" to JsonObject(data + ("planConnection" to JsonObject(plan + mapOf(
        "routingErrors" to JsonArray(emptyList()),"edges" to JsonArray(listOf(obj("node" to combined)))))))))
}
