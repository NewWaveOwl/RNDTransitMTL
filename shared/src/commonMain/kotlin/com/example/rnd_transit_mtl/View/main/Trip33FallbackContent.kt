package com.example.rnd_transit_mtl.View.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.View.current_trip.TripActionButton
import com.example.rnd_transit_mtl.View.current_trip.TripWarning
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.ui.theme.*

// Layout settings — dp widths/padding and viewport fractions from the supplied layouts.
private val FallbackPanelMaximumWidth = 620.dp
private val FallbackPanelHorizontalPadding = 24.dp
private val FallbackPanelSpacing = 16.dp
private const val FallbackPermissionTopFraction = 390f / 716f
private const val FallbackFailureMaximumHeightFraction = 0.65f

/** Renders explicit request failures and a temporary manual-origin selection.
 * @param failure Explicit validation, routing, or permission outcome.
 * @param detail Optional explanation without altering the saved request.
 * @param requestSummary Original request retained for retry.
 * @param originLabel Saved origin label.
 * @param pickingOrigin Whether map taps update a temporary point.
 * @param enabled Whether user actions accept input.
 * @param onChooseMap Enters map selection.
 * @param onConfirmOrigin Saves the temporary point only on confirmation.
 * @param onRetry Retries the request.
 * @param onEditSettings Returns to the planner with its draft intact.
 * @param onUnavailableAction Explains unavailable address search or GPS.
 * @param onCancelPicking Discards temporary selection.
 * @param modifier Parent layout constraints.
 * @param sampleLabel Explicit fixture label; null in the normal application.
 */
@Composable
internal fun Trip33FallbackContent(
    failure: Trip33Failure,
    detail: String,
    requestSummary: String,
    originLabel: String,
    pickingOrigin: Boolean,
    enabled: Boolean,
    onChooseMap: () -> Unit,
    onConfirmOrigin: (TripPoint) -> Unit,
    onRetry: () -> Unit,
    onEditSettings: () -> Unit,
    onUnavailableAction: () -> Unit,
    onCancelPicking: () -> Unit,
    modifier: Modifier = Modifier,
    sampleLabel: String? = null
) {
    var selectedX by rememberSaveable(pickingOrigin) { mutableStateOf<Float?>(null) }
    var selectedY by rememberSaveable(pickingOrigin) { mutableStateOf<Float?>(null) }
    var failurePanelHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val permission = failure == Trip33Failure.PermissionDenied
    val selected = if (selectedX != null && selectedY != null) {
        TripPoint("Selected point on map", selectedX!!, selectedY!!)
    } else {
        null
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelWidth = maxWidth.coerceAtMost(FallbackPanelMaximumWidth)
        val permissionTop = maxHeight * FallbackPermissionTopFraction

        FigmaOriginMap(
            origin = if (pickingOrigin) selected else null,
            enabled = enabled,
            onPick = if (pickingOrigin) { point ->
                selectedX = point.x
                selectedY = point.y
            } else null,
            modifier = Modifier.fillMaxSize()
        )

        if (!permission && !pickingOrigin) {
            TripLabelValueField(
                label = "Starting point",
                value = originLabel,
                showLocationIcon = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = FallbackPanelMaximumWidth)
                    .padding(16.dp)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onChooseMap
                    )
            )
        }

        if (permission && !pickingOrigin) {
            TripWarning(
                "Location access is off",
                "You can still plan a trip. Choose a starting point.",
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .width(panelWidth)
            )
        }

        if (sampleLabel != null) {
            Surface(
                color = TransitWhite,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 112.dp)
            ) {
                Text(
                    sampleLabel,
                    color = TransitMain,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }

        if (permission || pickingOrigin) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = permissionTop)
                    .width(panelWidth)
                    .heightIn(
                        max = (maxHeight - permissionTop).coerceAtLeast(1.dp)
                    )
                    .background(TransitWhite)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FallbackPanelHorizontalPadding, vertical = FallbackPanelSpacing),
                verticalArrangement = Arrangement.spacedBy(FallbackPanelSpacing)
            ) {
                TripLabelValueField(
                    "Starting point",
                    if (pickingOrigin) {
                        selected?.label ?: "Choose a point on the map"
                    } else {
                        "Search a place or address"
                    },
                    showLocationIcon = true,
                    modifier = Modifier.clickable(
                        enabled = enabled && !pickingOrigin,
                        onClick = onUnavailableAction
                    )
                )

                if (pickingOrigin) {
                    TripActionButton(
                        label = "Use this starting point",
                        enabled = enabled && selected != null,
                        onClick = {
                            selected?.let(onConfirmOrigin)
                        }
                    )
                    TripActionButton(
                        "Cancel",
                        onCancelPicking,
                        secondary = true,
                        enabled = enabled
                    )
                    Text(
                        "Tap the sample map, or focus it and use arrow keys.",
                        color = TransitComplementary,
                        fontSize = 12.sp
                    )
                } else {
                    TripActionButton(
                        "Choose a point on the map",
                        onChooseMap,
                        enabled = enabled
                    )
                    TripActionButton(
                        "Try current location again",
                        onUnavailableAction,
                        secondary = true,
                        enabled = enabled
                    )
                    Text(
                        "Your time and transport choices are kept.",
                        color = TransitComplementary,
                        fontSize = 12.sp
                    )
                }

                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        color = TransitComplementary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(panelWidth)
                    .heightIn(max = maxHeight * FallbackFailureMaximumHeightFraction)
                    .onSizeChanged { failurePanelHeightPx = it.height }
                    .background(TransitComplementary)
                    .verticalScroll(rememberScrollState())
            ) {
                val title = when (failure) {
                    Trip33Failure.NoRoute -> "No trip fits these choices"
                    Trip33Failure.ProviderUnavailable ->
                        "Routing service unavailable"
                    else -> "Check your trip settings"
                }

                val explanation = detail.ifBlank {
                    when (failure) {
                        Trip33Failure.NoRoute ->
                            "Try more time or another supported transport mode."
                        Trip33Failure.ProviderUnavailable ->
                            "The service could not be reached. Your choices are saved."
                        else -> "Correct the request and try again."
                    }
                }

                TripWarning(title, explanation)

                Column(
                    Modifier.padding(
                        start = 16.dp, end = 16.dp,
                        top = 14.dp, bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Your request is unchanged",
                        color = TransitWhite,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        requestSummary,
                        color = TransitWhite,
                        fontSize = 14.sp
                    )

                    val provider =
                        failure == Trip33Failure.ProviderUnavailable

                    TripActionButton(
                        if (provider) "Try again" else "Adjust time or modes",
                        if (provider) onRetry else onEditSettings,
                        enabled = enabled
                    )
                    TripActionButton(
                        if (provider) "Edit trip settings"
                        else "Try another search",
                        if (provider) onEditSettings else onRetry,
                        secondary = true,
                        enabled = enabled
                    )
                }
            }
        }

        val panelTop = if (permission || pickingOrigin) permissionTop
            else maxHeight - with(density) { failurePanelHeightPx.toDp() }
        Text(
            "© OpenStreetMap contributors · Sample map",
            color = TransitComplementary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    y = (panelTop - 24.dp).coerceAtLeast(0.dp)
                )
                .padding(start = 8.dp)
                .background(TransitWhite, RoundedCornerShape(4.dp))
                .padding(4.dp)
        )
    }
}
