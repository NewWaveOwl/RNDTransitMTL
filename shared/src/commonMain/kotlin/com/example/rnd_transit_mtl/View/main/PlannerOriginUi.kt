package com.example.rnd_transit_mtl.View.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.mapImageRect
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.validMapSourceSize
import com.example.rnd_transit_mtl.ui.theme.*
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.map_sample
import rnd_transit_mtl.shared.generated.resources.rnd_origin_marker
import rnd_transit_mtl.shared.generated.resources.rnd_recenter
import rnd_transit_mtl.shared.generated.resources.rnd_warning

// Layout settings � dp dimensions and the Figma panel's reference-height fraction.
private val OriginPanelMaxWidth = 620.dp
private val OriginPanelHorizontalPadding = 24.dp
private val OriginPanelSpacing = 16.dp
private val OriginMarkerSize = 36.dp
private val OriginRecenterSize = 48.dp
private val OriginActionMinimumHeight = 52.dp
private val OriginActionCornerRadius = 24.dp
private val OriginPanelTopFraction = 390f / 716f

/**
 * Uses source-image coordinates for both the map and origin.
 * Camera movement never edits the selected origin.
 * @param origin Marker position in normalized source-image coordinates, or no marker.
 * @param modifier Parent viewport constraints.
 * @param enabled Whether keyboard and pointer input are accepted.
 * @param recenterRequest Increment to restore the camera to the selected origin.
 * @param onPick Enables point selection when supplied; otherwise dragging pans the camera.
 */
@Composable
internal fun FigmaOriginMap(
    origin: TripPoint?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    recenterRequest: Int = 0,
    onPick: ((TripPoint) -> Unit)? = null
) {
    val map = painterResource(Res.drawable.map_sample)
    val marker = painterResource(Res.drawable.rnd_origin_marker)
    val source = validMapSourceSize(map.intrinsicSize)
    val picking = onPick != null

    var viewport by remember { mutableStateOf(Size.Zero) }
    var cameraX by rememberSaveable {
        mutableStateOf(origin?.x ?: 0.5f)
    }
    var cameraY by rememberSaveable {
        mutableStateOf(origin?.y ?: 0.35f)
    }

    LaunchedEffect(origin?.x, origin?.y, recenterRequest, picking) {
        if (!picking) {
            cameraX = origin?.x ?: 0.5f
            cameraY = origin?.y ?: 0.35f
        }
    }

    val base = if (viewport.width > 0f && viewport.height > 0f) {
        mapImageRect(viewport, source, crop = true)
    } else {
        Rect.Zero
    }

    val rect = if (base.width > 0f) {
        val left = (
            viewport.width / 2f - cameraX * base.width
        ).coerceIn(minOf(0f, viewport.width - base.width), 0f)

        val top = (
            viewport.height * 0.3f - cameraY * base.height
        ).coerceIn(minOf(0f, viewport.height - base.height), 0f)

        Rect(left, top, left + base.width, top + base.height)
    } else {
        Rect.Zero
    }

    val latestRect by rememberUpdatedState(rect)
    val latestOrigin by rememberUpdatedState(origin)
    val latestPick by rememberUpdatedState(onPick)

    Canvas(
        modifier
            .clipToBounds()
            .background(TransitWhite)
            .onSizeChanged {
                viewport = Size(it.width.toFloat(), it.height.toFloat())
            }
            .semantics {
                contentDescription = if (picking) {
                    "Choose a sample starting point. Tap the map, " +
                        "or focus it and use the arrow keys."
                } else {
                    "Sample map. Arrow keys move the map. " +
                        "Home recenters on the starting point."
                }
            }
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    val dx = when (event.key) {
                        Key.DirectionLeft -> -0.01f
                        Key.DirectionRight -> 0.01f
                        else -> 0f
                    }
                    val dy = when (event.key) {
                        Key.DirectionUp -> -0.01f
                        Key.DirectionDown -> 0.01f
                        else -> 0f
                    }

                    when {
                        dx != 0f || dy != 0f -> {
                            if (picking) {
                                val point = latestOrigin
                                    ?: TripPoint("Selected point", 0.5f, 0.35f)

                                latestPick?.invoke(
                                    point.copy(
                                        label = "Selected point on map",
                                        x = (point.x + dx).coerceIn(0f, 1f),
                                        y = (point.y + dy).coerceIn(0f, 1f)
                                    )
                                )
                            } else {
                                cameraX = (cameraX + dx).coerceIn(0f, 1f)
                                cameraY = (cameraY + dy).coerceIn(0f, 1f)
                            }
                            true
                        }

                        event.key == Key.MoveHome && !picking -> {
                            cameraX = latestOrigin?.x ?: 0.5f
                            cameraY = latestOrigin?.y ?: 0.35f
                            true
                        }

                        else -> false
                    }
                }
            }
            .focusable(enabled)
            .pointerInput(enabled, picking) {
                if (!enabled || !picking) return@pointerInput

                detectTapGestures { position ->
                    val bounds = latestRect
                    if (bounds.width > 0f && bounds.height > 0f) {
                        latestPick?.invoke(
                            TripPoint(
                                "Selected point on map",
                                ((position.x - bounds.left) / bounds.width)
                                    .coerceIn(0f, 1f),
                                ((position.y - bounds.top) / bounds.height)
                                    .coerceIn(0f, 1f)
                            )
                        )
                    }
                }
            }
            .pointerInput(enabled, picking) {
                if (!enabled || picking) return@pointerInput

                detectDragGestures { change, amount ->
                    val bounds = latestRect
                    if (bounds.width > 0f && bounds.height > 0f) {
                        change.consume()
                        cameraX = (
                            cameraX - amount.x / bounds.width
                        ).coerceIn(0f, 1f)
                        cameraY = (
                            cameraY - amount.y / bounds.height
                        ).coerceIn(0f, 1f)
                    }
                }
            }
    ) {
        if (rect.width <= 0f) return@Canvas

        translate(rect.left, rect.top) {
            with(map) { draw(size = rect.size) }
        }

        origin?.let { point ->
            val markerSize = OriginMarkerSize.toPx()
            val x = rect.left + point.x * rect.width
            val y = rect.top + point.y * rect.height

            translate(x - markerSize / 2f, y - markerSize / 2f) {
                with(marker) {
                    draw(size = Size(markerSize, markerSize))
                }
            }
        }
    }
}

