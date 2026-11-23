package transit

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Compares retained request choices without treating equivalent JSON-number spellings as different endpoints. */
internal object RoutingEvidenceVariables {
    private val numericKeys = setOf("originLat", "originLon", "destinationLat", "destinationLon", "walkSpeed")

    /**
     * Keeps structural equality for modes, timestamps and filters, and finite numeric equality for route coordinates and speed.
     * @param key The fixed GraphQL variable being checked.
     * @param planned The current adapter's variable, or null when absent.
     * @param recorded The retained request's variable, or null when absent.
     * @return True only for matching choices; numeric strings, missing numeric values and nonfinite values never match.
     */
    fun matches(key: String, planned: JsonElement?, recorded: JsonElement?): Boolean {
        if (key !in numericKeys) return planned == recorded
        val plannedNumber = (planned as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
            ?: return false
        val recordedNumber = (recorded as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
            ?: return false
        return plannedNumber == recordedNumber
    }
}
