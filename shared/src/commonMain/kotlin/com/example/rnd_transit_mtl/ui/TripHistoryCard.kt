package com.example.rnd_transit_mtl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_google_demo_delete
import rnd_transit_mtl.shared.generated.resources.ic_google_demo_edit

/**
 * Stateless completed-trip card.
 *
 * The parent owns expansion and action-tray visibility by trip ID.
 * A drag only requests a presentation change; it never removes a trip.
 *
 * Tap a reviewed card to expand/collapse its saved feedback.
 * Swipe left or long-press to reveal the action tray.
 * Swipe right or use Actions again to hide it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripHistoryCard(
    trip: Trip,
    expanded: Boolean,
    actionsVisible: Boolean,
    onToggleExpanded: () -> Unit,
    onShowActions: () -> Unit,
    onHideActions: () -> Unit,
    onOpenDetails: (() -> Unit)?,
    onReview: (() -> Unit)?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 64.dp.toPx() }

    val latestShowActions by rememberUpdatedState(onShowActions)
    val latestHideActions by rememberUpdatedState(onHideActions)

    val review = trip.review

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .pointerInput(trip.id, swipeThreshold, actionsEnabled) {
                if (!actionsEnabled) return@pointerInput

                var horizontalDrag = 0f

                detectHorizontalDragGestures(
                    onDragStart = {
                        horizontalDrag = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        horizontalDrag += amount
                    },
                    onDragEnd = {
                        when {
                            horizontalDrag <= -swipeThreshold ->
                                latestShowActions()

                            horizontalDrag >= swipeThreshold ->
                                latestHideActions()
                        }
                        horizontalDrag = 0f
                    },
                    onDragCancel = {
                        horizontalDrag = 0f
                    }
                )
            }
            .combinedClickable(
                enabled = actionsEnabled,
                onClickLabel = if (review == null) {
                    "Show trip actions"
                } else if (expanded) {
                    "Collapse saved review"
                } else {
                    "Expand saved review"
                },
                onLongClickLabel = "Show edit and remove actions",
                onLongClick = { latestShowActions() },
                onClick = {
                    if (review == null) {
                        latestShowActions()
                    } else {
                        onToggleExpanded()
                    }
                }
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = TransitWhite,
            contentColor = TransitMain
        ),
        border = BorderStroke(
            width = 1.dp,
            color = TransitMain.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = trip.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = if (review == null) {
                        "Not reviewed"
                    } else {
                        "Reviewed"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(
                            color = if (review == null) {
                                TransitHighlight
                            } else {
                                TransitSelected
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            Text(
                text = "${trip.start.label} to ${trip.destination.label}",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "${trip.plannedMinutes} planned minutes · " +
                    "${formatDemoDistanceKm(trip.distanceKm)} km demo distance",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Transport: ${tripTransportSummary(trip)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Routes: ${tripRouteSummary(trip)}",
                style = MaterialTheme.typography.bodyMedium
            )

            val completionText = trip.completedAtEpochMillis
                ?.let(::formatTripTimestampUtc)
                ?: "Completion information unavailable"

            Text(
                text = "Completed: $completionText",
                style = MaterialTheme.typography.bodySmall
            )

            if (review == null) {
                Text(
                    text = "No review saved. You can review this trip later.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                RatingStarsDisplay(
                    label = "Overall experience",
                    rating = review.overall,
                    prominent = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = TransitMain,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                )

                if (!expanded) {
                    Text(
                        text = if (review.comment.isBlank()) {
                            "No written comment."
                        } else {
                            "“${firstReviewSentence(review.comment)}”"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (review.imageUrl.isNotEmpty()) {
                        Text("Photo attached · expand to view",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    TripReviewSummary(
                        review = review,
                        includeOverall = false,
                        linksEnabled = actionsEnabled
                    )
                }

                Text(
                    text = if (expanded) {
                        "Tap to collapse review"
                    } else {
                        "Tap to read the full review"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TransitMain.copy(alpha = 0.75f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { onOpenDetails?.invoke() },
                    enabled = actionsEnabled && onOpenDetails != null,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = TransitMain,
                        disabledContentColor = TransitMain.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Details")
                }

                TextButton(
                    onClick = {
                        if (actionsVisible) {
                            onHideActions()
                        } else {
                            onShowActions()
                        }
                    },
                    enabled = actionsEnabled,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = TransitMain,
                        disabledContentColor = TransitMain.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        if (actionsVisible) {
                            "Hide actions"
                        } else {
                            "Actions"
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = actionsVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TransitMain)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onReview?.invoke() },
                    enabled = actionsEnabled && onReview != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TransitSelected,
                        contentColor = TransitMain,
                        disabledContainerColor = TransitWhite.copy(alpha = 0.15f),
                        disabledContentColor = TransitWhite.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            Res.drawable.ic_google_demo_edit
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )

                    Text(
                        text = if (review == null) {
                            "Review"
                        } else {
                            "Edit review"
                        },
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                Button(
                    onClick = onRemove,
                    enabled = actionsEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TransitHighlight,
                        contentColor = TransitMain,
                        disabledContainerColor = TransitWhite.copy(alpha = 0.15f),
                        disabledContentColor = TransitWhite.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            Res.drawable.ic_google_demo_delete
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )

                    Text(
                        text = "Remove",
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}
