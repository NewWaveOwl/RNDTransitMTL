package com.example.rnd_transit_mtl.View.trip_review

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
import com.example.rnd_transit_mtl.View.trip_review.components.ReviewQuestionCard
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.state.review.ReviewDraft
import com.example.rnd_transit_mtl.state.review.ReviewStep
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_close

// Layout settings — dp width limit, spacing, step bar, and button heights for the review form.
private val ReviewFormMaximumWidth = 640.dp
private val ReviewFormPadding = 16.dp
private val ReviewFormSpacing = 20.dp
private val ReviewCloseButtonSize = 48.dp
private val ReviewStepBarHeight = 6.dp
private val ReviewStepBarSpacing = 8.dp
private val ReviewPrimaryButtonMinHeight = 52.dp
private val ReviewSecondaryButtonMinHeight = 48.dp

// Animation settings — milliseconds for vertical rolling transitions between review questions.
private const val REVIEW_STEP_SLIDE_DURATION_MILLIS = 300
private const val REVIEW_STEP_FADE_IN_DURATION_MILLIS = 200
private const val REVIEW_STEP_FADE_OUT_DURATION_MILLIS = 150

/**
 * Stateless sequential review form.
 * The draft supplies both values and the current question; only transient animation and scrolling live here.
 *
 * @param tripTitle Display title of the trip being reviewed.
 * @param mode Initial post-trip review versus editing an existing review.
 * @param draft Current in-progress review ratings, comment, image URL, and active step.
 * @param controlsEnabled Whether form edits and navigation buttons accept input.
 * @param errorMessage Optional persistence or validation error message.
 * @param onRatingChange Called when a star rating is selected or cleared for a step.
 * @param onCommentChange Called when the user edits the review comment.
 * @param onImageUrlChange Called when the user edits the optional review image link.
 * @param onStepChange Called when moving to the previous or next question.
 * @param onSave Called when saving the completed review on the final step.
 * @param onDiscard Called when skipping the review or cancelling edits.
 * @param modifier Parent layout constraints.
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
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(TransitWhite)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = ReviewFormMaximumWidth)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(ReviewFormPadding),
            verticalArrangement = Arrangement.spacedBy(ReviewFormSpacing),
        ) {
            ReviewHeaderRow(
                tripTitle = tripTitle,
                mode = mode,
                controlsEnabled = controlsEnabled,
                onClose = {
                    focusManager.clearFocus()
                    onDiscard()
                },
            )

            ReviewStepProgressBar(currentStep = draft.step)

            AnimatedContent(
                targetState = draft.step,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (
                        slideInVertically(
                            animationSpec = tween(REVIEW_STEP_SLIDE_DURATION_MILLIS),
                            initialOffsetY = { it * direction },
                        ) + fadeIn(animationSpec = tween(REVIEW_STEP_FADE_IN_DURATION_MILLIS))
                    ) togetherWith (
                        slideOutVertically(
                            animationSpec = tween(REVIEW_STEP_SLIDE_DURATION_MILLIS),
                            targetOffsetY = { -it * direction },
                        ) + fadeOut(animationSpec = tween(REVIEW_STEP_FADE_OUT_DURATION_MILLIS))
                    )
                },
                label = "Review question rolling slide",
            ) { question ->
                ReviewQuestionCard(
                    step = question,
                    rating = draft.ratingFor(question),
                    comment = draft.comment,
                    imageUrl = draft.imageUrl,
                    enabled = controlsEnabled && question == draft.step,
                    onRatingChange = { rating -> onRatingChange(question, rating) },
                    onClearRating = { onRatingChange(question, null) },
                    onCommentChange = onCommentChange,
                    onImageUrlChange = onImageUrlChange,
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            ReviewFooterActions(
                draft = draft,
                mode = mode,
                controlsEnabled = controlsEnabled,
                onStepChange = { step ->
                    focusManager.clearFocus()
                    onStepChange(step)
                },
                onSave = {
                    focusManager.clearFocus()
                    onSave()
                },
                onDiscard = {
                    focusManager.clearFocus()
                    onDiscard()
                },
            )
        }
    }
}

/** Header displaying the screen title, trip name, and close/discard icon button. */
@Composable
private fun ReviewHeaderRow(
    tripTitle: String,
    mode: TripReviewMode,
    controlsEnabled: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "Rate your trip", style = MaterialTheme.typography.headlineSmall, color = TransitMain)
            Text(text = tripTitle, style = MaterialTheme.typography.bodyLarge, color = TransitMain)
        }

        IconButton(
            onClick = onClose,
            enabled = controlsEnabled,
            modifier = Modifier.size(ReviewCloseButtonSize),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = if (mode == TripReviewMode.INITIAL) {
                    "Close and skip review"
                } else {
                    "Close and discard review changes"
                },
                tint = TransitMain,
            )
        }
    }
}

/** Displays the question counter label and segmented step progress indicators. */
@Composable
private fun ReviewStepProgressBar(currentStep: ReviewStep) {
    Column(verticalArrangement = Arrangement.spacedBy(ReviewFormSpacing)) {
        Text(
            text = "Question ${currentStep.ordinal + 1} of ${ReviewStep.entries.size}",
            style = MaterialTheme.typography.labelLarge,
            color = TransitMain,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ReviewStepBarSpacing),
        ) {
            ReviewStep.entries.forEach { question ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(ReviewStepBarHeight)
                        .background(
                            color = if (question.ordinal <= currentStep.ordinal) {
                                TransitSelected
                            } else {
                                TransitMain.copy(alpha = 0.15f)
                            },
                            shape = RoundedCornerShape(3.dp),
                        ),
                )
            }
        }
    }
}

/** Validation hint and Continue/Save, Previous, and Skip/Cancel buttons for the review form. */
@Composable
private fun ReviewFooterActions(
    draft: ReviewDraft,
    mode: TripReviewMode,
    controlsEnabled: Boolean,
    onStepChange: (ReviewStep) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val next = draft.step.nextOrNull()
    val previous = draft.step.previousOrNull()
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
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }

    Button(
        onClick = { if (next == null) onSave() else onStepChange(next) },
        enabled = controlsEnabled && draft.overall != null && validImageUrl,
        modifier = Modifier.fillMaxWidth().heightIn(min = ReviewPrimaryButtonMinHeight),
    ) {
        Text(if (next == null) "Save review" else "Continue")
    }

    TextButton(
        onClick = { previous?.let(onStepChange) },
        enabled = controlsEnabled && previous != null,
        modifier = Modifier.fillMaxWidth().heightIn(min = ReviewSecondaryButtonMinHeight),
    ) {
        Text("Previous question")
    }

    TextButton(
        onClick = onDiscard,
        enabled = controlsEnabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = ReviewSecondaryButtonMinHeight),
    ) {
        Text(if (mode == TripReviewMode.INITIAL) "Skip review" else "Cancel changes")
    }
}
