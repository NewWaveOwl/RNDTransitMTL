package com.example.rnd_transit_mtl.layout

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
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

@Composable
internal fun PageTitle(title: String, layoutScale: Float, highlighted: Boolean) {
    Box(
        Modifier.fillMaxWidth().height(58.dp * layoutScale)
            .background(if (highlighted) TransitHighlight else TransitMain)
            .padding(horizontal = 12.dp * layoutScale),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(title, color = TransitWhite, fontSize = (if (highlighted) 44.sp else 26.sp) * layoutScale)
    }
}
