package com.example.rnd_transit_mtl.ui

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Keeps visual calculations within their supported range.
 *
 * The state layer normally supplies valid progress. Non-finite input
 * is rendered as zero rather than propagating invalid drawing values.
 */
internal fun boundedTripProgress(progress: Float): Float =
    if (progress.isFinite()) {
        progress.coerceIn(0f, 1f)
    } else {
        0f
    }

/**
 * Rounded display percentage.
 *
 * A value below completion is capped at 99 so the UI never says
 * 100 percent while its supplied progress is still below 1.
 */
internal fun tripProgressPercentage(progress: Float): Int {
    val bounded = boundedTripProgress(progress)
    return if (bounded == 1f) {
        100
    } else {
        (bounded * 100f).roundToInt().coerceIn(0, 99)
    }
}

/**
 * Formats small demo distances to two decimal places using common
 * Kotlin facilities, without a JVM-specific number formatter.
 */
internal fun formatDemoDistanceKm(distanceKm: Double): String {
    require(distanceKm.isFinite() && distanceKm >= 0.0) {
        "Demo distance must be finite and nonnegative."
    }
    require(distanceKm <= Long.MAX_VALUE.toDouble() / 100.0) {
        "Demo distance is too large to format."
    }

    val hundredths = (distanceKm * 100.0).roundToLong()
    val whole = hundredths / 100L
    val fraction = (hundredths % 100L)
        .toString()
        .padStart(2, '0')

    return "$whole.$fraction"
}