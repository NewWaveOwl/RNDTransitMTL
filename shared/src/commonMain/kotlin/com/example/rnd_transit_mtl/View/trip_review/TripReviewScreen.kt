package com.example.rnd_transit_mtl.View.trip_review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.example.rnd_transit_mtl.data.trips.LocalCloudTrips
import com.example.rnd_transit_mtl.data.trips.tryCloudAction
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.navigation.RegisterReviewBackHandler
import com.example.rnd_transit_mtl.state.review.ReviewDraft
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import kotlinx.coroutines.launch

// Layout settings — delegated to TripReviewContent.kt and TripReviewStatusViews.kt.

/**
 * Coordinates the latest stored record and its ID-associated review draft.
 *
 * Navigation callbacks return true only after their destination exists.
 * [registerBackHandler] connects the shared header to this screen's close action.
 *
 * @param tripId Stable identifier of the completed trip being reviewed.
 * @param mode Whether this is an initial post-trip review or an edit from History/Details.
 * @param isDestinationActive True only while this review destination is the active navigation entry.
 * @param onOpenHistory Navigates to History after an initial review completes or is skipped.
 * @param onReturnToOrigin Returns to the previous destination after an edit review finishes.
 * @param modifier Parent layout constraints.
 * @param registerBackHandler Optional hook connecting the shared top bar back action to discard/close.
 */
