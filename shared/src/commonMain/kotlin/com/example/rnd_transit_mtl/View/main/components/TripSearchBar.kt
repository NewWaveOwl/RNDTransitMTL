package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.*

/** Bike selector proportions and palette, without gestures or a fictional percentage. */
@Composable
internal fun TripSearchBar(modifier: Modifier = Modifier) {
    val transition=rememberInfiniteTransition(label="Trip search")
    val position by transition.animateFloat(0f, 0.62f,
        infiniteRepeatable(tween(1400, easing=FastOutSlowInEasing), RepeatMode.Reverse),
        label="Search sweep")
    BoxWithConstraints(modifier.fillMaxWidth().height(64.dp)
        .clip(RoundedCornerShape(18.dp)).background(TransitWhite)
        .semantics { progressBarRangeInfo=ProgressBarRangeInfo.Indeterminate }) {
        Box(Modifier.offset(x=maxWidth*position).fillMaxWidth(0.38f).fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(TransitMain,TransitSelected))))
    }
}
