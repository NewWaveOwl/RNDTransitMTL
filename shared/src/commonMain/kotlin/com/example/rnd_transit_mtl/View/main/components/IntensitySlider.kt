package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — reference dp values scaled by layoutScale; fraction threshold keeps value readable.
private val IntensitySliderCornerRadius = 24.dp
private val IntensityValueEndPadding = 24.dp
private const val IntensityInlineLabelMinimumFraction = 0.18f

/**
 * Displays and updates a filled slider while keeping its numeric value readable at low progress.
 *
 * Horizontal pointer positions map to the supplied value range. The numeric label
 * moves outside the filled section when progress is too low to contain it.
 *
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 * @param intensity Current value within [valueRange].
 * @param onIntensityChange Receives the value chosen by tapping or dragging the slider.
 * @param modifier Layout and appearance modifiers supplied by the parent.
 * @param valueRange Inclusive range used by the shared filled slider.
 * @param label Accessible control name.
 * @param valueText Text displayed within the slider.
 * @param enabled Whether pointer and accessibility actions can change the value.
 * @param valueFontSizeSp Numeric label font size before responsive scaling.
 * @param roundFilledEnd Whether the blue fill has rounded ends like the bike design.
 * @param trackColor Background colour of the unfilled slider track.
 */
@Composable
internal fun IntensitySlider(
    layoutScale: Float,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    label: String = "Attraction intensity",
    valueText: String = intensity.toInt().toString(),
    enabled: Boolean = true,
    valueFontSizeSp: Float = 28f,
    roundFilledEnd: Boolean = false,
    trackColor: Color = TransitSelected,
) {
    val onValueChange by rememberUpdatedState(onIntensityChange)
    val rangeSize = valueRange.endInclusive - valueRange.start
    val progress = ((intensity - valueRange.start) / rangeSize).coerceIn(0f, 1f)
    val sliderShape = RoundedCornerShape(IntensitySliderCornerRadius * layoutScale)

    Box(
        modifier = modifier
            .clip(sliderShape)
            .background(trackColor)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                fun valueAt(x: Float) = valueRange.start + (x / size.width).coerceIn(0f, 1f) * rangeSize
                awaitEachGesture {
                    // Set intensity immediately on touch-down so a tap also selects a value.
                    val down = awaitFirstDown()
                    onValueChange(valueAt(down.position.x))
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        onValueChange(valueAt(change.position.x))
                        change.consume()
                    } while (event.changes.any { it.pressed })
                }
            }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(intensity.coerceIn(valueRange), valueRange)
                if (enabled) {
                    setProgress { value ->
                        if (value.isFinite()) onIntensityChange(value.coerceIn(valueRange))
                        value.isFinite()
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .then(if (roundFilledEnd) Modifier.clip(sliderShape) else Modifier)
                .background(TransitMain),
        ) {
            if (progress >= IntensityInlineLabelMinimumFraction) {
                Text(
                    text = valueText,
                    color = TransitWhite,
                    fontSize = valueFontSizeSp.sp * layoutScale,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = IntensityValueEndPadding * layoutScale),
                )
            }
        }
        if (progress < IntensityInlineLabelMinimumFraction) {
            Text(
                text = valueText,
                color = TransitComplementary,
                fontSize = valueFontSizeSp.sp * layoutScale,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
