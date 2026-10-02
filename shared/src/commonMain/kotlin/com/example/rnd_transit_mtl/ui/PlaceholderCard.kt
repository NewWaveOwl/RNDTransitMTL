package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Displays a labeled placeholder card in Settings or History.
 *
 * The card provides static placeholder content; it has no interaction or local state.
 *
 * @param label Text displayed inside the card.
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 */
@Composable
internal fun PlaceholderCard(label: String, layoutScale: Float) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(end = 24.dp * layoutScale)
            .height(88.dp * layoutScale).background(TransitComplementary)
            .padding(horizontal = 24.dp * layoutScale),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = TransitWhite, fontSize = 28.sp * layoutScale)
    }
}