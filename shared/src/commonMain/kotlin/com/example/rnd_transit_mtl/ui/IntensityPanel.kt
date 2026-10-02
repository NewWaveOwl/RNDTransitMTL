package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight

/** Renders the intensity heading, interactive slider, and validation output. */
@Composable
internal fun IntensityPanel(
    layoutScale: Float = 1f,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    validationMessage: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(TransitHighlight)
            .padding(start = 30.dp * layoutScale, end = 40.dp * layoutScale, top = 4.dp * layoutScale, bottom = 8.dp * layoutScale),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("The attraction intensity", color = TransitWhite, fontSize = 26.sp * layoutScale, maxLines = 1)
        Spacer(Modifier.height(5.dp * layoutScale))
        IntensitySlider(
            layoutScale = layoutScale,
            intensity = intensity,
            onIntensityChange = onIntensityChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp * layoutScale)
        )
        Spacer(Modifier.height(3.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            if (validationMessage.isNotEmpty()) {
                Text(
                    text = validationMessage,
                    color = TransitWhite,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Draws the custom intensity slider and places its value inside the progress fill. */
@Composable
private fun IntensitySlider(
    layoutScale: Float,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = (intensity / 100f).coerceIn(0f, 1f)
    val sliderShape = RoundedCornerShape(32.dp * layoutScale)

    Box(
        modifier = modifier
            .background(TransitMain, sliderShape)
            .pointerInput(onIntensityChange) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onIntensityChange((down.position.x / size.width * 100f).coerceIn(0f, 100f))
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        onIntensityChange(
                            (change.position.x / size.width * 100f).coerceIn(0f, 100f)
                        )
                        change.consume()
                    } while (event.changes.any { it.pressed })
                }
            }
            .semantics { contentDescription = "Attraction intensity" }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(TransitSelected, sliderShape)
        ) {
            if (progress >= 0.18f) {
                Text(
                    text = intensity.toInt().toString(),
                    color = TransitWhite,
                    fontSize = 28.sp * layoutScale,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp * layoutScale)
                )
            }
        }
        if (progress < 0.18f) {
            Text(
                text = intensity.toInt().toString(),
                color = TransitWhite,
                fontSize = 28.sp * layoutScale,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
