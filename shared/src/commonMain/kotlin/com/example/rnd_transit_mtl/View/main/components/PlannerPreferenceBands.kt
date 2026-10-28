package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
 * @param selectedZoneIds Selected transit zones shared by transit modes.
 * @param onToggleTransport Requests a transport toggle.
 * @param onToggleZone Requests a zone toggle.
 * @param walkingPreference Selected walking style.
 * @param selectedAttractionIds Enabled attraction labels.
 * @param onWalkingPreferenceChange Requests a walking-style change.
 * @param onToggleAttraction Requests an attraction toggle.
 * @param intensity Attraction intensity from 0 to 100.
 * @param onIntensityChange Requests an intensity change.
 * @param enabled Whether choices can change.
 * @param onOpenDetailedSettings Opens the existing Settings destination.
 */
internal fun LazyListScope.PlannerPreferenceBands(
    scale: Float,
    transportTypes: List<TransportType>,
    selectedTransportIds: List<String>,
    selectedZoneIds: List<String>,
    onToggleTransport: (String) -> Unit,
    onToggleZone: (String) -> Unit,
    walkingPreference: String,
    selectedAttractionIds: List<String>,
    onWalkingPreferenceChange: (String) -> Unit,
    onToggleAttraction: (String) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    enabled: Boolean,
    onOpenDetailedSettings: () -> Unit
) {
    item(key = "transport") {
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
                        val zones = if (selected && transport.usesRoutes && selectedZoneIds.isNotEmpty())
                            " " + selectedZoneIds.sorted().joinToString(", ") else ""
                        PlannerChoice(title + zones, selected, enabled, { onToggleTransport(transport.id) },
                            scale, Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (transportTypes.any { it.usesRoutes && it.id in selectedTransportIds }) {
        item(key = "zones") {
            PreferenceBand(TransitHighlight, scale, Modifier.heightIn(min = 203.dp * scale)) {
                BandTitle("Transit zones", TransitComplementary, scale)
                Text("Choose the zones you want to include.", color = TransitComplementary,
                    fontSize = 13.sp * scale, lineHeight = 18.sp * scale)
                ChoiceGrid(MainPlannerViewModel.zoneIds, selectedZoneIds, enabled, onToggleZone, scale) { "ZONE $it" }
            }
        }
    }
    if ("walk" in selectedTransportIds) {
        item(key = "walking") {
            PreferenceBand(TransitComplementary, scale) {
                BandTitle("Walking preferences", TransitWhite, scale)
                Text("Choose the type of walk.", color = TransitWhite, fontSize = 13.sp * scale,
                    lineHeight = 18.sp * scale)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                    MainPlannerViewModel.walkingPreferences.forEach { preference ->
                        PlannerChoice(preference, preference == walkingPreference, enabled,
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
 */
@Composable
private fun PlannerChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit,
    scale: Float, modifier: Modifier = Modifier, role: Role = Role.Checkbox) {
    Surface(
        modifier = modifier.heightIn(min = 44.dp * scale)
            .toggleable(selected, enabled = enabled, role = role, onValueChange = { onClick() }),
        color = if (selected) TransitSelected else TransitWhite,
        contentColor = if (selected) TransitComplementary else TransitMain,
        shape = RoundedCornerShape(14.dp * scale)
    ) {
        Box(Modifier.padding(horizontal = 8.dp * scale, vertical = 10.dp * scale),
            contentAlignment = Alignment.Center) {
            Text(label, fontSize = 14.sp * scale, lineHeight = 20.sp * scale, fontWeight = FontWeight.Bold)
        }
    }
}
