package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.model.TripPoint

/**
 * Visible source-image area for a new trip, in normalized PNG coordinates.
 * This is a generation input only. The returned Trip stores its transformed
 * endpoints once; changing the viewport never regenerates that record.
 */
data class TripGenerationBounds(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    init {
        require(left.isFinite() && right.isFinite() &&
            top.isFinite() && bottom.isFinite())
        require(left >= 0f && right <= 1f && left < right)
        require(top >= 0f && bottom <= 1f && top < bottom)
    }

    fun toSourcePoint(point: TripPoint): TripPoint = point.copy(
        x = left + point.x * (right - left),
        y = top + point.y * (bottom - top)
    )
}
