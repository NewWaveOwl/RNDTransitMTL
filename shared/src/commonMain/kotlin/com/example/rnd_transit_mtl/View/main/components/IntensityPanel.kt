package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.roundToInt

private val IntensitySliderCornerRadius = 24.dp
private val IntensityValueEndPadding = 24.dp

// Layout settings — dimensions use reference dp multiplied by layoutScale.
private val IntensityHorizontalPadding = 24.dp
private val IntensityVerticalPadding = 16.dp
private val IntensityTitleGap = 12.dp
private val IntensityTrackHeight = 64.dp
private val IntensityTrackCornerRadius = 24.dp
private val IntensityValueWidth = 64.dp
private val IntensityValueInset = 8.dp
private val IntensityFeedbackGap = 8.dp
private val IntensityTitleFontSize = 20.sp
private val IntensityValueFontSize = 26.sp
private const val IntensityDisabledAlpha = 0.45f

/**
 * Displays the Figma attraction-intensity control.
 *
 * @param layoutScale Positive responsive scale used by the preference bands.
 * @param intensity Current attraction intensity from 0 to 100.
 * @param onIntensityChange Requests a new value; the caller owns state.
 * @param validationMessage Optional validation feedback.
 * @param modifier Layout supplied by the caller.
 * @param enabled Whether the user can change intensity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IntensityPanel(
    layoutScale: Float = 1f,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    validationMessage: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scale = if (
        layoutScale.isFinite() && layoutScale > 0f
    ) {
        layoutScale
    } else {
        1f
    }

    val boundedValue = if (intensity.isFinite()) {
        intensity.coerceIn(0f, 100f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .background(TransitComplementary)
            .padding(
                horizontal = IntensityHorizontalPadding * scale,
                vertical = IntensityVerticalPadding * scale
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Attraction intensity",
            color = TransitWhite,
            fontSize = IntensityTitleFontSize * scale,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(IntensityTitleGap * scale))

        Slider(
            value = boundedValue,
            onValueChange = { value ->
                if (value.isFinite()) {
                    onIntensityChange(value.coerceIn(0f, 100f))
                }
            },
            enabled = enabled,
            valueRange = 0f..100f,
            steps = 0,
            thumb = {},
            track = { _ ->
                IntensityTrack(
                    value = boundedValue,
                    scale = scale,
                    modifier = Modifier.alpha(
                        if (enabled) 1f else IntensityDisabledAlpha
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(IntensityTrackHeight * scale)
                .semantics {
                    contentDescription = "Attraction intensity"
                }
        )

        if (validationMessage.isNotBlank()) {
            Spacer(Modifier.height(IntensityFeedbackGap * scale))

            Text(
                text = validationMessage,
                color = TransitWhite,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Draws the white track, proportional blue fill, and numeric value.
 *
 * At low values the label sits in the white section for readability.
 *
 * @param value Bounded intensity from 0 to 100.
 * @param scale Positive scale for dimensions and text.
 * @param modifier Appearance supplied by the slider.
 */
@Composable
private fun IntensityTrack(
    value: Float,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val progress = value / 100f
    val shape = RoundedCornerShape(
        IntensityTrackCornerRadius * scale
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(IntensityTrackHeight * scale)
            .clip(shape)
            .background(TransitWhite)
            // Slider already exposes the value and range to accessibility.
            .clearAndSetSemantics {}
    ) {
        val fillWidth = maxWidth * progress
        val labelWidth =
            (IntensityValueWidth * scale).coerceAtMost(maxWidth)
        val labelInset = IntensityValueInset * scale

        val labelFitsInsideFill =
            fillWidth >= labelWidth + labelInset * 2

        val requestedOffset = if (labelFitsInsideFill) {
            fillWidth - labelWidth - labelInset
        } else {
            fillWidth + labelInset
        }

        val maximumOffset =
            (maxWidth - labelWidth).coerceAtLeast(0.dp)

        val labelOffset = requestedOffset.coerceIn(
            minimumValue = 0.dp,
            maximumValue = maximumOffset
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(fillWidth)
                .fillMaxHeight()
                .background(TransitMain, shape)
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = labelOffset)
                .width(labelWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value.roundToInt().toString(),
                color = if (labelFitsInsideFill) {
                    TransitWhite
                } else {
                    TransitMain
                },
                fontSize = IntensityValueFontSize * scale,
                maxLines = 1
            )
        }
    }
}

/**
 * Displays and updates intensity while keeping the value readable at low progress.
 *
 * Horizontal pointer positions map to the supplied value range. The numeric label
 * moves outside the filled section when progress is too low to contain it.
 *
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 * @param intensity Current value within valueRange.
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
    trackColor: Color = TransitSelected
) {
    val onValueChange by rememberUpdatedState(onIntensityChange)
    /** Convert the supplied range to a bounded fraction for the filled section width. */
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
                    /** Set intensity immediately on touch-down, so a tap also selects a value. */
                    val down = awaitFirstDown()
                    onValueChange(valueAt(down.position.x))
                    /**
                     * Continue updating from the first pointer in each event while any pointer is pressed.
                     * Clamp positions outside the slider to 0 or 100 and consume each handled change.
                     */
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        onValueChange(
                            valueAt(change.position.x)
                        )
                        change.consume()
                    } while (event.changes.any { it.pressed })
                }
            }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(intensity.coerceIn(valueRange), valueRange)
                if (enabled) setProgress { value ->
                    if (value.isFinite()) onIntensityChange(value.coerceIn(valueRange))
                    value.isFinite()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .then(if (roundFilledEnd) Modifier.clip(sliderShape) else Modifier)
                .background(TransitMain)
        ) {
            /** At 18% or above, place the value near the end of the filled section. */
            if (progress >= 0.18f) {
                Text(
                    text = valueText,
                    color = TransitWhite,
                    fontSize = valueFontSizeSp.sp * layoutScale,
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .padding(end = IntensityValueEndPadding * layoutScale)
                )
            }
        }
        /** Below 18%, center the value on the full track so the narrow fill does not obscure it. */
        if (progress < 0.18f) {
            Text(
                text = valueText,
                color = TransitComplementary,
                fontSize = valueFontSizeSp.sp * layoutScale,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
