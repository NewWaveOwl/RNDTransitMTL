package transit

import kotlinx.serialization.json.*
import java.time.Instant

/** Every stop count shares direct leg routing and one combined-budget search deadline. */
internal suspend fun generateRoundTrip(
    input: Request, routes: List<JsonElement>, zones: FareZones,
    route: suspend (JsonObject) -> JsonObject, departure: Instant,
    sample: (suspend (Request) -> List<Point>)?, timeoutMillis: Long,
    requestId: String, diagnostic: (String) -> Unit
): JsonObject {
    val search = LoopSearch(input, routes, zones, route, departure, timeoutMillis, requestId, diagnostic)
    return generateExplorationLoop(input, departure, sample, search)
}
