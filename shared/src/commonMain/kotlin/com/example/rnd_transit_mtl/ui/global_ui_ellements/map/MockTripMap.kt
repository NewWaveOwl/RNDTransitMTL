package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.tripLegColor
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.end_point_marker
import rnd_transit_mtl.shared.generated.resources.map_sample
import rnd_transit_mtl.shared.generated.resources.map_user_pointer_marker
import rnd_transit_mtl.shared.generated.resources.start_point_marker

// Layout settings — dp marker clearance and camera-control touch size.
private val TripMapMarkerInset = 40.dp
private val TripMapControlSize = 48.dp

// Animation settings — explicit recentering eases to its target; GPS following stays independent.
private const val TRIP_MAP_RECENTER_ANIMATION_DURATION_MILLIS = 500

/**
 * The PNG, route, endpoints and directed pointer share one uniform transform.
 * Current trip starts with exactly the planner's centered Crop; static details
 * use Fit. Pinch/drag changes only the camera, never stored endpoints or time.
 *
 * Map renderer decision (issue #27): drawing stays a composable, not a
 * MapRenderer interface, because rendering belongs to Compose and has no
 * data contract to share. Callers convert stored places with
 * Place.toSampleMapPoint(); this PNG map is the only code that knows the
 * image. An OpenStreetMap tile map will be a sibling composable taking
 * places and legs directly, so screens switch maps without model changes.
 *
 * Each leg is drawn along its stored geometry, so a routed trip shows its
 * turns and transfers. Unrouted legs are dashed to mark them as straight-line
 * fallbacks. The pointer moves along the whole line by length.
 * @param start Immutable source-image start point.
 * @param destination Immutable source-image destination.
 * @param progress Position along the simulated route, bounded to 0..1.
 * @param legs Stored legs from start to destination, usually Trip.toSampleMapLegs();
 * defaults to one unrouted straight line.
 * @param modifier Parent viewport constraints.
 * @param interactive Whether pinch and drag gestures are enabled.
 * @param matchPlannerBackground Whether the initial image uses Crop rather than Fit.
 * @param controlsTopPadding Top inset of the optional zoom controls.
 * @param recenterRequest Increment to center the camera on the current position.
 * @param followPosition Keep the camera centered as the visible position moves.
 * @param onManualCameraMove Reports a user pan or zoom.
 * @param showControls Whether to render the legacy zoom/reset row.
 * @param showPosition Whether to draw the current position pointer.
 * @param userPosition Actual measured position on the bundled map, independent of route progress.
 * @param topInset Overlay clearance sampled when the user explicitly recenters.
 * @param bottomInset Overlay clearance sampled when the user explicitly recenters.
 */