@Composable
fun TripReviewScreen(
    tripId: TripId,
    mode: TripReviewMode,
    isDestinationActive: Boolean,
    onOpenHistory: () -> Boolean,
    onReturnToOrigin: () -> Boolean,
    modifier: Modifier = Modifier,
    registerBackHandler: RegisterReviewBackHandler? = null,
) {
    val tripsStore = LocalTripsStore.current
    val cloud = LocalCloudTrips.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focusManager = LocalFocusManager.current

    val snapshot by tripsStore.state
    val trip = snapshot.completedTrips.firstOrNull { it.id == tripId }
    val draft = snapshot.reviewDrafts[tripId]

    val latestDestinationActive by rememberUpdatedState(isDestinationActive)
    val latestOpenHistory by rememberUpdatedState(onOpenHistory)
    val latestReturnToOrigin by rememberUpdatedState(onReturnToOrigin)

    var isAppResumed by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var errorMessage by remember(tripId, mode) { mutableStateOf<String?>(null) }

    // These flags prevent replaying Save/Skip while navigation is pending.
    var exitMessage by rememberSaveable(tripId, mode.name) { mutableStateOf<String?>(null) }
    var exitHandled by rememberSaveable(tripId, mode.name) { mutableStateOf(false) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            isAppResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        isAppResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun isInteractive(): Boolean =
        latestDestinationActive && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    fun canEdit(): Boolean = isInteractive() && exitMessage == null && !saving

    fun finish(message: String) {
        focusManager.clearFocus()
        exitMessage = message
        exitHandled = false
        errorMessage = null
    }

    fun requestExit() {
        if (!isInteractive() || exitMessage == null) return
        val handled = when (mode) {
            TripReviewMode.INITIAL -> latestOpenHistory()
            TripReviewMode.EDIT -> latestReturnToOrigin()
        }
        exitHandled = handled
        errorMessage = if (handled) null
        else "Navigation has not finished. Your trip is preserved. Use Continue to retry."
    }

    fun discardAndExit() {
        if (saving || !isInteractive()) return
        if (exitMessage != null) {
            requestExit()
            return
        }
        when (val result = tripsStore.skipReview(tripId)) {
            TripActionResult.Applied -> finish(
                if (mode == TripReviewMode.INITIAL) "Review skipped. Your completed trip is preserved."
                else "Changes discarded. Your saved review is preserved.",
            )
            is TripActionResult.MissingTrip -> finish("This trip is no longer available.")
            is TripActionResult.InvalidInput -> errorMessage = result.message
            else -> errorMessage = "The review could not be closed."
        }
    }

    fun updateDraft(transform: (ReviewDraft) -> ReviewDraft) {
        if (!canEdit()) return
        val latestDraft = tripsStore.reviewDrafts[tripId] ?: return
        errorMessage = describeReviewActionError(
            result = tripsStore.updateReviewDraft(tripId, transform(latestDraft)),
            fallback = "The review draft could not be updated.",
        )
    }

    fun saveAndExit() {
        if (!canEdit()) return
        if (cloud != null) {
            val result = tripsStore.prepareReview(tripId) { review ->
                saving = true
                scope.launch {
                    try {
                        if (tryCloudAction {
                                // A review needs its parent trip even when completion saving is pending.
                                cloud.flushFinalTrip(tripId)
                                cloud.mutate {
                                    val stored = saveReview(tripId, review)
                                    tripsStore.saveReview(tripId, stored)
                                }
                            }
                        ) {
                            finish("Review saved.")
                        } else {
                            errorMessage = "Could not save your review. Please retry."
                        }
                    } finally {
                        saving = false
                    }
                }
                TripActionResult.Applied
            }
            if (result != TripActionResult.Applied) {
                errorMessage = describeReviewActionError(result, "The review could not be saved.")
            }
            return
        }

        when (val result = tripsStore.saveReview(tripId)) {
            TripActionResult.Applied -> finish("Review saved.")
            is TripActionResult.MissingTrip -> finish("This trip is no longer available.")
            is TripActionResult.InvalidInput -> errorMessage = result.message
            else -> errorMessage = "The review could not be saved."
        }
    }

    val latestClose: () -> Unit by rememberUpdatedState(newValue = { discardAndExit() })
    DisposableEffect(registerBackHandler, isDestinationActive) {
        val unregister = if (isDestinationActive) registerBackHandler?.invoke { latestClose() } else null
        onDispose { unregister?.invoke() }
    }

    LaunchedEffect(tripsStore, tripId, mode, isDestinationActive, trip != null, exitMessage) {
        if (isDestinationActive && trip != null && exitMessage == null) {
            when (val result = tripsStore.beginReview(tripId)) {
                TripActionResult.Applied -> {
                    if (tripsStore.pendingReviewTripId == tripId) {
                        tripsStore.acknowledgeReviewNavigation(tripId)
                    }
                }
                else -> errorMessage = describeReviewActionError(result, "The review could not be opened.")
            }
        }
    }

    LaunchedEffect(tripId, mode, isDestinationActive, isAppResumed, exitMessage, exitHandled) {
        if (isDestinationActive && isAppResumed && exitMessage != null && !exitHandled) {
            requestExit()
        }
    }

    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = isDestinationActive && isAppResumed,
        onBackCompleted = { discardAndExit() },
    )

    val activeAndResumed = isDestinationActive && isAppResumed
    when {
        exitMessage != null -> TripReviewExitView(
            exitMessage = exitMessage.orEmpty(),
            errorMessage = errorMessage,
            mode = mode,
            enabled = activeAndResumed,
            onContinue = { requestExit() },
            modifier = modifier,
        )

        trip == null -> TripReviewUnavailableView(
            enabled = activeAndResumed,
            onReturn = { if (isInteractive()) finish("This trip is no longer available.") },
            modifier = modifier,
        )

        draft == null -> TripReviewOpeningView(
            errorMessage = errorMessage,
            enabled = activeAndResumed,
            onClose = { discardAndExit() },
            modifier = modifier,
        )

        else -> TripReviewContent(
            tripTitle = trip.title,
            mode = mode,
            draft = draft,
            controlsEnabled = canEdit(),
            errorMessage = errorMessage,
            onRatingChange = { question, rating -> updateDraft { it.withRating(question, rating) } },
            onCommentChange = { comment -> updateDraft { it.copy(comment = comment) } },
            onImageUrlChange = { imageUrl -> updateDraft { it.copy(imageUrl = imageUrl) } },
            onStepChange = { step -> updateDraft { it.copy(step = step) } },
            onSave = { saveAndExit() },
            onDiscard = { discardAndExit() },
            modifier = modifier,
        )
    }
}

/** Maps a [TripActionResult] from review draft/open/prepare operations into an optional error message. */
private fun describeReviewActionError(result: TripActionResult, fallback: String): String? = when (result) {
    TripActionResult.Applied -> null
    is TripActionResult.MissingTrip -> "This trip is no longer available."
    is TripActionResult.InvalidInput -> result.message
    else -> fallback
}
