package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.HistoryScreen
import com.example.rnd_transit_mtl.HistoryScreenKey
import com.example.rnd_transit_mtl.MainScreenKey
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.state.TripsState
import com.example.rnd_transit_mtl.ui.HistoryContent
import com.example.rnd_transit_mtl.ui.TripHistoryCard
import com.example.rnd_transit_mtl.ui.TripHistoryFilter
import com.example.rnd_transit_mtl.ui.historyTrips
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import com.example.rnd_transit_mtl.ui.theme.TransitMain

@Preview(
    name = "History — completed trips",
    showBackground = true,
    widthDp = 402,
    heightDp = 900
)
@Composable
fun HistoryScreenPreview() {
    HistoryPreviewHost()
}

@Preview(
    name = "History — empty",
    showBackground = true,
    widthDp = 320,
    heightDp = 700
)
@Composable
fun HistoryEmptyPreview() {
    HistoryPreviewHost(trips = emptyList())
}

@Preview(
    name = "History — Desktop",
    showBackground = true,
    widthDp = 1000,
    heightDp = 1000
)
@Composable
fun HistoryDesktopPreview() {
    HistoryPreviewHost()
}

@Preview(
    name = "History — Reviewed filter",
    showBackground = true,
    widthDp = 390,
    heightDp = 900
)
@Composable
fun HistoryReviewedFilterPreview() {
    RNDTransitTheme {
        HistoryContent(
            trips = historyTrips(
                historyPreviewTrips,
                TripHistoryFilter.REVIEWED
            ),
            filter = TripHistoryFilter.REVIEWED,
            totalCount = historyPreviewTrips.size,
            onFilterChange = {},
            onOpenDetails = {},
            onReview = {},
            onRemove = {}
        )
    }
}

@Preview(
    name = "History — Not reviewed filter",
    showBackground = true,
    widthDp = 320,
    heightDp = 800
)
@Composable
fun HistoryNotReviewedFilterPreview() {
    RNDTransitTheme {
        HistoryContent(
            trips = historyTrips(
                historyPreviewTrips,
                TripHistoryFilter.NOT_REVIEWED
            ),
            filter = TripHistoryFilter.NOT_REVIEWED,
            totalCount = historyPreviewTrips.size,
            onFilterChange = {},
            onOpenDetails = {},
            onReview = {},
            onRemove = {}
        )
    }
}

@Preview(
    name = "History card — full review and action tray",
    showBackground = true,
    widthDp = 390,
    heightDp = 1050
)
@Composable
fun ExpandedTripHistoryCardPreview() {
    RNDTransitTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TransitMain)
                .padding(16.dp)
        ) {
            TripHistoryCard(
                trip = reviewedHistoryPreviewTrip,
                expanded = true,
                actionsVisible = true,
                onToggleExpanded = {},
                onShowActions = {},
                onHideActions = {},
                onOpenDetails = {},
                onReview = {},
                onRemove = {}
            )
        }
    }
}

@Composable
private fun HistoryPreviewHost(
    trips: List<Trip> = historyPreviewTrips
) {
    NavigationPreviewHost(
        MainScreenKey,
        HistoryScreenKey,
        tripsState = TripsState(completedTrips = trips)
    ) {
        HistoryScreen(
            onOpenDetails = {},
            onReview = {}
        )
    }
}
