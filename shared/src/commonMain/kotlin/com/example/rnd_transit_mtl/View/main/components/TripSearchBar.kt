package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.*
import kotlinx.coroutines.isActive

private const val SearchSweepNanos = 1_400_000_000L
private const val SearchCycleNanos = SearchSweepNanos * 2

internal fun advanceSearchPhase(phaseNanos: Long, deltaNanos: Long): Long =
    (phaseNanos + deltaNanos.coerceAtLeast(0L) % SearchCycleNanos) % SearchCycleNanos

/** Bike selector proportions and palette, without gestures or a fictional percentage. */
@Composable
internal fun TripSearchBar(modifier: Modifier = Modifier) {
    var phaseNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var previousFrame = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frame ->
                val deltaNanos = (frame - previousFrame).coerceAtLeast(0L)
                previousFrame = frame
                phaseNanos = advanceSearchPhase(phaseNanos, deltaNanos)
            }
        }
    }
    val sweepBrush = remember { Brush.horizontalGradient(listOf(TransitMain, TransitSelected)) }
    Box(modifier.fillMaxWidth().height(64.dp)
        .clip(RoundedCornerShape(18.dp)).background(TransitWhite)
        .semantics { progressBarRangeInfo=ProgressBarRangeInfo.Indeterminate }) {
        Canvas(Modifier.fillMaxSize()) {
            // Read frame state only in drawing, avoiding per-frame composition/layout.
            val phase = phaseNanos.toFloat() / SearchSweepNanos
            val progress = if (phase <= 1f) phase else 2f - phase
            val position = FastOutSlowInEasing.transform(progress) * 0.62f
            val left = size.width * position
            val sweepWidth = size.width * 0.38f
            withTransform({ translate(left, 0f) }) {
                drawRoundRect(
                    brush = sweepBrush,
                    topLeft = Offset.Zero,
                    size = Size(sweepWidth, size.height),
                    cornerRadius = CornerRadius(18.dp.toPx())
                )
            }
        }
    }
}
