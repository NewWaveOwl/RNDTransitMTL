package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.center
import kotlin.math.hypot
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.ui.theme.*

// Layout settings — padding/spacing determine responsive cell widths; fractions (0..1) size buttons within cells.
private val PreferenceBandHorizontalPadding = 24.dp
private val PreferenceBandVerticalPadding = 16.dp
private val PreferenceChoiceSpacing = 8.dp
private val TransportGridColumnCount = 3
private val PreferenceGridColumnCount = 3
private val TransportButtonWidthFraction = 1f
private val ZoneButtonWidthFraction = 1f
private val WalkingButtonWidthFraction = 1f
private val AttractionButtonWidthFraction = 1f
private val PreferenceChoiceHeight = 44.dp
private val PreferenceChoiceCornerRadius = 14.dp
private val PreferenceChoiceHorizontalPadding = 8.dp
private val PreferenceChoiceVerticalPadding = 10.dp
private val ZoneDropdownMinimumHeight = 203.dp
private val IntensityBandHeight = 160.dp

// Animation settings — shared by Walk and transit dropdowns. Larger duration means slower motion.
private const val PREFERENCE_DROPDOWN_ANIMATION_DURATION_MILLIS = 500
private const val TRANSPORT_SELECTION_FILL_ANIMATION_DURATION_MILLIS = 400
private val PreferenceDropdownEasing = FastOutSlowInEasing

/**
 * Adds the main planner preference bands in scroll order.
 *
 * @param scale Responsive size multiplier relative to the 412 px Figma width.
 * @param transportTypes Loaded transport catalogue.
 * @param selectedTransportIds Enabled transport IDs.
 * @param selectedZonesByTransport Independently selected zone IDs for each transit mode.
 * @param onToggleTransport Requests a transport toggle.
 * @param onToggleZone Requests a zone toggle for the supplied transport ID and zone ID.
 * @param walkingPreference Selected walking style.
 * @param bikeSpeedKmh Selected cycling speed in km/h.
 * @param onBikeSpeedChange Requests a cycling-speed change.
 * @param intensity Current attraction intensity from 0 to 100.
 * @param onIntensityChange Requests an attraction-intensity change.
 * @param selectedAttractionIds Enabled attraction labels.
 * @param onWalkingPreferenceChange Requests a walking-style change.
 * @param onToggleAttraction Requests an attraction toggle.
 * @param enabled Whether choices can change.
 * @param onOpenDetailedSettings Opens the existing Settings destination.
 * @param expandedTransportId Mode whose zone dropdown is open, independently of selection.
 * @param onExpandedTransportChange Opens, closes, or switches the transit dropdown.
 */
