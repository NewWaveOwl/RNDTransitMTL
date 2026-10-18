package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.TripDetailsScreen
import com.example.rnd_transit_mtl.state.LocalTripsStore
import com.example.rnd_transit_mtl.ui.TripDetailsContent
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

@Preview(
    name = "Trip details — reviewed",
    showBackground = true,
    widthDp = 390,
    heightDp = 1200
)
@Composable
fun TripDetailsReviewedPreview() {
    RNDTransitTheme {
        TripDetailsContent(
            trip = reviewedHistoryPreviewTrip,
            onReturnToHistory = {},
            onReview = {},
            onOpenImageReference = {}
        )
    }
}

@Preview(
    name = "Trip details — Not reviewed",
    showBackground = true,
    widthDp = 320,
    heightDp = 1200
)
@Composable
fun TripDetailsUnreviewedPreview() {
    RNDTransitTheme {
        TripDetailsContent(
            trip = unreviewedHistoryPreviewTrip,
            onReturnToHistory = {},
            onReview = {},
            onOpenImageReference = {}
        )
    }
}

@Preview(
    name = "Trip details — missing ID recovery",
    showBackground = true,
    widthDp = 320,
    heightDp = 650
)
@Composable
fun TripDetailsUnavailablePreview() {
    RNDTransitTheme {
        TripDetailsContent(
            trip = null,
            onReturnToHistory = {},
            onReview = null,
            onOpenImageReference = {}
        )
    }
}

@Preview(
    name = "Trip details — provider and Desktop",
    showBackground = true,
    widthDp = 1000,
    heightDp = 1100
)
@Composable
fun TripDetailsProviderPreview() {
    val store = remember {
        createHistoryPreviewStore()
    }

    RNDTransitTheme {
        CompositionLocalProvider(LocalTripsStore provides store) {
            TripDetailsScreen(
                tripId = reviewedHistoryPreviewTrip.id,
                onReturnToHistory = {},
                onReview = {}
            )
        }
    }
}
