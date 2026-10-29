package com.example.rnd_transit_mtl.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.navigation.*
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.ui.theme.TransitMain

/**
 * Places shared navigation around the current destination.
 *
 * @param selectedTab Optional main-view selection supplied by future
 * Compass or Together navigation.
 * @param onOpenCompass Working Compass navigation callback, or null
 * while that feature is unavailable.
 * @param onOpenTogether Working Together navigation callback, or null
 * while that feature is unavailable.
 * @param content Destination content rendered inside scaffold padding.
 */
@Composable
fun MainLayout(
    selectedTab: MainViewTab? = null,
    onOpenCompass: (() -> Unit)? = null,
    onOpenTogether: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val navigator = LocalNavigator.current
    val navigation = LocalTripNavigation.current
    val tripsStore = LocalTripsStore.current
    val tripsState by tripsStore.state
    val currentKey = navigator.current

    val displayedTab = selectedTab ?: when (currentKey) {
        MainScreenKey -> MainViewTab.Map
        is CurrentTripScreenKey -> MainViewTab.Map
        else -> null
    }

    val enabledTabs = buildSet {
        add(MainViewTab.Map)
        if (onOpenCompass != null) add(MainViewTab.Compass)
        if (onOpenTogether != null) add(MainViewTab.Together)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = TransitMain,
        // Top and bottom bars handle their respective system insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SharedTopBar() },
        bottomBar = {
            SharedBottomBar(
                selectedTab = displayedTab,
                enabledTabs = enabledTabs,
                onSelect = { tab ->
                    if (navigator.current == currentKey) {
                        when (tab) {
                            MainViewTab.Map -> {
                                // Keep an already displayed map unchanged.
                                if (displayedTab != MainViewTab.Map) {
                                    val activeTrip =
                                        tripsState.activeTrip?.trip
                                    val completedId =
                                        tripsState.pendingReviewTripId

                                    when {
                                        activeTrip != null ->
                                            navigation.openCurrentTrip(
                                                activeTrip
                                            )

                                        completedId != null ->
                                            navigation.openCompletedTrip(
                                                completedId
                                            )

                                        else ->
                                            navigation.returnToPlanner()
                                    }
                                }
                            }

                            MainViewTab.Compass ->
                                onOpenCompass?.invoke()

                            MainViewTab.Together ->
                                onOpenTogether?.invoke()
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal
                    )
                )
        ) {
            content()
        }
    }
}
