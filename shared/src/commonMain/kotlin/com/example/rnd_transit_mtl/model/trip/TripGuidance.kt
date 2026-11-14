package com.example.rnd_transit_mtl.model.trip

import kotlin.math.roundToInt

/** Instructions for the same geometry-based playback position used by the trip map. */
data class TripGuidance(
    val legIndex: Int,
    val section: String,
    val currentAction: String,
    val nextAction: String,
    val nextIsLeftTurn: Boolean = false
)

/** Human-readable mode and line, using saved provider labels rather than a catalogue lookup.
 * @param legIndex Index in travel order.
 */
fun Trip.legLabel(legIndex: Int): String {
    val leg = legs[legIndex]
    val mode = selectedTransports.first { it.id == leg.transportId }.label
    val route = leg.navigation?.routeShortName?.takeIf { it.isNotBlank() }
        ?: selectedRoutes.firstOrNull { it.id == leg.routeId }?.label
    return if (route == null) mode else "$mode $route"
}

/** Derives current and upcoming actions without a second timer or location source.
 * Street step distances are scaled to the drawn leg length, matching the simulated pointer.
 * Missing instructions on older trips use only their saved mode and route labels.
 * @param progress Whole-route distance fraction; non-finite values mean the start.
 */
fun Trip.guidanceAt(progress: Float): TripGuidance {
    val fraction = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val lengths = legs.map { leg -> leg.geometry.zipWithNext().sumOf { (a, b) -> a.distanceMetersTo(b) } }
    var remaining = lengths.sum() * fraction
    var index = legs.lastIndex
    for (candidate in legs.indices) {
        if (remaining < lengths[candidate]) {
            index = candidate
            break
        }
        remaining = (remaining - lengths[candidate]).coerceAtLeast(0.0)
    }
    val leg = legs[index]
    val label = legLabel(index)
    val section = "Section ${index + 1} of ${legs.size}: $label"
    if (fraction == 1f) return TripGuidance(index, section, "Destination reached", "Finish and review your trip")
    val legFraction = if (lengths[index] > 0) (remaining / lengths[index]).coerceIn(0.0, 1.0) else 0.0
    val nextLeg = legs.getOrNull(index + 1)
    val nextAction = if (nextLeg == null) {
        "Arrive at ${destination.label}"
    } else if (nextLeg.routeId != null) {
        val stop = nextLeg.navigation?.fromName?.takeIf { it.isNotBlank() }?.let { " at $it" }.orEmpty()
        "Wait for ${legLabel(index + 1)}$stop, then board" + nextLeg.navigation.towards()
    } else {
        "Continue with ${legLabel(index + 1)}" + nextLeg.navigation?.steps?.firstOrNull()?.streetSuffix().orEmpty()
    }
    if (leg.routeId != null) {
        val stop = leg.navigation?.toName?.takeIf { it.isNotBlank() }?.let { " at $it" }.orEmpty()
        return TripGuidance(index, section, "Ride $label" + leg.navigation.towards(), "Get off$stop. $nextAction")
    }
    val steps = leg.navigation?.steps.orEmpty()
    val stepLength = steps.sumOf { it.distanceMeters }
    if (steps.isEmpty() || stepLength <= 0.0) {
        val action = if (leg.isRouted) "Continue with $label" else "Follow the preview section: $label"
        return TripGuidance(index, section, action, nextAction)
    }
    var stepRemaining = stepLength * legFraction
    var stepIndex = steps.lastIndex
    for (candidate in steps.indices) {
        if (stepRemaining < steps[candidate].distanceMeters) {
            stepIndex = candidate
            break
        }
        stepRemaining = (stepRemaining - steps[candidate].distanceMeters).coerceAtLeast(0.0)
    }
    val step = steps[stepIndex]
    val upcoming = steps.getOrNull(stepIndex + 1)
    val action = if (stepRemaining < 15 && step.relativeDirection != "DEPART") {
        step.manoeuvre()
    } else {
        "Continue with $label" + step.streetSuffix()
    }
    val next = upcoming?.let {
        "In ${(step.distanceMeters - stepRemaining).roundToInt().coerceAtLeast(1)} m, ${it.manoeuvre().replaceFirstChar { char -> char.lowercase() }}"
    } ?: nextAction
    return TripGuidance(index, section, action, next, upcoming?.relativeDirection in leftTurns)
}

private val leftTurns = setOf("LEFT", "HARD_LEFT", "SLIGHTLY_LEFT")

/** Destination signage saved on a transit leg. */
private fun LegNavigation?.towards(): String =
    this?.headsign?.takeIf { it.isNotBlank() }?.let { " towards $it" }.orEmpty()

/** Generated street names are deliberately omitted from spoken-style guidance. */
private fun NavigationStep.streetSuffix(): String =
    streetName?.takeIf { !bogusName && it.isNotBlank() }?.let { " on $it" }.orEmpty()

/** Formats the provider's manoeuvre without guessing an unavailable street or turn. */
private fun NavigationStep.manoeuvre(): String {
    val street = streetName?.takeIf { !bogusName && it.isNotBlank() }
    val turn = when (relativeDirection) {
        "LEFT" -> "Turn left"
        "HARD_LEFT" -> "Turn sharply left"
        "SLIGHTLY_LEFT" -> "Bear left"
        "RIGHT" -> "Turn right"
        "HARD_RIGHT" -> "Turn sharply right"
        "SLIGHTLY_RIGHT" -> "Bear right"
        "UTURN_LEFT", "UTURN_RIGHT" -> "Make a U-turn"
        "CIRCLE_CLOCKWISE", "CIRCLE_COUNTERCLOCKWISE" -> "Take the roundabout" + exit?.let { " exit $it" }.orEmpty()
        "ELEVATOR" -> "Take the elevator"
        else -> "Continue"
    }
    return turn + street?.let { if (turn == "Continue") " on $it" else " onto $it" }.orEmpty()
}
