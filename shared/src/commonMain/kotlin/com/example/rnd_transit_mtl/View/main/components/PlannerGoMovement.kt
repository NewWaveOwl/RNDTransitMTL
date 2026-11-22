package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Layout settings — keyboard and accessibility movement in dp.
private val GoMovementStep = 24.dp

/**
 * Moves GO from its non-interactive surface, leaving the time selector and GO action independent.
 * Arrow keys and accessibility actions provide alternatives to dragging; Home resets placement.
 * @param enabled Whether movement accepts input.
 * @param onMove Requests a bounded movement in pixels.
 * @param onReset Restores the original sheet-relative placement.
 * @param onDragStart Captures the saved placement before a gesture.
 * @param onDragCancel Restores that placement if the gesture is cancelled.
 */
@Composable
internal fun Modifier.plannerGoMovement(
    enabled: Boolean,
    onMove: (Offset) -> Unit,
    onReset: () -> Unit,
    onDragStart: () -> Pair<Float?, Float?>,
    onDragCancel: (Pair<Float?, Float?>) -> Unit
): Modifier {
    val move by rememberUpdatedState(onMove)
    val reset by rememberUpdatedState(onReset)
    val start by rememberUpdatedState(onDragStart)
    val cancel by rememberUpdatedState(onDragCancel)
    val step = with(LocalDensity.current) { GoMovementStep.toPx() }
    fun moveIfEnabled(amount: Offset): Boolean {
        if (enabled) move(amount)
        return enabled
    }
    return this
        .semantics {
            contentDescription = "Move GO box. Drag the labels or use arrow keys. Home resets its position."
            customActions = listOf(
                CustomAccessibilityAction("Move GO up") { moveIfEnabled(Offset(0f, -step)) },
                CustomAccessibilityAction("Move GO down") { moveIfEnabled(Offset(0f, step)) },
                CustomAccessibilityAction("Move GO left") { moveIfEnabled(Offset(-step, 0f)) },
                CustomAccessibilityAction("Move GO right") { moveIfEnabled(Offset(step, 0f)) },
                CustomAccessibilityAction("Reset GO position") { if (enabled) reset(); enabled }
            )
        }
        .onKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) false
            else when (event.key) {
                Key.DirectionUp -> moveIfEnabled(Offset(0f, -step))
                Key.DirectionDown -> moveIfEnabled(Offset(0f, step))
                Key.DirectionLeft -> moveIfEnabled(Offset(-step, 0f))
                Key.DirectionRight -> moveIfEnabled(Offset(step, 0f))
                Key.MoveHome -> { reset(); true }
                else -> false
            }
        }
        .focusable(enabled)
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            var previous: Pair<Float?, Float?> = null to null
            detectDragGestures(
                onDragStart = { previous = start() },
                onDragCancel = { cancel(previous) },
                onDrag = { change, amount ->
                    change.consume()
                    move(amount)
                }
            )
        }
}
