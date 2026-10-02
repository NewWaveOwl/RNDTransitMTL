package com.example.rnd_transit_mtl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.TeamMember
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.artiom_profile
import rnd_transit_mtl.shared.generated.resources.caio_profile
import rnd_transit_mtl.shared.generated.resources.jim_profile

/**
 * Displays the Caio, Artiom, and Jimmy photographs using the shared team-member layout.
 *
 * The team list scrolls independently of the decorative space below it.
 */
@Composable
fun AboutScreen() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        /** Scale team rows and spacing from the reference width while limiting extreme sizes. */
        val layoutScale = (maxWidth.value / 402f).coerceIn(0.7f, 1.4f)

        Column(Modifier.fillMaxSize().background(TransitHighlight)) {
            /** Give the scrollable team list the remaining height above the bottom spacer. */
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().background(TransitComplementary)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp * layoutScale, vertical = 44.dp * layoutScale),
                verticalArrangement = Arrangement.spacedBy(20.dp * layoutScale)
            ) {
                TeamMember("Caio", Res.drawable.caio_profile, layoutScale)
                TeamMember("Artiom", Res.drawable.artiom_profile, layoutScale)
                TeamMember("Jimmy", Res.drawable.jim_profile, layoutScale)
            }
            Spacer(Modifier.fillMaxWidth().height(88.dp * layoutScale))
        }
    }
}