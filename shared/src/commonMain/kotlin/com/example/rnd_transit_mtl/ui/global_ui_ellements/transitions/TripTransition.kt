package com.example.rnd_transit_mtl.ui.global_ui_ellements.transitions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
internal fun Modifier.tripPanelEntrance(enabled: Boolean, fromBottom: Boolean = true): Modifier {
    if (!enabled) return this

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(250))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (if (fromBottom) 20.dp else (-20).dp).toPx() * (1f - progress.value)
    }
}

// Shared animation settings — edit here rather than copying this duration into individual screens.
/** Keeps planner exits, map navigation and the trip footer entrance in sync; larger values mean slower motion. */
internal const val GO_TRIP_TRANSITION_MILLIS = 1_000
