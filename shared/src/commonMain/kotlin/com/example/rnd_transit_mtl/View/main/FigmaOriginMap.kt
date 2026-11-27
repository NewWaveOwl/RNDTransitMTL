package com.example.rnd_transit_mtl.View.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.mapImageRect
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.validMapSourceSize
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ellipse_starting
import rnd_transit_mtl.shared.generated.resources.map_sample
import rnd_transit_mtl.shared.generated.resources.rnd_origin_marker

// Layout settings — dp marker sizes and normalized keyboard-step / viewport focus constants.
private val OriginMarkerSize = 36.dp
private val OriginPickingMarkerSize = 16.dp
private const val OriginDefaultCameraX = 0.5f
private const val OriginDefaultCameraY = 0.35f
private const val OriginCameraVerticalFocusFraction = 0.3f
private const val OriginKeyboardStepNormalized = 0.01f

/**
 * Interactive bundled sample map using source-image coordinates for both the map and origin.
 * Camera movement never edits the selected origin.
 *
 * @param origin Marker position in normalized source-image coordinates, or null for no marker.
 * @param modifier Parent viewport constraints.
 * @param enabled Whether keyboard and pointer input are accepted.
 * @param recenterRequest Increment to restore the camera to the selected origin.
 * @param onPick Enables point selection when supplied; otherwise dragging pans the camera.
 * @param onTap Stages a point from the browsing map without changing keyboard camera controls.
 */
@Composable
internal fun FigmaOriginMap(
    origin: TripPoint?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    recenterRequest: Int = 0,
    onPick: ((TripPoint) -> Unit)? = null,
    onTap: ((TripPoint) -> Unit)? = null,
) {
    val map = painterResource(Res.drawable.map_sample)
    val picking = onPick != null
    val marker = painterResource(
        if (picking) Res.drawable.ellipse_starting else Res.drawable.rnd_origin_marker,
    )
    val source = validMapSourceSize(map.intrinsicSize)

    var viewport by remember { mutableStateOf(Size.Zero) }
    var cameraX by rememberSaveable { mutableStateOf(origin?.x ?: OriginDefaultCameraX) }
    var cameraY by rememberSaveable { mutableStateOf(origin?.y ?: OriginDefaultCameraY) }

    LaunchedEffect(origin?.x, origin?.y, recenterRequest, picking) {
        if (!picking) {
            cameraX = origin?.x ?: OriginDefaultCameraX
            cameraY = origin?.y ?: OriginDefaultCameraY
        }
    }

    val rect = computeOriginMapRect(viewport, source, cameraX, cameraY)
    val latestRect by rememberUpdatedState(rect)
    val latestOrigin by rememberUpdatedState(origin)
    val latestPick by rememberUpdatedState(onPick ?: onTap)

    Canvas(
        modifier
            .clipToBounds()
            .background(TransitWhite)
            .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics {
                contentDescription = if (picking) {
                    "Choose a sample starting point. Tap the map, " +
                        "or focus it and use the arrow keys."
                } else {
                    "Sample map. Tap to choose a starting point. Arrow keys move the map. " +
                        "Home recenters on the starting point."
                }
            }
            .onKeyEvent { event ->
                handleOriginMapKeyEvent(
                    event = event,
                    enabled = enabled,
                    picking = picking,
                    latestOrigin = latestOrigin,
                    cameraX = cameraX,
                    cameraY = cameraY,
                    onPickPoint = { latestPick?.invoke(it) },
                    onCameraMove = { newX, newY ->
                        cameraX = newX
                        cameraY = newY
                    },
                )
            }
            .focusable(enabled)
            .pointerInput(enabled, picking, onTap != null) {
                if (!enabled || (onPick == null && onTap == null)) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    if (up.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                        return@awaitEachGesture
                    }
                    val bounds = latestRect
                    if (bounds.width > 0f && bounds.height > 0f) {
                        latestPick?.invoke(
                            TripPoint(
                                "Selected point on map",
                                ((up.position.x - bounds.left) / bounds.width).coerceIn(0f, 1f),
                                ((up.position.y - bounds.top) / bounds.height).coerceIn(0f, 1f),
                            ),
                        )
                    }
                }
            }
            .pointerInput(enabled, picking) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, amount ->
                    val bounds = latestRect
                    if (bounds.width > 0f && bounds.height > 0f) {
                        change.consume()
                        cameraX = (cameraX - amount.x / bounds.width).coerceIn(0f, 1f)
                        cameraY = (cameraY - amount.y / bounds.height).coerceIn(0f, 1f)
                    }
                }
            },
    ) {
        if (rect.width <= 0f) return@Canvas

        translate(rect.left, rect.top) {
            with(map) { draw(size = rect.size) }
        }

        origin?.let { point ->
            val markerSize = (if (picking) OriginPickingMarkerSize else OriginMarkerSize).toPx()
            val x = rect.left + point.x * rect.width
            val y = rect.top + point.y * rect.height
            translate(x - markerSize / 2f, y - markerSize / 2f) {
                with(marker) { draw(size = Size(markerSize, markerSize)) }
            }
        }
    }
}

/** Computes the clamped drawing rectangle for the sample map centred around normalized camera coordinates. */
private fun computeOriginMapRect(
    viewport: Size,
    source: Size,
    cameraX: Float,
    cameraY: Float,
): Rect {
    if (viewport.width <= 0f || viewport.height <= 0f) return Rect.Zero
    val base = mapImageRect(viewport, source, crop = true)
    if (base.width <= 0f) return Rect.Zero
    val left = (viewport.width / 2f - cameraX * base.width)
        .coerceIn(minOf(0f, viewport.width - base.width), 0f)
    val top = (viewport.height * OriginCameraVerticalFocusFraction - cameraY * base.height)
        .coerceIn(minOf(0f, viewport.height - base.height), 0f)
    return Rect(left, top, left + base.width, top + base.height)
}

/** Handles directional arrow and Home keys for picking or panning on [FigmaOriginMap]. */
private fun handleOriginMapKeyEvent(
    event: KeyEvent,
    enabled: Boolean,
    picking: Boolean,
    latestOrigin: TripPoint?,
    cameraX: Float,
    cameraY: Float,
    onPickPoint: (TripPoint) -> Unit,
    onCameraMove: (Float, Float) -> Unit,
): Boolean {
    if (!enabled || event.type != KeyEventType.KeyDown) return false
    val dx = when (event.key) {
        Key.DirectionLeft -> -OriginKeyboardStepNormalized
        Key.DirectionRight -> OriginKeyboardStepNormalized
        else -> 0f
    }
    val dy = when (event.key) {
        Key.DirectionUp -> -OriginKeyboardStepNormalized
        Key.DirectionDown -> OriginKeyboardStepNormalized
        else -> 0f
    }
    return when {
        dx != 0f || dy != 0f -> {
            if (picking) {
                val point = latestOrigin ?: TripPoint("Selected point", OriginDefaultCameraX, OriginDefaultCameraY)
                onPickPoint(
                    point.copy(
                        label = "Selected point on map",
                        x = (point.x + dx).coerceIn(0f, 1f),
                        y = (point.y + dy).coerceIn(0f, 1f),
                    ),
                )
            } else {
                onCameraMove((cameraX + dx).coerceIn(0f, 1f), (cameraY + dy).coerceIn(0f, 1f))
            }
            true
        }

        event.key == Key.MoveHome && !picking -> {
            onCameraMove(latestOrigin?.x ?: OriginDefaultCameraX, latestOrigin?.y ?: OriginDefaultCameraY)
            true
        }

        else -> false
    }
}
