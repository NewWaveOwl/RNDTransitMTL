package com.example.rnd_transit_mtl.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.rnd_transit_mtl.data.TripGenerationBounds
import com.example.rnd_transit_mtl.model.TripPoint

/** Actual bundled map_sample PNG dimensions, used if a painter has no intrinsic size. */
internal val bundledMapSize = Size(1510f, 746f)

internal fun validMapSourceSize(intrinsicSize: Size): Size =
    if (intrinsicSize.width.isFinite() && intrinsicSize.height.isFinite() &&
        intrinsicSize.width > 0f && intrinsicSize.height > 0f
    ) intrinsicSize else bundledMapSize

/**
 * Center-aligned uniform Crop matches the planner's Image(ContentScale.Crop).
 * Fit is used for static details, with room around edge-point markers.
 */
internal fun mapImageRect(
    viewport: Size,
    source: Size,
    crop: Boolean,
    markerInset: Float = 0f
): Rect {
    val width = (viewport.width - markerInset * 2f).coerceAtLeast(1f)
    val height = (viewport.height - markerInset * 2f).coerceAtLeast(1f)
    val scale = if (crop) {
        maxOf(viewport.width / source.width, viewport.height / source.height)
    } else {
        minOf(width / source.width, height / source.height)
    }
    val imageWidth = source.width * scale
    val imageHeight = source.height * scale
    return Rect(
        left = (viewport.width - imageWidth) / 2f,
        top = (viewport.height - imageHeight) / 2f,
        right = (viewport.width + imageWidth) / 2f,
        bottom = (viewport.height + imageHeight) / 2f
    )
}

internal fun TripPoint.toMapPosition(rect: Rect): Offset =
    Offset(rect.left + x * rect.width, rect.top + y * rect.height)

/**
 * New endpoints are generated in the visible crop below the title/zoom controls
 * and above the progress footer.
 * CurrentTripContent reserves at most 45% for that footer. Safe generation
 * margins are applied inside this area by MockTripGenerator.
 */
internal fun plannerTripBounds(
    viewport: Size,
    source: Size,
    titleHeight: Float
): TripGenerationBounds {
    val rect = mapImageRect(viewport, source, crop = true)
    val usableBottom = viewport.height * 0.55f
    val usableTop = titleHeight.coerceIn(0f, usableBottom * 0.6f)
    return TripGenerationBounds(
        left = ((0f - rect.left) / rect.width).coerceIn(0f, 1f),
        right = ((viewport.width - rect.left) / rect.width).coerceIn(0f, 1f),
        top = ((usableTop - rect.top) / rect.height).coerceIn(0f, 1f),
        bottom = ((usableBottom - rect.top) / rect.height).coerceIn(0f, 1f)
    )
}
