package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.IntensityPanel
import com.example.rnd_transit_mtl.ui.theme.*

/**
 * Adds the Figma preference bands in scroll order; intensity is always the final band.
 *
 * @param scale Responsive size multiplier relative to the 412 px Figma width.
 * @param transportTypes Loaded transport catalogue.
 * @param selectedTransportIds Enabled transport IDs.
 * @param selectedZonesByTransport Independently selected zone IDs for each transit mode.
 * @param onToggleTransport Requests a transport toggle.
 * @param onToggleZone Requests a zone toggle for the supplied transport ID and zone ID.
 * @param walkingPreference Selected walking style.
 * @param selectedAttractionIds Enabled attraction labels.
 * @param onWalkingPreferenceChange Requests a walking-style change.
 * @param onToggleAttraction Requests an attraction toggle.
 * @param intensity Attraction intensity from 0 to 100.
 * @param onIntensityChange Requests an intensity change.
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
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    enabled: Boolean,
    onOpenDetailedSettings: () -> Unit,
    expandedTransportId: String?,
    onExpandedTransportChange: (String) -> Unit
) {
    item(key = "transport") {
        // Animate the dropdown itself so following bands move with its visible height.
        Column(Modifier.fillMaxWidth().background(TransitMain)) {
            PreferenceBand(TransitMain, scale) {
                Text("Types of transport", color = TransitWhite, fontSize = 20.sp * scale,
                    lineHeight = 28.sp * scale, fontWeight = FontWeight.Bold)
                val order = listOf("walk", "bike", "bus", "metro", "train", "rem")
                val ordered = transportTypes.sortedBy { order.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                ordered.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                        row.forEach { transport ->
                            val selected = transport.id in selectedTransportIds
                            val title = if (transport.id == "walk") "Walk" else transport.label
                            val selectedZoneIds = selectedZonesByTransport[transport.id].orEmpty()
                            val zones = if (selected && transport.usesRoutes && selectedZoneIds.isNotEmpty())
                                " " + selectedZoneIds.sorted().joinToString(", ") else ""
                            PlannerChoice(title + zones, selected, enabled, {
                                if (transport.usesRoutes) onExpandedTransportChange(transport.id)
                                else onToggleTransport(transport.id)
                            }, scale, Modifier.weight(1f),
                                dropdownExpanded = if (transport.usesRoutes) expandedTransportId == transport.id else null)
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
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
    item(key = "walking") {
        AnimatedVisibility(
            visible = "walk" in selectedTransportIds,
            enter = preferenceDropdownEnter(),
            exit = preferenceDropdownExit()
        ) {
            PreferenceBand(TransitComplementary, scale) {
                BandTitle("Walking preferences", TransitWhite, scale)
                Text("Choose the type of walk.", color = TransitWhite, fontSize = 13.sp * scale,
                    lineHeight = 18.sp * scale)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                    MainPlannerViewModel.walkingPreferences.forEach { preference ->
                        PlannerChoice(preference, preference == walkingPreference, enabled && "walk" in selectedTransportIds,
                            { onWalkingPreferenceChange(preference) }, scale, Modifier.weight(1f), Role.RadioButton)
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
            Text("Sample routes don’t yet reflect walking or attraction preferences.",
                color = TransitWhite, style = MaterialTheme.typography.bodySmall)
        }
    }
    item(key = "settings") {
        PreferenceBand(TransitComplementary, scale) {
            TextButton(onClick = onOpenDetailedSettings, enabled = enabled,
                modifier = Modifier.fillMaxWidth()) {
                Text("More preferences in Settings", color = TransitWhite)
            }
        }
    }
    item(key = "intensity") {
        IntensityPanel(
            layoutScale = scale,
            intensity = intensity,
            onIntensityChange = { if (enabled) onIntensityChange(it) },
            validationMessage = "",
            modifier = Modifier.fillMaxWidth().height(160.dp * scale)
        )
    }
}

/** Duration of each opening or closing slide. Switching menus completes both phases. */
internal const val TRANSPORT_DROPDOWN_ANIMATION_MILLIS = 500

/** Shared opening motion for walking preferences and transit-zone dropdowns. */
private fun preferenceDropdownEnter() = expandVertically(
    animationSpec = tween(TRANSPORT_DROPDOWN_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
    expandFrom = Alignment.Top
)

/** Shared closing motion; content remains mounted and clipped while its height shrinks. */
private fun preferenceDropdownExit() = shrinkVertically(
    animationSpec = tween(TRANSPORT_DROPDOWN_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
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
            PreferenceBand(TransitHighlight, scale, Modifier.heightIn(min = 203.dp * scale)) {
                BandTitle("${transport.label} zones", TransitComplementary, scale)
                Text("Choose the zones for ${transport.label}.", color = TransitComplementary,
                    fontSize = 13.sp * scale, lineHeight = 18.sp * scale)
                ChoiceGrid(MainPlannerViewModel.zoneIds, selectedZonesByTransport[transport.id].orEmpty(),
                    enabled && requestedMode?.id == transport.id && visibility.targetState,
                    { onToggleZone(transport.id, it) }, scale) { "ZONE $it" }
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
        .padding(horizontal = 24.dp * scale, vertical = 16.dp * scale),
        verticalArrangement = Arrangement.spacedBy(8.dp * scale), content = content)
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
 * @param label Converts an identifier to its visible label.
 */
@Composable
private fun ChoiceGrid(ids: List<String>, selected: List<String>, enabled: Boolean,
    onToggle: (String) -> Unit, scale: Float, label: (String) -> String = { it }) {
    ids.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            row.forEach { id ->
                PlannerChoice(label(id), id in selected, enabled, { onToggle(id) }, scale, Modifier.weight(1f))
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
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
    Surface(
        modifier = modifier.height(44.dp * scale)
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
                    .then(if (dropdownExpanded) Modifier.border(1.dp, TransitComplementary,
                        RoundedCornerShape(14.dp * scale)) else Modifier)
            }),
        color = if (selected) TransitSelected else TransitWhite,
        contentColor = if (selected) TransitComplementary else TransitMain,
        shape = RoundedCornerShape(14.dp * scale)
    ) {
        Box(Modifier.padding(horizontal = 8.dp * scale, vertical = 10.dp * scale),
            contentAlignment = Alignment.Center) {
            Text(label, fontSize = 14.sp * scale, lineHeight = 20.sp * scale,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}
