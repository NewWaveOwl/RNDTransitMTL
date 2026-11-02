package com.example.rnd_transit_mtl.layout.previews
import com.example.rnd_transit_mtl.model.ids.TripId

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.navigation.HistoryScreenKey
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.ProfileScreenKey
import com.example.rnd_transit_mtl.View.trip_details.TripDetailsScreen
import com.example.rnd_transit_mtl.navigation.TripDetailsScreenKey
import com.example.rnd_transit_mtl.navigation.TripReviewScreenKey
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.state.trip.ActiveTripState
import com.example.rnd_transit_mtl.state.trip.TripsState
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import com.example.rnd_transit_mtl.View.history.previews.historyPreviewTrips
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip

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
        id = TripId("preview-active-trip"),
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
