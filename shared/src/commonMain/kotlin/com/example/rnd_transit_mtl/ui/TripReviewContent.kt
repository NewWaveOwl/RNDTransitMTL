package com.example.rnd_transit_mtl.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.TripReviewMode
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.state.ReviewDraft
import com.example.rnd_transit_mtl.state.ReviewStep
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_close

/**
 * Stateless sequential review form.
 *
 * The draft supplies both values and the current question.
 * Only transient presentation animation and scrolling live here.
 *
 * Next rolls the new panel upward; Previous reverses that direction.
 * Outgoing panels cannot receive edits during their transition.
 */
@Composable
fun TripReviewContent(
    tripTitle: String,
    mode: TripReviewMode,
    draft: ReviewDraft,
    controlsEnabled: Boolean,
    errorMessage: String?,
    onRatingChange: (ReviewStep, Int?) -> Unit,
    onCommentChange: (String) -> Unit,
    onImageUrlChange: (String) -> Unit,
    onStepChange: (ReviewStep) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(TransitWhite)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Rate your trip",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TransitMain
                    )

                    Text(
                        text = tripTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TransitMain
                    )
                }

                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onDiscard()
                    },
                    enabled = controlsEnabled,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = if (
                            mode == TripReviewMode.INITIAL
                        ) {
                            "Close and skip review"
                        } else {
                            "Close and discard review changes"
                        },
                        tint = TransitMain
                    )
                }
            }

            Text(
                text = "Question ${draft.step.ordinal + 1} " +
                        "of ${ReviewStep.entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = TransitMain
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReviewStep.entries.forEach { question ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(
                                color = if (
                                    question.ordinal <= draft.step.ordinal
                                ) {
                                    TransitSelected
                                } else {
                                    TransitMain.copy(alpha = 0.15f)
                                },
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }

            AnimatedContent(
                targetState = draft.step,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    val direction = if (
                        targetState.ordinal > initialState.ordinal
                    ) {
                        1
                    } else {
                        -1
                    }

                    (
                            slideInVertically(
                                animationSpec = tween(300),
                                initialOffsetY = { it * direction }
                            ) + fadeIn(animationSpec = tween(200))
                            ) togetherWith (
                            slideOutVertically(
                                animationSpec = tween(300),
                                targetOffsetY = { -it * direction }
                            ) + fadeOut(animationSpec = tween(150))
                            )
                },
                label = "Review question rolling slide"
            ) { question ->
                val panelEnabled =
                    controlsEnabled && question == draft.step

                ReviewQuestionCard(
                    step = question,
                    rating = draft.ratingFor(question),
                    comment = draft.comment,
                    imageUrl = draft.imageUrl,
                    enabled = panelEnabled,
                    onRatingChange = { rating ->
                        onRatingChange(question, rating)
                    },
                    onClearRating = {
                        onRatingChange(question, null)
                    },
                    onCommentChange = onCommentChange,
                    onImageUrlChange = onImageUrlChange
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }

            val next = draft.step.nextOrNull()
            val imageUrl = draft.imageUrl.trim()
            val validImageUrl = imageUrl.isEmpty() || Trip.isSupportedImageUrl(imageUrl)
            val continueHint = when {
                draft.overall == null -> "Select Overall experience stars to continue."
                !validImageUrl -> "Correct the image link or leave it empty to continue."
                else -> null
            }

            if (controlsEnabled && continueHint != null) {
                Text(
                    text = continueHint,
                    color = TransitMain,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }

            Button(
                onClick = {
                    focusManager.clearFocus()

                    if (next == null) {
                        onSave()
                    } else {
                        onStepChange(next)
                    }
                },
                enabled = controlsEnabled && draft.overall != null && validImageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    if (next == null) {
                        "Save review"
                    } else {
                        "Continue"
                    }
                )
            }

            val previous = draft.step.previousOrNull()

            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    previous?.let(onStepChange)
                },
                enabled = controlsEnabled && previous != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Previous question")
            }

            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    onDiscard()
                },
                enabled = controlsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    if (mode == TripReviewMode.INITIAL) {
                        "Skip review"
                    } else {
                        "Cancel changes"
                    }
                )
            }
        }
    }
}
