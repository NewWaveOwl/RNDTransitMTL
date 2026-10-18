package com.example.rnd_transit_mtl.model

import kotlinx.serialization.Serializable

/**
 * Saved feedback for a completed trip.
 *
 * Overall is required. Unselected optional ratings remain null.
 * An empty comment means no written feedback was supplied.
 * imageUrl is optional saved review feedback, separate from Trip.imageUrl.
 */
@Serializable
data class TripReview(
    val overall: Int,
    val quality: Int? = null,
    val interesting: Int? = null,
    val `fun`: Int? = null,
    val comment: String = "",
    val imageUrl: String = ""
) {
    init {
        require(imageUrl.isEmpty() || Trip.isSupportedImageUrl(imageUrl)) {
            "Review image link must be empty or a valid HTTPS URL."
        }
        require(overall in 1..5) {
            "Overall rating must be between 1 and 5."
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
}
