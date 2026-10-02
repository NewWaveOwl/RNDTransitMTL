package com.example.rnd_transit_mtl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.PlaceholderCard
import com.example.rnd_transit_mtl.ui.theme.TransitMain

/**
 * Displays the sample TRIP A, TRIP C, and TRIP D history cards.
 *
 * These are static placeholders, separate from the planner's saved trip summaries.
 */
@Composable
fun HistoryScreen() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        /** Adapt card sizes and spacing to the available width using the reference layout. */
        val layoutScale = (maxWidth.value / 402f).coerceIn(0.7f, 1.4f)

        Column(Modifier.fillMaxSize().background(TransitMain)) {
            /** Allow the placeholder list to scroll on smaller displays. */
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp * layoutScale)
            ) {
                PlaceholderCard("TRIP A", layoutScale)
                PlaceholderCard("TRIP C", layoutScale)
                PlaceholderCard("TRIP D", layoutScale)
            }
        }
    }
}