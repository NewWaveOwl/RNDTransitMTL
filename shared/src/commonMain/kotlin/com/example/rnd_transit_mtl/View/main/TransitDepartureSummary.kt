package com.example.rnd_transit_mtl.View.main

import com.example.rnd_transit_mtl.model.trip.RouteLeg
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.time.Instant

/** Stop departure countdown and Montreal clock time, using fresh estimates or the schedule. */
internal fun transitDepartureSummary(leg: RouteLeg, routeLabel: String?, nowMillis: Long): String {
    val mode = when (leg.transportId.value) {
        "bus" -> "Bus"
        "metro" -> "Metro"
        "rem" -> "REM"
        "train" -> "Train"
        else -> leg.transportId.value.replaceFirstChar { it.uppercase() }
    }
    val name = leg.navigation?.routeShortName?.takeIf { it.isNotBlank() }
        ?: routeLabel?.takeIf { it.isNotBlank() }
        ?: ""
    val line = if (name.isBlank()) mode else "$mode $name"
    val timing = leg.transitTiming ?: return "$line - time unavailable"
    val estimate = timing.estimatedDeparture.takeIf { timing.realtimeApplied && timing.predictionsFreshAt(nowMillis) }
    val departure = listOfNotNull(estimate, timing.scheduledDeparture).firstNotNullOfOrNull {
        runCatching { Instant.parse(it) }.getOrNull()
    } ?: return "$line - time unavailable"
    val local = departure.toLocalDateTime(TimeZone.of("America/Toronto"))
    val clock = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    val remaining = departure.toEpochMilliseconds() - nowMillis
    val countdown = when {
        remaining > 0 -> "in ${ceil(remaining / 60_000.0).toLong()} min"
        remaining == 0L -> "now"
        else -> "departed"
    }
    return "$line $countdown ($clock)"
}
