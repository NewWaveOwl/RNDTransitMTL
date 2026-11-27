package com.example.rnd_transit_mtl.View.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.global_ui_ellements.location.LocationAccuracyNotice
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — reference dp insets scaled by the planner's responsive factor.
internal val PlannerOriginFieldTopPadding = 16.dp
private val PlannerOriginFieldHorizontalPadding = 16.dp
private val OriginStatusHorizontalPadding = 8.dp
private val OriginStatusVerticalPadding = 4.dp
private val OriginStatusCornerRadius = 4.dp
private val OriginAccuracyNoticeTopGap = 8.dp

// Animation settings — milliseconds for the starting-point field slide and fade transition.
private const val PLANNER_ORIGIN_FIELD_ANIMATION_DURATION_MILLIS = 300

/**
 * Animated starting-point field, origin availability status pill, and inline accuracy notice.
 *
 * @param visible Whether the sheet position leaves sufficient map height to show the field.
 * @param scale Responsive planner width scale factor (`0.7f..1.5f`).
 * @param originValue Formatted starting-point label displayed inside the field.
 * @param originStatus Optional automatic-origin availability message shown beneath the field.
 * @param enabled Whether tapping the starting-point field or accuracy notice is permitted.
 * @param onEditOrigin Opens the origin selection options without mutating saved choices.
 * @param onMeasuredHeight Reports the rendered height in pixels for map camera top-inset calculation.
 */
@Composable
internal fun PlannerOriginOverlay(
    visible: Boolean,
    scale: Float,
    originValue: String,
    originStatus: String?,
    enabled: Boolean,
    onEditOrigin: () -> Unit,
    onMeasuredHeight: (Int) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(PLANNER_ORIGIN_FIELD_ANIMATION_DURATION_MILLIS),
            initialOffsetY = { -it },
        ) + fadeIn(tween(PLANNER_ORIGIN_FIELD_ANIMATION_DURATION_MILLIS)),
        exit = slideOutVertically(
            animationSpec = tween(PLANNER_ORIGIN_FIELD_ANIMATION_DURATION_MILLIS),
            targetOffsetY = { -it },
        ) + fadeOut(tween(PLANNER_ORIGIN_FIELD_ANIMATION_DURATION_MILLIS)),
        modifier = Modifier
            .padding(horizontal = PlannerOriginFieldHorizontalPadding * scale)
            .offset(y = PlannerOriginFieldTopPadding * scale),
    ) {
        Column(Modifier.onSizeChanged { onMeasuredHeight(it.height) }) {
            TripLabelValueField(
                label = "Starting point",
                value = originValue,
                showLocationIcon = true,
                modifier = Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = "Choose starting point",
                    onClick = onEditOrigin,
                ),
            )
            if (originStatus != null) {
                Surface(color = TransitWhite, shape = RoundedCornerShape(OriginStatusCornerRadius)) {
                    Text(
                        originStatus,
                        modifier = Modifier.padding(
                            horizontal = OriginStatusHorizontalPadding,
                            vertical = OriginStatusVerticalPadding,
                        ),
                        color = TransitComplementary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            LocationAccuracyNotice(Modifier.padding(top = OriginAccuracyNoticeTopGap), enabled = enabled)
        }
    }
}
