package com.example.rnd_transit_mtl.ui.global_ui_ellements.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.data.location.LocalPlannerLocation
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TransitOutlineButton
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary

// Layout settings — dp spacing and button height for the Settings location-accuracy section.
private val LocationAccuracyEntrySpacing = 6.dp
private val LocationAccuracyEntryButtonHeight = 48.dp

/**
 * Settings section opening the shared live location-accuracy diagnostics dialog
 * without starting another location source.
 */
@Composable
internal fun LocationAccuracySettingsEntry() {
    val location = LocalPlannerLocation.current
    var opened by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(LocationAccuracyEntrySpacing)) {
        Text("Location accuracy", color = TransitComplementary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            location?.accuracyMonitor?.state?.let(::locationPermissionLabel) ?: "Live location is available on Android.",
            color = TransitComplementary,
            fontSize = 14.sp,
        )
        TransitOutlineButton(
            "Improve location accuracy",
            onClick = { opened = true },
            height = LocationAccuracyEntryButtonHeight,
            fontSize = 15.sp,
        )
    }
    if (opened) LocationAccuracyDialog(location) { opened = false }
}
