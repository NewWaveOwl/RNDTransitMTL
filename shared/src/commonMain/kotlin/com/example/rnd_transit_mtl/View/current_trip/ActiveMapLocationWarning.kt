package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.runtime.Composable

// Layout settings — warning card padding and styling are owned by TripWarning.kt.

/**
 * Displays location-status warnings when tracking is stale, unavailable, or missing permissions.
 *
 * @param locationState Current [TripLocationState] evaluated by the trip location coordinator.
 * @param staleMessage Optional detailed location or route warning message from the coordinator.
 */
@Composable
internal fun ActiveMapLocationWarning(
    locationState: TripLocationState,
    staleMessage: String?,
) {
    when (locationState) {
        TripLocationState.Stale -> TripWarning(
            title = "Location is not updating",
            detail = staleMessage ?: "No fresh location reading for over 30 seconds. Route remains available.",
        )

        TripLocationState.Missing -> TripWarning(
            title = "Location unavailable",
            detail = "Your saved route remains available.",
        )

        TripLocationState.Sample, TripLocationState.Live -> {
            if (staleMessage != null) {
                TripWarning(
                    title = if (locationState == TripLocationState.Sample) "Location not updating" else "Check your route",
                    detail = staleMessage,
                )
            }
        }

        TripLocationState.PermissionDenied -> TripWarning(
            title = "Location permission needed",
            detail = "Tap recenter to allow location access.",
        )

        TripLocationState.NotTracking,
        TripLocationState.Waiting,
        TripLocationState.LastKnown,
        TripLocationState.LowAccuracy,
        TripLocationState.MatchedLowAccuracy,
        -> Unit
    }
}
