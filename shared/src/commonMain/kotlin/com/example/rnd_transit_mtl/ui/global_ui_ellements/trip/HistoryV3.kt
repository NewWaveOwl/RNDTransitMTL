package com.example.rnd_transit_mtl.ui.global_ui_ellements.trip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — shared section and feature padding are defined in TripV3Shared.kt.
private val HistoryListSpacing = 16.dp
private val HistoryRowTextSpacing = 8.dp
private val HistoryActionSpacing = 12.dp

/**
 * Presentation snapshot of a real saved record; status is supplied by the coordinator.
 *
 * @property id Stable identifier of the saved trip.
 * @property title Display title for the trip.
 * @property summary Formatted duration, distance, and mode summary.
 * @property status Coordinator-supplied completion or review status text.
 * @property completed Whether the trip ended as Completed rather than Cancelled.
 */
data class HistoryRowV3(
    val id: TripId,
    val title: String,
    val summary: String,
    val status: String,
    val completed: Boolean,
)

/**
 * Saved-record list preserving single-tap Actions, double-tap Details, and accessible action buttons.
 *
 * @param rows Ordered trip history rows supplied by the coordinator.
 * @param onOpenTrip Called with the selected trip ID to inspect saved details.
 * @param onPlanNew Called when the user chooses to plan a new adventure.
 * @param onGo Called when the user activates the header GO shortcut.
 * @param modifier Configuration applied to the list container.
 * @param enabled Whether interactive controls accept clicks.
 * @param errorMessage Optional error message rendered above the plan-new action.
 * @param onReviewTrip Optional callback to review or edit the review for a trip ID.
 * @param onRemoveTrip Optional callback to remove a trip ID from history.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryV3(
    rows: List<HistoryRowV3>,
    onOpenTrip: (TripId) -> Unit,
    onPlanNew: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    onReviewTrip: ((TripId) -> Unit)? = null,
    onRemoveTrip: ((TripId) -> Unit)? = null,
) {
    LazyColumn(
        modifier.fillMaxSize().background(TransitWhite),
        verticalArrangement = Arrangement.spacedBy(HistoryListSpacing),
    ) {
        item {
            V3Heading(
                title = "Your adventures",
                subtitle = "Saved records · newest first",
                onGo = onGo,
                enabled = enabled,
            )
        }

        if (rows.isEmpty()) {
            item {
                Text(
                    "Your completed adventures will appear here.",
                    color = TransitComplementary,
                    modifier = Modifier.padding(horizontal = V3SectionPadding),
                )
            }
        }

        items(rows, key = { it.id }) { row ->
            HistoryV3Row(
                row = row,
                enabled = enabled,
                onOpenTrip = onOpenTrip,
                onReviewTrip = onReviewTrip,
                onRemoveTrip = onRemoveTrip,
            )
        }

        item {
            Column(Modifier.padding(horizontal = V3SectionPadding)) {
                V3Error(errorMessage)
                V3Button("Plan a new adventure", onPlanNew, enabled)
            }
        }
    }
}

/**
 * Renders one saved-trip history card with expandable Review and Remove actions.
 *
 * @param row Presentation snapshot for the trip row.
 * @param enabled Whether tap and button interactions are enabled.
 * @param onOpenTrip Called with the trip ID when opening details.
 * @param onReviewTrip Optional callback for reviewing or editing the review.
 * @param onRemoveTrip Optional callback for removing the saved trip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryV3Row(
    row: HistoryRowV3,
    enabled: Boolean,
    onOpenTrip: (TripId) -> Unit,
    onReviewTrip: ((TripId) -> Unit)?,
    onRemoveTrip: ((TripId) -> Unit)?,
) {
    var actionsVisible by rememberSaveable(row.id) { mutableStateOf(false) }

    Column {
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (row.completed) TransitComplementary else TransitMain)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = if (actionsVisible) "Hide actions" else "Show actions",
                    onDoubleClick = { onOpenTrip(row.id) },
                    onClick = { actionsVisible = !actionsVisible },
                )
                .padding(V3FeaturePadding),
            verticalArrangement = Arrangement.spacedBy(HistoryRowTextSpacing),
        ) {
            Text(
                "${row.title} · #${row.id.value.takeLast(3)}",
                color = TransitWhite,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(row.summary, color = TransitWhite)
            Text(
                row.status,
                color = if (row.completed) TransitSelected else TransitWhite,
                fontWeight = FontWeight.Bold,
            )
            Row {
                TextButton(onClick = { onOpenTrip(row.id) }, enabled = enabled) {
                    Text("Details", color = TransitWhite)
                }
                TextButton(onClick = { actionsVisible = !actionsVisible }, enabled = enabled) {
                    Text(if (actionsVisible) "Hide actions" else "Actions", color = TransitWhite)
                }
            }
        }

        AnimatedVisibility(actionsVisible) {
            Row(
                Modifier.padding(horizontal = V3SectionPadding),
                horizontalArrangement = Arrangement.spacedBy(HistoryActionSpacing),
            ) {
                V3Button(
                    label = "Review / edit review",
                    onClick = { onReviewTrip?.invoke(row.id) },
                    enabled = enabled && onReviewTrip != null,
                    modifier = Modifier.weight(1f),
                )
                V3Button(
                    label = "Remove",
                    onClick = { onRemoveTrip?.invoke(row.id) },
                    enabled = enabled && onRemoveTrip != null,
                    secondary = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
