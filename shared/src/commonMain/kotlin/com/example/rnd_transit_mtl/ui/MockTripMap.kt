package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.TripPoint
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitOrange
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.end_point_marker
import rnd_transit_mtl.shared.generated.resources.map_sample
import rnd_transit_mtl.shared.generated.resources.map_user_pointer_marker
import rnd_transit_mtl.shared.generated.resources.start_point_marker

/**
 * The PNG, route, endpoints and directed pointer share one uniform transform.
 * Current trip starts with exactly the planner's centered Crop; static details
 * use Fit. Pinch/drag changes only the camera, never stored endpoints or time.
 */
@Composable
fun MockTripMap(
    start: TripPoint,
    destination: TripPoint,
    progress: Float,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    matchPlannerBackground: Boolean = false,
    controlsTopPadding: Dp = 10.dp
) {
    val mapPainter = painterResource(Res.drawable.map_sample)
    val startPainter = painterResource(Res.drawable.start_point_marker)
    val endPainter = painterResource(Res.drawable.end_point_marker)
    // XML is an exact shared-resource conversion of the supplied SVG.
    val pointerPainter = painterResource(Res.drawable.map_user_pointer_marker)
    val sourceSize = validMapSourceSize(mapPainter.intrinsicSize)
    val boundedProgress = boundedTripProgress(progress)
    val percentage = tripProgressPercentage(boundedProgress)
    val markerInset = with(LocalDensity.current) { 40.dp.toPx() }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var zoom by rememberSaveable(start, destination) { mutableStateOf(1f) }
    var panX by rememberSaveable(start, destination) { mutableStateOf(0f) }
    var panY by rememberSaveable(start, destination) { mutableStateOf(0f) }

    val baseRect = if (viewport.width > 0f && viewport.height > 0f) {
        mapImageRect(viewport, sourceSize, matchPlannerBackground, markerInset)
    } else Rect.Zero
    // Zooming out to Fit lets older trips outside the initial crop remain reachable.
    val minimumZoom = if (matchPlannerBackground && baseRect.width > 0f) {
        minOf(viewport.width / baseRect.width, viewport.height / baseRect.height)
    } else 1f

    fun clampPan(pan: Offset, cameraZoom: Float): Offset {
        val limitX = ((baseRect.width * cameraZoom - viewport.width) / 2f)
            .coerceAtLeast(0f)
        val limitY = ((baseRect.height * cameraZoom - viewport.height) / 2f)
            .coerceAtLeast(0f)
        return Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))
    }

    fun setCamera(value: Float, pan: Offset) {
        if (viewport.width <= 0f || viewport.height <= 0f) return
        val nextZoom = value.coerceIn(minimumZoom, 4f)
        val boundedPan = clampPan(pan, nextZoom)
        zoom = nextZoom
        panX = boundedPan.x / viewport.width
        panY = boundedPan.y / viewport.height
    }

    Box(
        modifier = modifier.fillMaxSize().clipToBounds().background(TransitMain)
            .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics {
                contentDescription = "Mock transit map. Yellow start triangle, " +
                    "green destination triangle, and a directed yellow pointer. " +
                    if (interactive) "Pinch to zoom and drag to move the picture." else ""
                stateDescription = "$percentage percent complete"
            }
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(interactive, start, destination, viewport, matchPlannerBackground) {
                    if (!interactive) return@pointerInput
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        if (viewport.width > 0f && viewport.height > 0f) {
                            val oldZoom = zoom.coerceIn(minimumZoom, 4f)
                            val nextZoom = (oldZoom * zoomChange).coerceIn(minimumZoom, 4f)
                            val ratio = nextZoom / oldZoom
                            val center = Offset(viewport.width / 2f, viewport.height / 2f)
                            val oldPan = clampPan(
                                Offset(panX * viewport.width, panY * viewport.height), oldZoom
                            )
                            val nextPan = (centroid - center) -
                                (centroid - center - oldPan) * ratio + pan
                            setCamera(nextZoom, nextPan)
                        }
                    }
                }
        ) {
            if (baseRect.width <= 0f || baseRect.height <= 0f) return@Canvas
            val cameraZoom = zoom.coerceIn(minimumZoom, 4f)
            val cameraPan = clampPan(Offset(panX * size.width, panY * size.height), cameraZoom)
            val imageWidth = baseRect.width * cameraZoom
            val imageHeight = baseRect.height * cameraZoom
            val mapRect = Rect(
                left = (size.width - imageWidth) / 2f + cameraPan.x,
                top = (size.height - imageHeight) / 2f + cameraPan.y,
                right = (size.width + imageWidth) / 2f + cameraPan.x,
                bottom = (size.height + imageHeight) / 2f + cameraPan.y
            )
            translate(mapRect.left, mapRect.top) {
                with(mapPainter) { draw(size = mapRect.size) }
            }

            val startPosition = start.toMapPosition(mapRect)
            val endPosition = destination.toMapPosition(mapRect)
            val currentPosition = Offset(
                startPosition.x + (endPosition.x - startPosition.x) * boundedProgress,
                startPosition.y + (endPosition.y - startPosition.y) * boundedProgress
            )
            val pointerSize = minOf(48.dp.toPx(), size.width * 0.14f, size.height * 0.14f)
            val endpointSize = pointerSize * 1.5f
            val routeWidth = minOf(10.dp.toPx(), pointerSize * 0.25f)
            drawLine(
                TransitMain, startPosition, endPosition,
                strokeWidth = routeWidth, cap = StrokeCap.Round
            )
            if (boundedProgress > 0f) {
                drawLine(
                    TransitOrange, startPosition, currentPosition,
                    strokeWidth = routeWidth * 0.65f, cap = StrokeCap.Round
                )
            }
            drawDirectedTripPointer(
                pointerPainter, currentPosition, pointerSize,
                tripPointerRotation(startPosition, endPosition)
            )
            drawAnchoredTripEndpoint(startPainter, startPosition, endpointSize)
            drawAnchoredTripEndpoint(endPainter, endPosition, endpointSize)
        }

        if (interactive) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(start = 10.dp, end = 10.dp, top = controlsTopPadding)
                    .background(TransitMain, RoundedCornerShape(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                TextButton(
                    onClick = {
                        setCamera(zoom / 1.25f, Offset(panX * viewport.width, panY * viewport.height))
                    },
                    enabled = zoom > minimumZoom,
                    modifier = Modifier.size(48.dp)
                        .semantics { contentDescription = "Zoom out" }
                ) { Text("−", color = TransitWhite) }
                TextButton(
                    onClick = {
                        setCamera(zoom * 1.25f, Offset(panX * viewport.width, panY * viewport.height))
                    },
                    enabled = zoom < 4f,
                    modifier = Modifier.size(48.dp)
                        .semantics { contentDescription = "Zoom in" }
                ) { Text("+", color = TransitWhite) }
                TextButton(onClick = { setCamera(1f, Offset.Zero) }) {
                    Text("Reset", color = TransitWhite)
                }
            }
        }
    }
}
