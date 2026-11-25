package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.rnd_transit_mtl.model.geo.GeoPoint

/** Smooths accepted display changes only; animation frames never become GPS or progress. */
@Composable
internal fun rememberAnimatedMapPosition(target: GeoPoint?): GeoPoint? {
    var rendered by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val from = rendered
        if (target == null || from == null) {
            rendered = target
        } else if (from != target) {
            val longitudeDelta = ((target.longitude - from.longitude + 540.0) % 360.0) - 180.0
            Animatable(0f).animateTo(1f, tween(500, easing = LinearEasing)) {
                val longitude = (from.longitude + longitudeDelta * value + 540.0) % 360.0 - 180.0
                rendered = GeoPoint(from.latitude + (target.latitude - from.latitude) * value, longitude)
            }
            rendered = target
        }
    }
    return if (target == null) null else rendered ?: target
}
