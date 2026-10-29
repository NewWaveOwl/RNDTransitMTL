package com.example.rnd_transit_mtl.ui.global_ui_ellements.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.MockTripMap
import com.example.rnd_transit_mtl.ui.theme.*
import kotlin.math.roundToInt
import com.example.rnd_transit_mtl.ui.global_ui_ellements.ratings.StarRatingRow

// Layout settings — dp dimensions shared by the supplied V3 screens.
private val V3SectionPadding = 16.dp
private val V3FeaturePadding = 24.dp
private val V3ButtonMinimumHeight = 52.dp
private val V3GoWidth = 72.dp
private val V3GoHeight = 44.dp
private val V3GoCornerRadius = 18.dp
private val V3DetailsMaximumPanelHeight = 330.dp

/**
 * These composables fill the area between the existing app header
 * and bottom navigation. They do not add another app scaffold.
 */

@Composable
private fun V3Page(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .background(TransitWhite)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        content = content
    )
}

@Composable
private fun V3Heading(
    title: String,
    subtitle: String?,
    onGo: () -> Unit,
    enabled: Boolean
) {
    Column(
        Modifier.fillMaxWidth().padding(V3SectionPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = TransitComplementary,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .size(V3GoWidth, V3GoHeight)
                    .background(
                        Brush.horizontalGradient(
                            listOf(TransitMain, TransitSelected)
                        ),
                        RoundedCornerShape(V3GoCornerRadius)
                    )
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = "Return to main",
                        onClick = onGo
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "GO",
                    color = TransitWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (subtitle != null) {
            Text(subtitle, color = TransitMain, fontSize = 14.sp)
        }
    }
}

@Composable
private fun V3Button(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    secondary: Boolean = false,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = V3ButtonMinimumHeight),
        shape = RoundedCornerShape(24.dp),
        border = if (secondary) BorderStroke(1.dp, TransitMain) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) TransitWhite else TransitMain,
            contentColor = if (secondary) TransitMain else TransitWhite,
            disabledContainerColor = TransitMain.copy(alpha = 0.08f),
            disabledContentColor = TransitComplementary.copy(alpha = 0.45f)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun V3Note(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        minLines = 2,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TransitMain,
            unfocusedBorderColor = TransitMain,
            focusedTextColor = TransitComplementary,
            unfocusedTextColor = TransitComplementary,
            focusedLabelColor = TransitComplementary,
            unfocusedLabelColor = TransitComplementary
        )
    )
}

@Composable
private fun V3Error(message: String?) {
    if (!message.isNullOrBlank()) {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

private fun distanceText(trip: Trip): String =
    "${(trip.distanceKm * 10).roundToInt() / 10.0} km"

private fun transportText(trip: Trip): String =
    trip.selectedTransports.joinToString(" + ") { it.label }

// ---------- COMPLETE ----------

/** Completed-record presentation. Callbacks delegate Review, checked Skip, and planner navigation to its coordinator. */
@Composable
fun CompleteV3(
    trip: Trip,
    onReview: () -> Unit,
    onSkipToHistory: () -> Unit,
    onPlanAnother: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null
) {
    V3Page(modifier) {
        V3Heading("Adventure complete", null, onGo, enabled)

        Column(
            Modifier
                .fillMaxWidth()
                .background(TransitComplementary)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "You made time for discovery",
                color = TransitWhite,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${trip.title} · Sample trip #${trip.id.takeLast(3)}",
                color = TransitWhite
            )
            Text(
                "${trip.plannedMinutes} min · ${distanceText(trip)}",
                color = TransitWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Trip saved as Completed", color = TransitWhite)
        }

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            V3Error(errorMessage)
            V3Button("Review this trip", onReview, enabled)
            V3Button("Skip review — view history", onSkipToHistory, enabled, true)
            V3Button("Plan another adventure", onPlanAnother, enabled, true)
        }
    }
}

// ---------- REVIEW ----------

/** Overall-review form; a non-null rating is required for Save. Draft and persistence belong to the caller. */
@Composable
fun ReviewV3(
    title: String,
    reference: String,
    rating: Int?,
    comment: String,
    onRatingChange: (Int?) -> Unit,
    onCommentChange: (String) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onFeedback: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    feedbackEnabled: Boolean = true,
    errorMessage: String? = null
) {
    V3Page(modifier) {
        V3Heading(
            "Rate your trip",
            "$title · Trip #$reference",
            onGo,
            enabled
        )

        Column(
            Modifier
                .fillMaxWidth()
                .background(TransitComplementary)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Overall experience",
                color = TransitWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text("How was your adventure?", color = TransitWhite)

            StarRatingRow(rating, { onRatingChange(if (rating == it) null else it) },
                "Overall experience", enabled = enabled, prominent = true)

            Text(
                rating?.let { "$it out of 5" } ?: "No rating selected",
                color = TransitWhite
            )
        }

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            V3Note(comment, onCommentChange, "Optional comment", enabled)

            Text(
                "Choose stars to save a review, or skip. Your trip stays saved.",
                color = TransitComplementary,
                fontSize = 12.sp
            )

            V3Error(errorMessage)
            V3Button("Save review", onSave, enabled && rating != null)
            V3Button("Skip review", onSkip, enabled, true)
            V3Button(
                "Rate a route segment or place",
                onFeedback,
                enabled && feedbackEnabled,
                true
            )
        }
    }
}

// ---------- SEGMENT / PLACE FEEDBACK ----------

enum class FeedbackChoice {
    NONE, LIKED, DISLIKED
}

