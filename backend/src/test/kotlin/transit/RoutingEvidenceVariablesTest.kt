package transit

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/** Protects offline evidence replay against numeric spelling differences while rejecting changed route choices. */
class RoutingEvidenceVariablesTest {
    @Test fun equivalentCoordinateAndSpeedSpellingsMatchTheSameJsonNumbers() {
        assertTrue(RoutingEvidenceVariables.matches("destinationLon", json.parseToJsonElement("-73.6150"), JsonPrimitive(-73.615)))
        assertTrue(RoutingEvidenceVariables.matches("walkSpeed", json.parseToJsonElement("1.33333333330"), JsonPrimitive(1.3333333333)))
    }

    @Test fun alteredCoordinateDoesNotMatchTheRecordedEndpoint() {
        assertFalse(RoutingEvidenceVariables.matches("originLat", JsonPrimitive(45.5088), JsonPrimitive(45.5089)))
        assertFalse(RoutingEvidenceVariables.matches("destinationLon", JsonPrimitive(-73.615), JsonPrimitive(-73.616)))
    }

    @Test fun missingNumericValuesNeverMatch() {
        assertFalse(RoutingEvidenceVariables.matches("originLon", null, null))
        assertFalse(RoutingEvidenceVariables.matches("destinationLat", JsonPrimitive(46.3432), null))
        assertFalse(RoutingEvidenceVariables.matches("walkSpeed", JsonNull, JsonNull))
    }

    @Test fun numericStringsCannotMasqueradeAsJsonNumbers() {
        assertFalse(RoutingEvidenceVariables.matches("destinationLat", JsonPrimitive("46.3432"), JsonPrimitive(46.3432)))
        assertFalse(RoutingEvidenceVariables.matches("originLon", JsonPrimitive(-73.5686), JsonPrimitive("-73.5686")))
    }

    @Test fun nonfiniteNumericValuesNeverMatch() {
        assertFalse(RoutingEvidenceVariables.matches("originLat", JsonPrimitive(Double.NaN), JsonPrimitive(Double.NaN)))
        assertFalse(RoutingEvidenceVariables.matches("walkSpeed", JsonPrimitive(1.3), JsonPrimitive(Double.POSITIVE_INFINITY)))
    }

    @Test fun nonnumericChoicesRetainStructuralEquality() {
        assertTrue(RoutingEvidenceVariables.matches("filters", JsonNull, JsonNull))
        assertTrue(RoutingEvidenceVariables.matches("modes", obj("direct" to listOf("CAR")), obj("direct" to listOf("CAR"))))
        assertFalse(RoutingEvidenceVariables.matches("modes", obj("direct" to listOf("CAR")), obj("direct" to listOf("WALK"))))
        assertFalse(RoutingEvidenceVariables.matches("departure", JsonPrimitive("2026-11-23T13:00:00Z"), JsonPrimitive("2026-11-23T14:00:00Z")))
    }
}
