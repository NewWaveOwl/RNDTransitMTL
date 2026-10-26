package com.example.rnd_transit_mtl.ui

import com.example.rnd_transit_mtl.model.Trip
import kotlin.time.Instant

enum class TripHistoryFilter(val label: String) {
    ALL("All"),
    REVIEWED("Reviewed"),
    NOT_REVIEWED("Not reviewed")
}

/**
 * Derives a presentation list from the shared completed collection.
 *
 * This is not another stored collection. Completion time determines
 * order; creation time and ID provide deterministic tie breaking.
 */
internal fun historyTrips(
    completedTrips: List<Trip>,
    filter: TripHistoryFilter
): List<Trip> =
    completedTrips
        .filter { trip ->
            when (filter) {
                TripHistoryFilter.ALL -> true
                TripHistoryFilter.REVIEWED -> trip.review != null
                TripHistoryFilter.NOT_REVIEWED -> trip.review == null
            }
        }
        .sortedWith(
            compareByDescending<Trip> {
                it.completedAtEpochMillis ?: Long.MIN_VALUE
            }
                .thenByDescending { it.createdAtEpochMillis }
                .thenBy { it.id }
        )

internal fun formatTripTimestampUtc(epochMillis: Long): String =
    Instant.fromEpochMilliseconds(epochMillis)
        .toString()
        .replace('T', ' ')
        .removeSuffix("Z") + " UTC"

internal fun tripTransportSummary(trip: Trip): String =
    trip.selectedTransports.joinToString { it.label }

internal fun tripRouteSummary(trip: Trip): String {
    if (trip.selectedRoutes.isEmpty()) return "No routes selected"

    return trip.selectedRoutes.joinToString { route ->
        val transport = trip.selectedTransports.firstOrNull {
            it.id == route.transportTypeId
        }

        if (transport == null) {
            route.label
        } else {
            "${transport.label}: ${route.label}"
        }
    }
}

/**
 * Supplies the collapsed comment preview.
 *
 * Stops at a newline or a sentence terminator followed by whitespace.
 * Decimal punctuation inside a sentence is retained.
 */
internal fun firstReviewSentence(comment: String): String {
    val text = comment.trim()

    for (index in text.indices) {
        val character = text[index]

        if (character == '\n' || character == '\r') {
            return text.substring(0, index).trim()
        }

        val sentenceTerminator =
            character == '.' || character == '!' || character == '?'

        val endsSentence = index == text.lastIndex ||
            text[index + 1].isWhitespace()

        if (sentenceTerminator && endsSentence) {
            return text.substring(0, index + 1).trim()
        }
    }

    return text
}