internal fun LazyListScope.PlannerPreferenceBands(
    scale: Float,
    transportTypes: List<TransportType>,
    selectedTransportIds: List<String>,
    selectedZonesByTransport: Map<String, List<String>>,
    onToggleTransport: (String) -> Unit,
    onToggleZone: (String, String) -> Unit,
    walkingPreference: String,
    selectedAttractionIds: List<String>,
    onWalkingPreferenceChange: (String) -> Unit,
    onToggleAttraction: (String) -> Unit,
    enabled: Boolean,
    onOpenDetailedSettings: () -> Unit,
    expandedTransportId: String?,
    onExpandedTransportChange: (String) -> Unit,
    bikeSpeedKmh: Int,
    onBikeSpeedChange: (Int) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit
) {
    item(key = "transport") {
        // Animate the dropdown itself so following bands move with its visible height.
        Column(Modifier.fillMaxWidth().background(TransitMain)) {
            PreferenceBand(TransitMain, scale) {
                Text("Types of transport", color = TransitWhite, fontSize = 20.sp * scale,
                    lineHeight = 28.sp * scale, fontWeight = FontWeight.Bold)
                val order = listOf("walk", "bike", "bus", "metro", "train", "rem")
                val ordered = transportTypes.sortedBy { order.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                ordered.chunked(TransportGridColumnCount).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PreferenceChoiceSpacing * scale)) {
                        row.forEach { transport ->
                            val selected = transport.id in selectedTransportIds
                            val title = if (transport.id == "walk") "Walk" else transport.label
                            val selectedZoneIds = selectedZonesByTransport[transport.id].orEmpty()
                            val zones = if (selected && transport.usesRoutes && selectedZoneIds.isNotEmpty())
                                " " + selectedZoneIds.sorted().joinToString(", ") else ""
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                PlannerChoice(title + zones, selected, enabled, {
                                    if (transport.usesRoutes) onExpandedTransportChange(transport.id)
                                    else onToggleTransport(transport.id)
                                }, scale, Modifier.fillMaxWidth(TransportButtonWidthFraction),
                                    dropdownExpanded = if (transport.usesRoutes) expandedTransportId == transport.id else null)
                            }
                        }
                        repeat(TransportGridColumnCount - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            TransitZoneDropdown(
                transportTypes = transportTypes,
                requestedTransportId = expandedTransportId,
                selectedZonesByTransport = selectedZonesByTransport,
                onToggleZone = onToggleZone,
                scale = scale,
                enabled = enabled
            )
        }
    }
    // Keep the lazy item mounted until the closing animation has finished.
    item(key = "bike") {
        AnimatedVisibility(visible = "bike" in selectedTransportIds,
            enter = preferenceDropdownEnter(), exit = preferenceDropdownExit()) {
            BikeSpeedPanel(bikeSpeedKmh, onBikeSpeedChange, scale, enabled && "bike" in selectedTransportIds)
        }
    }
    item(key = "walking") {
        AnimatedVisibility(
            visible = "walk" in selectedTransportIds,
            enter = preferenceDropdownEnter(),
            exit = preferenceDropdownExit()
        ) {
            PreferenceBand(TransitComplementary, scale) {
                BandTitle("Walking preferences", TransitWhite, scale)
                Text("Choose your pace.", color = TransitWhite, fontSize = 13.sp * scale,
                    lineHeight = 18.sp * scale)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PreferenceChoiceSpacing * scale)) {
                    MainPlannerViewModel.walkingPreferences.forEach { preference ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            PlannerChoice(preference, preference == walkingPreference, enabled && "walk" in selectedTransportIds,
                                { onWalkingPreferenceChange(preference) }, scale, Modifier.fillMaxWidth(WalkingButtonWidthFraction), Role.RadioButton)
                        }
                    }
                }
            }
        }
    }
    item(key = "attractions") {
        PreferenceBand(TransitMain, scale) {
            Text("Make it your kind of random.", color = TransitWhite, fontSize = 22.sp * scale,
                lineHeight = 31.sp * scale, fontWeight = FontWeight.Bold)
            Text("Attractions", color = TransitWhite, fontSize = 14.sp * scale, fontWeight = FontWeight.Bold)
            ChoiceGrid(MainPlannerViewModel.attractionIds, selectedAttractionIds, enabled, onToggleAttraction, scale)
            Text("Sample routes don’t yet reflect walking pace, bike speed, or attraction preferences.",
                color = TransitWhite, style = MaterialTheme.typography.bodySmall)
        }
    }
    item(key = "intensity") {
        IntensityPanel(layoutScale = scale, intensity = intensity, onIntensityChange = onIntensityChange,
            validationMessage = "", enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = IntensityBandHeight * scale))
    }
    item(key = "settings") {
        PreferenceBand(TransitComplementary, scale) {
            TextButton(onClick = onOpenDetailedSettings, enabled = enabled,
                modifier = Modifier.fillMaxWidth()) {
                Text("More preferences in Settings", color = TransitWhite)
            }
        }
    }
}

/** Shared opening motion for walking preferences and transit-zone dropdowns. */
private fun preferenceDropdownEnter() = expandVertically(
    animationSpec = tween(PREFERENCE_DROPDOWN_ANIMATION_DURATION_MILLIS, easing = PreferenceDropdownEasing),
    expandFrom = Alignment.Top
)

