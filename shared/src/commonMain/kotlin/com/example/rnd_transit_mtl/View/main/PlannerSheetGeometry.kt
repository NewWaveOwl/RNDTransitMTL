package com.example.rnd_transit_mtl.View.main

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.example.rnd_transit_mtl.View.main.components.PlannerDragStripHeight

// Layout settings — Figma reference coordinates (800 px reference height) and short-window cap.
// Drag-strip height is defined in View/main/components/PlannerSheetDragHandle.kt.
private const val PlannerReferenceViewportHeight = 800f
private const val PlannerMainSheetTop = 400f
private const val PlannerRoutesSheetTop = 256f
private const val PlannerPreferencesSheetTop = 100f
private const val PlannerShortWindowTopFraction = 0.3f
private const val PlannerGoCollapsedGap = 10f
private const val PlannerGoMainGap = 24f
private const val PlannerGoRoutesGap = 7f
private const val PlannerGoPreferencesGap = 12f

// Animation settings — larger millisecond values make snapping and handle taps slower.
internal const val PLANNER_SHEET_ANIMATION_DURATION_MILLIS = 300

/** The three Figma layouts plus a bottom stop that exposes the full map. */
internal enum class PlannerSheetPosition { Main, Routes, Preferences, Collapsed }

/**
 * Pixel positions of the four planner sheet stops and interpolated GO spacing.
 *
 * @property collapsed Bottom stop offset in pixels leaving the drag handle visible.
 * @property main Default planner stop offset in pixels.
 * @property routes Intermediate transport/routes stop offset in pixels.
 * @property preferences Top expanded stop offset in pixels.
 * @property scalePx Pixels per Figma reference unit, including density and responsive width scaling.
 */
internal data class PlannerSheetGeometry(
    val collapsed: Float,
    val main: Float,
    val routes: Float,
    val preferences: Float,
    val scalePx: Float,
) {
    /**
     * Interpolates the Figma spacing so GO follows the handle continuously.
     *
     * @param offset Current sheet-handle offset in pixels.
     * @return Vertical gap in pixels between GO and the sheet handle.
     */
    fun goGap(offset: Float): Float = if (offset >= main) {
        val fraction = ((collapsed - offset) / (collapsed - main).coerceAtLeast(1f)).coerceIn(0f, 1f)
        (PlannerGoCollapsedGap + (PlannerGoMainGap - PlannerGoCollapsedGap) * fraction) * scalePx
    } else if (offset >= routes) {
        val fraction = ((offset - routes) / (main - routes)).coerceIn(0f, 1f)
        (PlannerGoRoutesGap + (PlannerGoMainGap - PlannerGoRoutesGap) * fraction) * scalePx
    } else {
        val fraction = ((offset - preferences) / (routes - preferences)).coerceIn(0f, 1f)
        (PlannerGoPreferencesGap + (PlannerGoRoutesGap - PlannerGoPreferencesGap) * fraction) * scalePx
    }
}

/**
 * Fits Figma handle positions below the existing header, preserving usable space on short windows.
 *
 * @param heightPx Available viewport height in pixels below the shared header.
 * @param scalePx Pixels per Figma reference unit, including density and responsive width scaling.
 * @return Calculated sheet stop anchors and scale factor.
 */
internal fun plannerSheetGeometry(heightPx: Float, scalePx: Float): PlannerSheetGeometry {
    val top = (PlannerPreferencesSheetTop * scalePx).coerceAtMost(heightPx * PlannerShortWindowTopFraction)
    // Leave the handle visible at the bottom so the preferences can be reopened.
    val remaining = (heightPx - top).coerceAtLeast(1f)
    val main = top + remaining * (
        (PlannerMainSheetTop - PlannerPreferencesSheetTop) /
            (PlannerReferenceViewportHeight - PlannerPreferencesSheetTop)
    )
    return PlannerSheetGeometry(
        collapsed = (heightPx - PlannerDragStripHeight.value * scalePx).coerceAtLeast(main),
        main = main,
        routes = top + remaining * (
            (PlannerRoutesSheetTop - PlannerPreferencesSheetTop) /
                (PlannerReferenceViewportHeight - PlannerPreferencesSheetTop)
        ),
        preferences = top,
        scalePx = scalePx,
    )
}

/**
 * Coordinates vertical scrolling inside the preferences list with anchored sheet dragging.
 *
 * @param sheet Anchored draggable state owning the sheet's vertical offset and stops.
 * @param list Scroll state of the inner preferences [LazyListState].
 * @param enabled Whether user drag and fling gestures should move or settle the sheet.
 * @return [NestedScrollConnection] handing overflow scroll to the anchored sheet.
 */
@Composable
internal fun rememberPlannerSheetNestedScroll(
    sheet: AnchoredDraggableState<PlannerSheetPosition>,
    list: LazyListState,
    enabled: Boolean,
): NestedScrollConnection {
    val currentEnabled by rememberUpdatedState(enabled)
    return remember(sheet, list) {
        object : NestedScrollConnection {
            private var movedSheet = false

            private fun consume(delta: Float): Offset {
                val consumed = sheet.dispatchRawDelta(delta)
                movedSheet = movedSheet || consumed != 0f
                return Offset(0f, consumed)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!currentEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
                return if (available.y < 0f || !list.canScrollBackward) consume(available.y) else Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (currentEnabled && source == NestedScrollSource.UserInput) consume(available.y) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!currentEnabled || !movedSheet) return Velocity.Zero
                movedSheet = false
                sheet.settle(tween(PLANNER_SHEET_ANIMATION_DURATION_MILLIS))
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!currentEnabled || !movedSheet) return Velocity.Zero
                movedSheet = false
                sheet.settle(tween(PLANNER_SHEET_ANIMATION_DURATION_MILLIS))
                return available
            }
        }
    }
}
