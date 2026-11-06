package com.example.rnd_transit_mtl.View.history

import com.example.rnd_transit_mtl.model.trip.Trip
import kotlin.time.Instant
import com.example.rnd_transit_mtl.model.trip.TripStatus

enum class TripHistoryFilter(val label: String) {
    ALL("All"),
    REVIEWED("Reviewed"),
    NOT_REVIEWED("Not reviewed"),
    CANCELLED("Cancelled")
}

/**
 * Derives a presentation list from the shared completed and kept cancelled trips.
 *
 * This is not another stored collection. The time a trip ended (completion
 * or cancellation) determines order; creation time and ID provide
 * deterministic tie breaking. Review filters only include completed trips,
 * because cancelled trips cannot be reviewed.
 */
internal fun historyTrips(
    trips: List<Trip>,
    filter: TripHistoryFilter
): List<Trip> =
    trips
        .filter { trip ->
            val completed = trip.status == TripStatus.Completed
            when (filter) {
                TripHistoryFilter.ALL -> true
                TripHistoryFilter.REVIEWED -> completed && trip.review != null
                TripHistoryFilter.NOT_REVIEWED -> completed && trip.review == null
                TripHistoryFilter.CANCELLED -> trip.status == TripStatus.Cancelled
            }
        }
        .sortedWith(
            compareByDescending<Trip> {
                it.completedAt ?: it.cancelledAt ?: Long.MIN_VALUE
            }
                .thenByDescending { it.createdAt }
                .thenBy { it.id.value }
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