/** Shared closing motion; content remains mounted and clipped while its height shrinks. */
private fun preferenceDropdownExit() = shrinkVertically(
    animationSpec = tween(PREFERENCE_DROPDOWN_ANIMATION_DURATION_MILLIS, easing = PreferenceDropdownEasing),
    shrinkTowards = Alignment.Top
)

/**
 * Retains the current menu during collapse, then opens the most recently requested mode.
 *
 * A new request cancels the pending switch, so rapid clicks never open a stale menu.
 * The transition owns layout height and clipping; no second parent size animation competes with it.
 *
 * @param transportTypes Catalogue used to resolve the dropdown title.
 * @param requestedTransportId Requested open mode, or null to close all menus.
 * @param selectedZonesByTransport Independent zone selections, unchanged by expansion.
 * @param onToggleZone Requests a selection change for a transport and zone ID.
 * @param scale Responsive size multiplier.
 * @param enabled Whether zone choices can change.
 */
@Composable
private fun TransitZoneDropdown(
    transportTypes: List<TransportType>,
    requestedTransportId: String?,
    selectedZonesByTransport: Map<String, List<String>>,
    onToggleZone: (String, String) -> Unit,
    scale: Float,
    enabled: Boolean
) {
    val requestedMode = transportTypes.firstOrNull { it.usesRoutes && it.id == requestedTransportId }
    var displayedTransportId by remember { mutableStateOf<String?>(null) }
    val visibility = remember { MutableTransitionState(false) }

    LaunchedEffect(requestedMode?.id) {
        if (displayedTransportId != requestedMode?.id) {
            visibility.targetState = false
            snapshotFlow { visibility.isIdle && !visibility.currentState }.first { it }
            displayedTransportId = requestedMode?.id
        }
        visibility.targetState = requestedMode != null
    }

    AnimatedVisibility(
        visibleState = visibility,
        enter = preferenceDropdownEnter(),
        exit = preferenceDropdownExit()
    ) {
        transportTypes.firstOrNull { it.usesRoutes && it.id == displayedTransportId }?.let { transport ->
            PreferenceBand(TransitHighlight, scale, Modifier.heightIn(min = ZoneDropdownMinimumHeight * scale)) {
                BandTitle("${transport.label} zones", TransitComplementary, scale)
                Text("Choose the zones for ${transport.label}.", color = TransitComplementary,
                    fontSize = 13.sp * scale, lineHeight = 18.sp * scale)
                ChoiceGrid(MainPlannerViewModel.zoneIds, selectedZonesByTransport[transport.id].orEmpty(),
                    enabled && requestedMode?.id == transport.id && visibility.targetState,
                    { onToggleZone(transport.id, it) }, scale, buttonWidthFraction = ZoneButtonWidthFraction) { "ZONE $it" }
            }
        }
    }
}

/** A full-width Figma band with 24 px side padding and 16 px vertical padding.
 * @param color Band background colour.
 * @param scale Responsive size multiplier.
 * @param modifier Optional band constraints.
 * @param content Preference controls in an 8 px spaced column.
 */
@Composable
private fun PreferenceBand(color: Color, scale: Float, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(color)
        .padding(horizontal = PreferenceBandHorizontalPadding * scale, vertical = PreferenceBandVerticalPadding * scale),
        verticalArrangement = Arrangement.spacedBy(PreferenceChoiceSpacing * scale), content = content)
}

/** Figma band heading.
 * @param title Visible heading.
 * @param color Foreground colour for the band.
 * @param scale Responsive size multiplier.
 */
@Composable
private fun BandTitle(title: String, color: Color, scale: Float) {
    Text(title, color = color, fontSize = 24.sp * scale, lineHeight = 34.sp * scale,
        fontWeight = FontWeight.Bold)
}

