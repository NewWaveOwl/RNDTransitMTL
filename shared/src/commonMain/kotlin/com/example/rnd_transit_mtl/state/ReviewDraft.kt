package com.example.rnd_transit_mtl.state

import kotlinx.serialization.Serializable

/**
 * Unsaved feedback for a completed trip.
 *
 * The store associates this record with the stable trip ID.
 * Changing a draft never immediately changes the saved TripReview.
 *
 * Overall may be null during editing but is required when saving.
 * Optional unselected ratings remain null.
 *
 * step restores the current question after rotation.
 * Animation progress, focus, and keyboard state are not stored.
 * imageUrl may be incomplete while typing; the store validates it on Save.
 */
@Serializable
data class ReviewDraft(
    val overall: Int? = null,
    val quality: Int? = null,
    val interesting: Int? = null,
    val `fun`: Int? = null,
    val comment: String = "",
    val step: ReviewStep = ReviewStep.OVERALL,
    val imageUrl: String = ""
) {
    init {
        require(overall == null || overall in 1..5) {
            "Overall rating must be null or between 1 and 5."
        }
        require(quality == null || quality in 1..5) {
            "Quality rating must be null or between 1 and 5."
        }
        require(interesting == null || interesting in 1..5) {
            "Interesting rating must be null or between 1 and 5."
        }
        require(`fun` == null || `fun` in 1..5) {
            "Fun rating must be null or between 1 and 5."
        }
    }

    fun ratingFor(question: ReviewStep): Int? = when (question) {
        ReviewStep.OVERALL -> overall
        ReviewStep.QUALITY -> quality
        ReviewStep.INTERESTING -> interesting
        ReviewStep.FUN -> `fun`
    }

    /**
     * Returns another validated draft.
     *
     * Null clears an optional selection. It is never converted to zero.
     */
    fun withRating(
        question: ReviewStep,
        rating: Int?
    ): ReviewDraft = when (question) {
        ReviewStep.OVERALL -> copy(overall = rating)
        ReviewStep.QUALITY -> copy(quality = rating)
        ReviewStep.INTERESTING -> copy(interesting = rating)
        ReviewStep.FUN -> copy(`fun` = rating)
    }
}
