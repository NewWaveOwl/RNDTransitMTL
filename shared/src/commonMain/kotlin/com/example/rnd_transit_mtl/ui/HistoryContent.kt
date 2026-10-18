package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Presents an already sorted/filtered list.
 *
 * The only local state is card expansion and tray visibility.
 * Stable item keys associate those states with trip IDs.
 * Collection operations are delegated to the screen.
 */
@Composable
fun HistoryContent(
    trips: List<Trip>,
    filter: TripHistoryFilter,
    totalCount: Int,
    onFilterChange: (TripHistoryFilter) -> Unit,
    onOpenDetails: ((String) -> Unit)?,
    onReview: ((String) -> Unit)?,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    errorMessage: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TransitMain),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$totalCount completed " +
                            if (totalCount == 1) "trip" else "trips",
                        style = MaterialTheme.typography.titleMedium,
                        color = TransitWhite
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripHistoryFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filter == option,
                                onClick = { onFilterChange(option) },
                                enabled = actionsEnabled,
                                label = { Text(option.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = TransitMain,
                                    labelColor = TransitWhite,
                                    selectedContainerColor = TransitSelected,
                                    selectedLabelColor = TransitMain
                                ),
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TransitWhite
                    )
                }
            }

            if (trips.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = TransitWhite,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = when {
                                totalCount == 0 -> "No completed trips yet"
                                filter == TripHistoryFilter.REVIEWED ->
                                    "No reviewed trips"
                                filter == TripHistoryFilter.NOT_REVIEWED ->
                                    "Every completed trip is reviewed"
                                else -> "No trips to display"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = TransitMain
                        )

                        Text(
                            text = if (totalCount == 0) {
                                "Finish a trip to add it to History."
                            } else {
                                "Choose another filter to see your trips."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = TransitMain
                        )

                        if (filter != TripHistoryFilter.ALL) {
                            TextButton(
                                onClick = {
                                    onFilterChange(TripHistoryFilter.ALL)
                                },
                                enabled = actionsEnabled
                            ) {
                                Text("Show all trips")
                            }
                        }
                    }
                }
            }

            items(
                items = trips,
                key = { trip -> trip.id }
            ) { trip ->
                var expanded by rememberSaveable(trip.id) {
                    mutableStateOf(false)
                }
                var actionsVisible by rememberSaveable(trip.id) {
                    mutableStateOf(false)
                }

                TripHistoryCard(
                    trip = trip,
                    expanded = expanded,
                    actionsVisible = actionsVisible,
                    onToggleExpanded = { expanded = !expanded },
                    onShowActions = { actionsVisible = true },
                    onHideActions = { actionsVisible = false },
                    onOpenDetails = onOpenDetails?.let { callback ->
                        { callback(trip.id) }
                    },
                    onReview = onReview?.let { callback ->
                        { callback(trip.id) }
                    },
                    onRemove = { onRemove(trip.id) },
                    actionsEnabled = actionsEnabled
                )
            }
        }
    }
}