/** Three-column choices; missing cells keep equal widths.
 * @param ids Supported selection identifiers.
 * @param selected Currently selected identifiers.
 * @param enabled Whether choices can change.
 * @param onToggle Requests a selection change.
 * @param scale Responsive size multiplier.
 * @param buttonWidthFraction Fraction of each responsive cell occupied by the button, from 0 to 1.
 * @param label Converts an identifier to its visible label.
 */
@Composable
private fun ChoiceGrid(ids: List<String>, selected: List<String>, enabled: Boolean,
    onToggle: (String) -> Unit, scale: Float,
    buttonWidthFraction: Float = AttractionButtonWidthFraction, label: (String) -> String = { it }) {
    ids.chunked(PreferenceGridColumnCount).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PreferenceChoiceSpacing * scale)) {
            row.forEach { id ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    PlannerChoice(label(id), id in selected, enabled, { onToggle(id) }, scale,
                        Modifier.fillMaxWidth(buttonWidthFraction))
                }
            }
            repeat(PreferenceGridColumnCount - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Accessible 44 px Figma choice; its row supplies the responsive width.
 * @param label Visible label, including selected zones for enabled transit modes.
 * @param selected Whether the choice is selected.
 * @param enabled Whether interaction is allowed.
 * @param onClick Requests a selection change.
 * @param scale Responsive size multiplier.
 * @param modifier Layout supplied by the row.
 * @param role Checkbox for multi-select choices, radio button for walking style.
 * @param dropdownExpanded Null for ordinary choices; otherwise controls the transit expansion indicator.
 */
@Composable
private fun PlannerChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit,
    scale: Float, modifier: Modifier = Modifier, role: Role = Role.Checkbox,
    dropdownExpanded: Boolean? = null) {
    val expanded = dropdownExpanded == true
    val animateTransportFill = dropdownExpanded != null
    val selectionFill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(if (animateTransportFill) TRANSPORT_SELECTION_FILL_ANIMATION_DURATION_MILLIS else 0,
            easing = FastOutSlowInEasing),
        label = "Transport selection radial fill"
    )
    Surface(
        modifier = modifier.height(PreferenceChoiceHeight * scale)
            .then(if (dropdownExpanded == null) {
                Modifier.toggleable(selected, enabled = enabled, role = role, onValueChange = { onClick() })
            } else {
                Modifier.clickable(enabled = enabled, role = Role.Button,
                    onClickLabel = if (dropdownExpanded) "Close zone choices" else "Open zone choices",
                    onClick = onClick)
                    .semantics {
                        stateDescription = (if (selected) "Included" else "Not included") +
                            (if (dropdownExpanded) ", zones expanded" else ", zones collapsed")
                    }
            }),
        color = if (animateTransportFill) TransitWhite else if (selected) TransitSelected else TransitWhite,
        contentColor = if (selected || expanded) TransitComplementary else TransitMain,
        shape = RoundedCornerShape(PreferenceChoiceCornerRadius * scale)
    ) {
        Box(Modifier.fillMaxSize()
            .then(if (animateTransportFill) Modifier.drawBehind {
                // Shrink the white centre so lime advances inward from the borders.
                drawRect(TransitSelected)
                if (selectionFill < 1f) {
                    drawCircle(TransitWhite,
                        radius = hypot(size.width, size.height) / 2f * (1f - selectionFill),
                        center = size.center)
                }
            } else Modifier)
            .then(if (expanded && !selected) Modifier.drawWithCache {
                val highlight = Brush.radialGradient(
                    *TransitOpenChoiceGradientStops,
                    center = size.center,
                    radius = size.height / 2f
                )
                // Figma uses a centred ellipse spanning the entire chip, rather than a small spot.
                onDrawBehind {
                    withTransform({ scale(size.width / size.height, 1f, size.center) }) {
                        drawRect(highlight)
                    }
                }
            } else Modifier)
            .padding(horizontal = PreferenceChoiceHorizontalPadding * scale, vertical = PreferenceChoiceVerticalPadding * scale),
            contentAlignment = Alignment.Center) {
            Text(label, fontSize = 14.sp * scale, lineHeight = 20.sp * scale,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}
