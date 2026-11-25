package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    enabled: Boolean = true,
) {
    val scale = if (layoutScale.isFinite() && layoutScale > 0f) layoutScale else 1f
    val boundedValue = if (intensity.isFinite()) intensity.coerceIn(0f, 100f) else 0f

    Column(
        modifier = modifier
            .background(TransitComplementary)
            .padding(
                horizontal = IntensityHorizontalPadding * scale,
                vertical = IntensityVerticalPadding * scale,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Attraction intensity",
            color = TransitWhite,
            fontSize = IntensityTitleFontSize * scale,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.alpha(if (enabled) 1f else IntensityDisabledAlpha),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(IntensityTrackHeight * scale)
                .semantics { contentDescription = "Attraction intensity" },
        )

        if (validationMessage.isNotBlank()) {
            Spacer(Modifier.height(IntensityFeedbackGap * scale))
            Text(
                text = validationMessage,
                color = TransitWhite,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Draws the white track, proportional blue fill, and numeric value.
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
    modifier: Modifier = Modifier,
) {
    val progress = value / 100f
    val shape = RoundedCornerShape(IntensityTrackCornerRadius * scale)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(IntensityTrackHeight * scale)
            .clip(shape)
            .background(TransitWhite)
            // Slider already exposes the value and range to accessibility.
            .clearAndSetSemantics {},
    ) {
        val fillWidth = maxWidth * progress
        val labelWidth = (IntensityValueWidth * scale).coerceAtMost(maxWidth)
        val labelInset = IntensityValueInset * scale
        val labelFitsInsideFill = fillWidth >= labelWidth + labelInset * 2
        val requestedOffset = if (labelFitsInsideFill) {
            fillWidth - labelWidth - labelInset
        } else {
            fillWidth + labelInset
        }
        val maximumOffset = (maxWidth - labelWidth).coerceAtLeast(0.dp)
        val labelOffset = requestedOffset.coerceIn(0.dp, maximumOffset)

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(fillWidth)
                .fillMaxHeight()
                .background(TransitMain, shape),
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = labelOffset)
                .width(labelWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.roundToInt().toString(),
                color = if (labelFitsInsideFill) TransitWhite else TransitMain,
                fontSize = IntensityValueFontSize * scale,
                maxLines = 1,
            )
        }
    }
}