/** Target-feedback form. Keep its live entry disabled until target-based persistence and navigation are connected. */
@Composable
fun FeedbackV3(
    tripReference: String,
    targetTitle: String,
    targetDescription: String,
    choice: FeedbackChoice,
    note: String,
    onChoiceChange: (FeedbackChoice) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null
) {
    V3Page(modifier) {
        V3Heading(
            "Trip feedback",
            "Trip #$tripReference · Saved route is unchanged",
            onGo,
            enabled
        )

        Column(
            Modifier
                .fillMaxWidth()
                .background(TransitComplementary)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                targetTitle,
                color = TransitWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(targetDescription, color = TransitWhite)
            Text(
                "Current feedback: " + when (choice) {
                    FeedbackChoice.NONE -> "No Rating"
                    FeedbackChoice.LIKED -> "Liked"
                    FeedbackChoice.DISLIKED -> "Disliked"
                },
                color = TransitWhite,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Like" to FeedbackChoice.LIKED,
                    "Dislike" to FeedbackChoice.DISLIKED,
                    "Clear" to FeedbackChoice.NONE
                ).forEach { (label, value) ->
                    val selected = choice == value
                    Surface(
                        color = if (selected) TransitSelected else TransitWhite,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onChoiceChange(value) }
                            )
                    ) {
                        Box(
                            Modifier.heightIn(min = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) TransitComplementary
                                else TransitMain,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            V3Note(note, onNoteChange, "Optional note", enabled)
            V3Error(errorMessage)
            V3Button("Save feedback", onSave, enabled)
            Text(
                "The same controls apply to an attraction on this trip. " +
                    "Clear returns it to No Rating.",
                color = TransitComplementary
            )
        }
    }
}

// ---------- HISTORY ----------

/** Presentation snapshot of a real saved record; status is supplied by the coordinator. */
data class HistoryRowV3(
    val id: String,
    val title: String,
    val summary: String,
    val status: String,
    val completed: Boolean
)

/** Saved-record list preserving single-tap Actions, double-tap Details, and accessible action buttons. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryV3(
    rows: List<HistoryRowV3>,
    onOpenTrip: (String) -> Unit,
    onPlanNew: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    onReviewTrip: ((String) -> Unit)? = null,
    onRemoveTrip: ((String) -> Unit)? = null
) {
    LazyColumn(
        modifier.fillMaxSize().background(TransitWhite),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            V3Heading(
                "Your adventures",
                "Saved records · newest first",
                onGo,
                enabled
            )
        }

        if (rows.isEmpty()) {
            item {
                Text(
                    "Your completed adventures will appear here.",
                    color = TransitComplementary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        items(rows, key = { it.id }) { row ->
            var actionsVisible by rememberSaveable(row.id) { mutableStateOf(false) }
            Column {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (row.completed) TransitComplementary else TransitMain
                    )
                    .combinedClickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = if (actionsVisible) "Hide actions" else "Show actions",
                        onDoubleClick = { onOpenTrip(row.id) },
                        onClick = { actionsVisible = !actionsVisible }
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${row.title} · #${row.id.takeLast(3)}",
                    color = TransitWhite,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(row.summary, color = TransitWhite)
                Text(
                    row.status,
                    color = if (row.completed) TransitSelected else TransitWhite,
                    fontWeight = FontWeight.Bold
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
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    V3Button("Review / edit review", { onReviewTrip?.invoke(row.id) },
                        enabled && onReviewTrip != null, modifier = Modifier.weight(1f))
                    V3Button("Remove", { onRemoveTrip?.invoke(row.id) },
                        enabled && onRemoveTrip != null, secondary = true, modifier = Modifier.weight(1f))
                }
            }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                V3Error(errorMessage)
                V3Button("Plan a new adventure", onPlanNew, enabled)
            }
        }
    }
}

// ---------- SAVED TRIP DETAILS ----------

/** Saved-trip presentation. Repeat creates a fresh proposal; Edit copy restores preferences without overwriting history.
 * @param onReview Opens the saved trip's review editor; null omits the action when navigation is unavailable.
 */
@Composable
fun TripDetailsV3(
    trip: Trip,
    completedLabel: String,
    onRepeat: () -> Unit,
    onEditCopy: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    onReview: (() -> Unit)? = null
) {
    Column(modifier.fillMaxSize().background(TransitWhite)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            MockTripMap(
                start = trip.start,
                destination = trip.destination,
                progress = 1f,
                matchPlannerBackground = true,
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                color = TransitWhite,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Saved trip · #${trip.id.takeLast(3)}",
                    color = TransitMain,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Text(
                "© OpenStreetMap contributors · Sample map",
                color = TransitComplementary,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(TransitWhite)
                    .padding(4.dp)
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = V3DetailsMaximumPanelHeight)
                .background(TransitComplementary)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${trip.title} · #${trip.id.takeLast(3)}",
                color = TransitWhite,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Completed · $completedLabel",
                color = TransitSelected,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${trip.plannedMinutes} min · ${distanceText(trip)} · " +
                    transportText(trip),
                color = TransitWhite,
                fontSize = 18.sp
            )

            val review = trip.review
            TripReviewSummary(review = review, linksEnabled = enabled)

            V3Error(errorMessage)
            if (onReview != null) {
                V3Button(
                    label = if (review == null) "Review trip" else "Edit review",
                    onClick = onReview,
                    enabled = enabled
                )
            }
            V3Button("Repeat as a new trip", onRepeat, enabled)
            V3Button("Edit a copy of these settings", onEditCopy, enabled, true)
            Text(
                "This completed record is never overwritten.",
                color = TransitWhite,
                fontSize = 12.sp
            )
        }
    }
}