@Composable
fun MockTripMap(
    start: TripPoint,
    destination: TripPoint,
    waypoints: List<TripPoint> = emptyList(),
    progress: Float,
    legs: List<TripMapLeg> = listOf(TripMapLeg(listOf(start, destination), isRouted = false)),
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    matchPlannerBackground: Boolean = false,
    controlsTopPadding: Dp = 10.dp,
    recenterRequest: Int = 0,
    showControls: Boolean = true,
    showPosition: Boolean = true,
    followPosition: Boolean = false,
    onManualCameraMove: () -> Unit = {},
    userPosition: TripPoint? = null,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    userUncertaintyRing: List<TripPoint> = emptyList(),
) {
    val mapPainter = painterResource(Res.drawable.map_sample)
    val startPainter = painterResource(Res.drawable.start_point_marker)
    val endPainter = painterResource(Res.drawable.end_point_marker)
    // XML is an exact shared-resource conversion of the supplied SVG.
    val pointerPainter = painterResource(Res.drawable.map_user_pointer_marker)
    val sourceSize = validMapSourceSize(mapPainter.intrinsicSize)
    val boundedProgress = boundedTripProgress(progress)
    val latestManualCameraMove by rememberUpdatedState(onManualCameraMove)
    val followedProgress = boundedProgress.takeIf { followPosition && showPosition }
    val followedUserPosition = userPosition.takeIf { followPosition && showPosition }
    val percentage = tripProgressPercentage(boundedProgress)
    val density = LocalDensity.current
    val markerInset = with(density) { TripMapMarkerInset.toPx() }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var zoom by rememberSaveable(start, destination) { mutableStateOf(1f) }
    var panX by rememberSaveable(start, destination) { mutableStateOf(0f) }
    var panY by rememberSaveable(start, destination) { mutableStateOf(0f) }
    var handledRecenterRequest by remember(start, destination) { mutableStateOf(0) }
    var recenterAnimating by remember(start, destination) { mutableStateOf(false) }
    var cameraGestureGeneration by remember(start, destination) { mutableStateOf(0) }
    var followCenterYFraction by remember(start, destination) { mutableStateOf(0.5f) }
    val recenterProgress = remember(start, destination) { Animatable(0f) }
    val latestTopInset by rememberUpdatedState(topInset)
    val latestBottomInset by rememberUpdatedState(bottomInset)

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

    val cameraPoint = remember(baseRect, legs, userPosition, boundedProgress) {
        userPosition?.toMapPosition(baseRect) ?: positionAlongPolyline(
            routeLine(legs.map { leg -> leg.points.map { it.toMapPosition(baseRect) } }), boundedProgress
        ).point
    }
    val latestCameraPoint by rememberUpdatedState(cameraPoint)
    fun panForPoint(point: Offset, cameraZoom: Float): Offset {
        val viewportCenter = Offset(viewport.width / 2f, viewport.height / 2f)
        val targetCenter = Offset(viewportCenter.x, viewport.height * followCenterYFraction)
        return (viewportCenter - point) * cameraZoom + targetCenter - viewportCenter
    }

    // GPS updates and overlay insets are not animation keys: neither restarts a recenter.
    LaunchedEffect(recenterRequest, viewport, start, destination, showPosition, cameraGestureGeneration) {
        val requested = recenterRequest > 0 && recenterRequest != handledRecenterRequest
        if (showPosition && requested &&
            viewport.width > 0f &&
            viewport.height > 0f &&
            baseRect.width > 0f
        ) {
            val top = with(density) { latestTopInset.toPx() }.coerceIn(0f, viewport.height)
            val bottom = (viewport.height - with(density) { latestBottomInset.toPx() }).coerceIn(top, viewport.height)
            followCenterYFraction = (top + bottom) / (2f * viewport.height)
            val point = latestCameraPoint
            val targetCenter = Offset(viewport.width / 2f, viewport.height * followCenterYFraction)
            // Zoom only as far as needed to center the marker above overlays while
            // keeping the finite bundled image covering the map viewport.
            val requiredZoom = maxOf(
                targetCenter.x / (point.x - baseRect.left).coerceAtLeast(1f),
                (viewport.width - targetCenter.x) / (baseRect.right - point.x).coerceAtLeast(1f),
                targetCenter.y / (point.y - baseRect.top).coerceAtLeast(1f),
                (viewport.height - targetCenter.y) / (baseRect.bottom - point.y).coerceAtLeast(1f),
            )
            val initialZoom = zoom.coerceIn(minimumZoom, 4f)
            val targetZoom = maxOf(initialZoom, requiredZoom).coerceIn(minimumZoom, 4f)
            val initialPan = Offset(panX * viewport.width, panY * viewport.height)
            val targetPan = clampPan(panForPoint(point, targetZoom), targetZoom)
            handledRecenterRequest = recenterRequest
            recenterAnimating = true
            try {
                recenterProgress.snapTo(0f)
                recenterProgress.animateTo(1f, tween(TRIP_MAP_RECENTER_ANIMATION_DURATION_MILLIS, easing = FastOutSlowInEasing)) {
                    setCamera(initialZoom + (targetZoom - initialZoom) * value,
                        initialPan + (targetPan - initialPan) * value)
                }
            } finally {
                recenterAnimating = false
            }
        }
    }
    LaunchedEffect(viewport, followedProgress, followedUserPosition, showPosition, recenterAnimating, handledRecenterRequest) {
        if (followPosition && showPosition && !recenterAnimating && recenterRequest == handledRecenterRequest &&
            viewport.width > 0f && viewport.height > 0f) {
            val currentZoom = zoom.coerceIn(minimumZoom, 4f)
            setCamera(currentZoom, panForPoint(latestCameraPoint, currentZoom))
        }
    }

    Box(
        modifier = modifier.fillMaxSize().clipToBounds().background(TransitMain)
            .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics {
                contentDescription = "Mock transit map. Yellow start triangle, " +
                    "green destination triangle. " +
                    (if (showPosition) "Directed yellow position pointer. " else "Position unavailable. ") +
                    (if (showPosition && userUncertaintyRing.isNotEmpty()) "Location uncertainty circle. " else "") +
                    (if (legs.all { it.isRouted }) "Solid line follows the planned route. "
                    else "Dashed line is a straight-line preview, not a real route. ") +
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
                            cameraGestureGeneration++
                            latestManualCameraMove()
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

            if (showPosition && userUncertaintyRing.isNotEmpty()) {
                val ring = Path().apply {
                    userUncertaintyRing.forEachIndexed { index, point ->
                        val position = point.toMapPosition(mapRect)
                        if (index == 0) moveTo(position.x, position.y) else lineTo(position.x, position.y)
                    }
                    close()
                }
                drawPath(ring, TransitHighlight.copy(alpha = 0.2f))
                drawPath(ring, TransitHighlight.copy(alpha = 0.53f), style = Stroke(1.5.dp.toPx()))
            }

            val startPosition = start.toMapPosition(mapRect)
            val endPosition = destination.toMapPosition(mapRect)
            val legPositions = legs.map { leg -> leg.points.map { it.toMapPosition(mapRect) } }
            val current = positionAlongPolyline(routeLine(legPositions), boundedProgress)
            val pointerSize = minOf(48.dp.toPx(), size.width * 0.14f, size.height * 0.14f)
            val endpointSize = pointerSize * 1.5f
            val routeWidth = minOf(10.dp.toPx(), pointerSize * 0.25f)
            val travelledFractions = legProgressFractions(legPositions, boundedProgress)
            legPositions.forEachIndexed { index, positions ->
                // Gaps wide enough to read as dashes under the travelled overlay.
                val dashes = if (legs[index].isRouted) null else PathEffect.dashPathEffect(
                    floatArrayOf(routeWidth * 1.6f, routeWidth * 1.6f)
                )
                drawRouteLine(positions, Color.White, routeWidth * 1.5f, dashes)
                drawRouteLine(positions, tripLegColor(index), routeWidth, dashes)
                val travelled = travelledFractions[index]
                if (travelled > 0f) {
                    drawRouteLine(
                        positionAlongPolyline(positions, travelled).travelled,
                        Color.White.copy(alpha = 0.5f), routeWidth * 0.3f, dashes
                    )
                }
            }
            drawAnchoredTripEndpoint(startPainter, startPosition, endpointSize)
            if (waypoints.isEmpty()) drawAnchoredTripEndpoint(endPainter, endPosition, endpointSize)
            else waypoints.forEach { drawAnchoredTripEndpoint(endPainter, it.toMapPosition(mapRect), endpointSize) }
            if (showPosition) {
                val direction = current.direction.takeUnless { it == Offset.Zero }
                    ?: (endPosition - startPosition)
                drawDirectedTripPointer(
                    pointerPainter, userPosition?.toMapPosition(mapRect) ?: current.point, pointerSize,
                    tripPointerRotation(Offset.Zero, direction)
                )
            }
        }

        if (interactive && showControls) {
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
                    modifier = Modifier.size(TripMapControlSize)
                        .semantics { contentDescription = "Zoom out" }
                ) { Text("−", color = TransitWhite) }
                TextButton(
                    onClick = {
                        setCamera(zoom * 1.25f, Offset(panX * viewport.width, panY * viewport.height))
                    },
                    enabled = zoom < 4f,
                    modifier = Modifier.size(TripMapControlSize)
                        .semantics { contentDescription = "Zoom in" }
                ) { Text("+", color = TransitWhite) }
                TextButton(onClick = { setCamera(1f, Offset.Zero) }) {
                    Text("Reset", color = TransitWhite)
                }
            }
        }
    }
}

/** Draws a leg through its points with round ends and corners. */
private fun DrawScope.drawRouteLine(
    points: List<Offset>,
    color: Color,
    width: Float,
    pathEffect: PathEffect?
) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        path, color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = pathEffect)
    )
}