/** Figma recenter control.
 * @param enabled Whether the origin is available and input is permitted.
 * @param onClick Requests camera recentering without changing the origin.
 * @param modifier Parent placement.
 */
@Composable
internal fun FigmaRecenterButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(Res.drawable.rnd_recenter),
        contentDescription = "Recenter map on starting point",
        modifier = modifier
            .size(OriginRecenterSize)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
    )
}

/** Shared origin action styling; secondary actions use a white surface.
 * @param label Visible action text.
 * @param onClick Action callback.
 * @param enabled Whether the action accepts input.
 * @param secondary Whether this is a secondary action.
 */
@Composable
private fun OriginAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    secondary: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = OriginActionMinimumHeight),
        shape = RoundedCornerShape(OriginActionCornerRadius),
        border = if (secondary) BorderStroke(1.dp, TransitMain) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) TransitWhite else TransitMain,
            contentColor = if (secondary) TransitMain else TransitWhite
        )
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Options match permission frame 91:588.
 * Map picking is a temporary mode reached by its existing primary button.
 * @param origin Saved origin in options mode, temporary point in picking mode.
 * @param minutes Current planned minutes, kept while choosing an origin.
 * @param locationAccessOff Explicit permission-off state; no live GPS is inferred.
 * @param picking Whether map taps choose a temporary point.
 * @param enabled Whether actions and back navigation accept input.
 * @param message Optional search or location availability feedback.
 * @param onPick Reports a temporary source-image point.
 * @param onChooseMap Enters map selection.
 * @param onConfirm Accepts the temporary point.
 * @param onSearch Requests address search.
 * @param onRetryLocation Requests current location.
 * @param onBack Cancels the current mode.
 * @param modifier Parent layout constraints.
 */
@Composable
internal fun OriginSelectionContent(
    origin: TripPoint?,
    minutes: Int,
    locationAccessOff: Boolean,
    picking: Boolean,
    enabled: Boolean,
    message: String?,
    onPick: (TripPoint) -> Unit,
    onChooseMap: () -> Unit,
    onConfirm: () -> Unit,
    onSearch: () -> Unit,
    onRetryLocation: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backState = rememberNavigationEventState(
        currentInfo = NavigationEventInfo.None
    )

    NavigationBackHandler(
        state = backState,
        isBackEnabled = enabled,
        onBackCompleted = onBack
    )

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onPreviewKeyEvent {
                if (enabled &&
                    it.type == KeyEventType.KeyDown &&
                    it.key == Key.Escape
                ) {
                    onBack()
                    true
                } else {
                    false
                }
            }
    ) {
        val panelWidth = maxWidth.coerceAtMost(OriginPanelMaxWidth)
        val panelTop = maxHeight * OriginPanelTopFraction
        val availablePanelHeight =
            (maxHeight - panelTop).coerceAtLeast(1.dp)

        FigmaOriginMap(
            origin = if (picking) origin else null,
            enabled = enabled,
            onPick = if (picking) onPick else null,
            modifier = Modifier.fillMaxSize()
        )

        if (locationAccessOff && !picking) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .width(panelWidth)
                    .background(TransitOrange)
                    .padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painterResource(Res.drawable.rnd_warning),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Location access is off",
                        color = TransitComplementary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "You can still plan a trip. Choose a starting point.",
                        color = TransitComplementary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Text(
            "© OpenStreetMap contributors · Sample map",
            color = TransitComplementary,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = (panelTop - 24.dp).coerceAtLeast(0.dp))
                .padding(start = 8.dp)
                .background(TransitWhite, RoundedCornerShape(4.dp))
                .padding(4.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = panelTop)
                .width(panelWidth)
                .heightIn(max = availablePanelHeight)
                .background(TransitWhite)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OriginPanelHorizontalPadding, vertical = OriginPanelSpacing),
            verticalArrangement = Arrangement.spacedBy(OriginPanelSpacing)
        ) {
            TripLabelValueField(
                label = "Starting point",
                value = if (picking) {
                    origin?.label ?: "Choose a point on the map"
                } else {
                    "Search a place or address"
                },
                showLocationIcon = true,
                modifier = Modifier.clickable(
                    enabled = enabled && !picking,
                    role = Role.Button,
                    onClick = onSearch
                )
            )

            if (picking) {
                OriginAction(
                    label = "Use this starting point",
                    onClick = onConfirm,
                    enabled = enabled && origin != null
                )
                OriginAction(
                    label = "Cancel",
                    onClick = onBack,
                    enabled = enabled,
                    secondary = true
                )
                Text(
                    "Tap the sample map, or focus it and use arrow keys.",
                    color = TransitComplementary,
                    fontSize = 12.sp
                )
            } else {
                OriginAction(
                    "Choose a point on the map",
                    onChooseMap,
                    enabled
                )
                OriginAction(
                    "Try current location again",
                    onRetryLocation,
                    enabled,
                    secondary = true
                )
                Text(
                    "Your $minutes-minute and transport choices are kept.",
                    color = TransitComplementary,
                    fontSize = 12.sp
                )
            }

            if (!message.isNullOrBlank()) {
                Text(
                    message,
                    color = TransitComplementary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
