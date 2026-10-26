package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.HistoryScreenKey
import com.example.rnd_transit_mtl.MainScreenKey
import com.example.rnd_transit_mtl.ProfileScreenKey
import com.example.rnd_transit_mtl.TripDetailsScreen
import com.example.rnd_transit_mtl.TripDetailsScreenKey
import com.example.rnd_transit_mtl.TripReviewScreenKey
import com.example.rnd_transit_mtl.model.TripReviewMode
import com.example.rnd_transit_mtl.state.ActiveTripState
import com.example.rnd_transit_mtl.state.TripsState
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

@Preview(
    name = "Shared header — Details",
    showBackground = true,
    widthDp = 390,
    heightDp = 1100
)
@Composable
fun NavigationDetailsHeaderPreview() {
    NavigationPreviewHost(
        MainScreenKey,
        HistoryScreenKey,
        TripDetailsScreenKey(reviewedHistoryPreviewTrip.id),
        tripsState = TripsState(
            completedTrips = historyPreviewTrips
        )
    ) {
        TripDetailsScreen(
            tripId = reviewedHistoryPreviewTrip.id,
            onReturnToHistory = {},
            onReview = {}
        )
    }
}

@Preview(
    name = "Shared header — Resume",
    showBackground = true,
    widthDp = 320,
    heightDp = 400
)
@Composable
fun NavigationResumeHeaderPreview() {
    val activeTrip = reviewedHistoryPreviewTrip.copy(
        id = "preview-active-trip",
        completedAtEpochMillis = null,
        review = null
    )

    NavigationPreviewHost(
        MainScreenKey,
        ProfileScreenKey,
        tripsState = TripsState(
            activeTrip = ActiveTripState(
                trip = activeTrip,
                elapsedMillis = 5_000L
            )
        )
    ) {
        Text(
            text = "The stored trip is paused at 50%. " +
                    "This preview does not run a simulation.",
            color = TransitWhite,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Preview(
    name = "Shared header — Initial review",
    showBackground = true,
    widthDp = 390,
    heightDp = 400
)
@Composable
fun NavigationReviewHeaderPreview() {
    val completedTrip = reviewedHistoryPreviewTrip.copy(review = null)

    NavigationPreviewHost(
        MainScreenKey,
        TripReviewScreenKey(
            tripId = completedTrip.id,
            mode = TripReviewMode.INITIAL
        ),
        tripsState = TripsState(
            completedTrips = listOf(completedTrip)
        )
    ) {
        Text(
            text = "Back skips the initial review and opens History.",
            color = TransitWhite,
            modifier = Modifier.padding(24.dp)
        )
    }
}
