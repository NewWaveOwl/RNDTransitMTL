package com.example.rnd_transit_mtl.View.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitOrange
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_warning

// Layout settings — dp dimensions and the Figma panel's reference-height fraction.
private val OriginPanelMaxWidth = 620.dp
private val OriginPanelHorizontalPadding = 24.dp
private val OriginPanelSpacing = 16.dp
private val OriginActionMinimumHeight = 52.dp
private val OriginActionCornerRadius = 24.dp
private const val OriginPanelTopFraction = 390f / 716f
private val OriginPickerPanelMinimumHeight = 280.dp
private const val OriginPickerMapMinimumFraction = 0.3f
private val OriginWarningIconSize = 26.dp

/**
 * Origin selection overlay matching permission frame 91:588.
 * Map picking is a temporary mode reached by its primary button.
 *
 * @param origin Saved origin in options mode, or temporary point in picking mode.
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
 * @param liveMapData Optional live platform map data; null uses [FigmaOriginMap].
 * @param draftLabel Optional formatted coordinate/place label for the staged live-map pin.
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
    modifier: Modifier = Modifier,
    liveMapData: TransitMapData? = null,
    draftLabel: String? = null,
) {
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState, isBackEnabled = enabled, onBackCompleted = onBack)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onPreviewKeyEvent {
                if (enabled && it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                    onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        val panelWidth = maxWidth.coerceAtMost(OriginPanelMaxWidth)
        val panelTop = if (picking) {
            (maxHeight * OriginPanelTopFraction)
                .coerceAtMost(
                    (maxHeight - OriginPickerPanelMinimumHeight)
                        .coerceAtLeast(maxHeight * OriginPickerMapMinimumFraction),
                )
        } else {
            maxHeight * OriginPanelTopFraction
        }
        val availablePanelHeight = (maxHeight - panelTop).coerceAtLeast(1.dp)

        if (liveMapData != null) {
            TransitMap(
                liveMapData.copy(sharedTripFlow = true, bottomInset = availablePanelHeight),
                Modifier.fillMaxSize(),
            ) {}
        } else {
            FigmaOriginMap(
                origin = if (picking) origin else null,
                enabled = enabled,
                onPick = onPick,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (locationAccessOff && !picking) {
            LocationAccessOffBanner(
                panelWidth = panelWidth,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            )
        }

        if (liveMapData == null) {
            Text(
                "© OpenStreetMap contributors · Sample map",
                color = TransitComplementary,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = (panelTop - 24.dp).coerceAtLeast(0.dp))
                    .padding(start = 8.dp)
                    .background(TransitWhite, RoundedCornerShape(4.dp))
                    .padding(4.dp),
            )
        }

        OriginSelectionSheet(
            origin = origin,
            minutes = minutes,
            picking = picking,
            enabled = enabled,
            message = message,
            hasLiveMap = liveMapData != null,
            draftLabel = draftLabel,
            onChooseMap = onChooseMap,
            onConfirm = onConfirm,
            onSearch = onSearch,
            onRetryLocation = onRetryLocation,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = panelTop)
                .width(panelWidth)
                .heightIn(max = availablePanelHeight),
        )
    }
}

/** Warning banner shown when location access is off and the user is choosing an origin option. */
@Composable
private fun LocationAccessOffBanner(
    panelWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .width(panelWidth)
            .background(TransitOrange)
            .padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painterResource(Res.drawable.rnd_warning),
            contentDescription = null,
            modifier = Modifier.size(OriginWarningIconSize),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Location access is off",
                color = TransitComplementary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "You can still plan a trip. Choose a starting point.",
                color = TransitComplementary,
                fontSize = 14.sp,
            )
        }
    }
}

/** Scrollable white bottom sheet containing the origin field and action buttons. */
@Composable
private fun OriginSelectionSheet(
    origin: TripPoint?,
    minutes: Int,
    picking: Boolean,
    enabled: Boolean,
    message: String?,
    hasLiveMap: Boolean,
    draftLabel: String?,
    onChooseMap: () -> Unit,
    onConfirm: () -> Unit,
    onSearch: () -> Unit,
    onRetryLocation: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(TransitWhite)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OriginPanelHorizontalPadding, vertical = OriginPanelSpacing),
        verticalArrangement = Arrangement.spacedBy(OriginPanelSpacing),
    ) {
        TripLabelValueField(
            label = "Starting point",
            value = if (picking) {
                draftLabel ?: origin?.label ?: "Choose a point on the map"
            } else {
                "Search a place or address"
            },
            showLocationIcon = true,
            modifier = Modifier.clickable(
                enabled = enabled && !picking,
                role = Role.Button,
                onClick = onSearch,
            ),
        )

        if (picking) {
            OriginAction(
                label = "Start here",
                onClick = onConfirm,
                enabled = enabled && (origin != null || draftLabel != null),
            )
            OriginAction(
                label = "Cancel",
                onClick = onBack,
                enabled = enabled,
                secondary = true,
            )
            Text(
                if (hasLiveMap) "Tap to place a pin. Drag to move the map."
                else "Tap the sample map, or focus it and use arrow keys.",
                color = TransitComplementary,
                fontSize = 12.sp,
            )
        } else {
            OriginAction("Choose a point on the map", onChooseMap, enabled)
            OriginAction(
                if (hasLiveMap) "Use my location" else "Try current location again",
                onRetryLocation,
                enabled,
                secondary = true,
            )
            Text(
                "Your $minutes-minute and transport choices are kept.",
                color = TransitComplementary,
                fontSize = 12.sp,
            )
        }

        if (!message.isNullOrBlank()) {
            Text(message, color = TransitComplementary, fontSize = 12.sp)
        }
    }
}

/** Shared origin action styling; secondary actions use a white surface. */
@Composable
private fun OriginAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    secondary: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = OriginActionMinimumHeight),
        shape = RoundedCornerShape(OriginActionCornerRadius),
        border = if (secondary) BorderStroke(1.dp, TransitMain) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) TransitWhite else TransitMain,
            contentColor = if (secondary) TransitMain else TransitWhite,
        ),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
