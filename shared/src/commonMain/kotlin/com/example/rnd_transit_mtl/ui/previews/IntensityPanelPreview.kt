package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.IntensityPanel
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

@Preview(name = "Intensity — empty", widthDp = 390, heightDp = 120)
@Composable
fun EmptyIntensityPreview() = IntensityExample(0f)

@Preview(name = "Intensity — 1 percent", widthDp = 390, heightDp = 120)
@Composable
fun TinyIntensityPreview() = IntensityExample(1f)

@Preview(name = "Intensity — 11 percent", widthDp = 390, heightDp = 120)
@Composable
fun LowIntensityPreview() = IntensityExample(11f)

@Preview(name = "Intensity — full", widthDp = 390, heightDp = 120)
@Composable
fun FullIntensityPreview() = IntensityExample(100f)

@Composable
private fun IntensityExample(value: Float) {
    RNDTransitTheme {
        IntensityPanel(
            intensity = value,
            onIntensityChange = {},
            validationMessage = "",
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
    }
}
