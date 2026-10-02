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

/**
 * Displays the attraction-intensity slider and trip-validation feedback.
 *
 * The parent owns the intensity value and validation message; slider gestures
 * request intensity changes through the supplied callback.
 *
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 * @param intensity Current attraction intensity on the 0 to 100 scale.
 * @param onIntensityChange Receives the intensity chosen by tapping or dragging the slider.
 * @param validationMessage Validation message to display, or an empty string when there is no error.
 * @param modifier Layout and appearance modifiers supplied by the parent.
 */
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
        /** Keep feedback space in the layout and show its text only when a validation message exists. */
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

/**
 * Displays and updates intensity while keeping the value readable at low progress.
 *
 * Horizontal pointer positions map to values from 0 to 100. The numeric label
 * moves outside the filled section when progress is too low to contain it.
 *
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 * @param intensity Current attraction intensity on the 0 to 100 scale.
 * @param onIntensityChange Receives the intensity chosen by tapping or dragging the slider.
 * @param modifier Layout and appearance modifiers supplied by the parent.
 */
@Composable
private fun IntensitySlider(
    layoutScale: Float,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    /** Convert the intensity percentage to a bounded fraction for the filled section width. */
    val progress = (intensity / 100f).coerceIn(0f, 1f)
    val sliderShape = RoundedCornerShape(32.dp * layoutScale)

    Box(
        modifier = modifier
            .background(TransitMain, sliderShape)
            .pointerInput(onIntensityChange) {
                awaitEachGesture {
                    /** Set intensity immediately on touch-down, so a tap also selects a value. */
                    val down = awaitFirstDown()
                    onIntensityChange((down.position.x / size.width * 100f).coerceIn(0f, 100f))
                    /**
                     * Continue updating from the first pointer in each event while any pointer is pressed.
                     * Clamp positions outside the slider to 0 or 100 and consume each handled change.
                     */
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
            /** At 18% or above, place the value near the end of the filled section. */
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
        /** Below 18%, center the value on the full track so the narrow fill does not obscure it. */
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