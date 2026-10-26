package com.example.rnd_transit_mtl.model

import kotlinx.serialization.Serializable

/**
 * Determines where the review coordinator returns after finishing.
 *
 * INITIAL: Save, Skip, Close, and Back open History.
 * EDIT: Save returns to the origin; Cancel, Close, and Back discard
 * unsaved changes and return to the origin.
 *
 * The future route stores this mode alongside the stable trip ID.
 */
@Serializable
enum class TripReviewMode {
    INITIAL,
    EDIT
}