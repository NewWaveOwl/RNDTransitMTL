package com.example.rnd_transit_mtl.state

import kotlinx.serialization.Serializable

/**
 * Restricted questions in the review sequence.
 *
 * Stored by name through serialization, independently of animation state.
 */
@Serializable
enum class ReviewStep {
    OVERALL,
    QUALITY,
    INTERESTING,
    FUN;

    fun previousOrNull(): ReviewStep? =
        entries.getOrNull(ordinal - 1)

    fun nextOrNull(): ReviewStep? =
        entries.getOrNull(ordinal + 1)
}