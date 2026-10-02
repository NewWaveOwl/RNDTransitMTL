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

/** Settings navigation shell; theme and language controls are reserved for later work. */
@Composable
fun SettingsScreen() {
    val navigator = LocalNavigator.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layoutScale = (maxWidth.value / 402f).coerceIn(0.7f, 1.4f)
        Column(Modifier.fillMaxSize().background(TransitMain)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp * layoutScale)
            ) {
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
