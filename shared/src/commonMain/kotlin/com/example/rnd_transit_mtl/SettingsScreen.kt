package com.example.rnd_transit_mtl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.PlaceholderCard
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Displays settings placeholders and a shortcut back to the trip planner.
 *
 * Theme and language cards are static placeholders. Trip Settings returns
 * to the last Home entry already present in the shared back stack.
 */
@Composable
fun SettingsScreen() {
    /** Use the application navigator to return to an existing planner destination. */
    val navigator = LocalNavigator.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        /** Adapt the reference layout to the available width without excessive scaling. */
        val layoutScale = (maxWidth.value / 402f).coerceIn(0.7f, 1.4f)

        Column(Modifier.fillMaxSize().background(TransitMain)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp * layoutScale)
            ) {
                /** Remove destinations above the last Home entry; leave the stack unchanged if Home is absent. */
                TextButton(
                    onClick = { navigator.popUntil(MainScreenKey) },
                    modifier = Modifier.fillMaxWidth().height(88.dp * layoutScale).background(TransitComplementary)
                ) {
                    Text("TRIP SETTINGS", color = TransitWhite, fontSize = 28.sp * layoutScale, maxLines = 1)
                }

                PlaceholderCard("THEME", layoutScale)
                PlaceholderCard("LANGUAGE", layoutScale)
            }
        }
    }
}