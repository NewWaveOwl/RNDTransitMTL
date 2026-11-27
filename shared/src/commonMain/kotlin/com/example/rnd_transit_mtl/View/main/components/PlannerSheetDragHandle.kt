package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.View.main.PlannerSheetPosition
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — reference dp dimensions scaled by the planner sheet's responsive factor.
internal val PlannerDragStripHeight = 16.dp
private val PlannerDragHandleWidth = 42.dp
private val PlannerDragHandleHeight = 4.dp
private val PlannerDragHandleCornerRadius = 2.dp

/**
 * Tappable, keyboard-navigable, and screen-reader accessible drag handle for the preferences sheet.
 *
 * @param currentTarget Active or target [PlannerSheetPosition] owned by the parent sheet state.
 * @param dragStripHeight Scaled height of the interactive handle bar in dp.
 * @param scale Responsive planner width scale factor (`0.7f..1.5f`).
 * @param enabled Whether tap, keyboard, and accessibility actions are permitted.
 * @param onMoveSheet Called with the requested destination [PlannerSheetPosition].
 */
@Composable
internal fun PlannerSheetDragHandle(
    currentTarget: PlannerSheetPosition,
    dragStripHeight: Dp,
    scale: Float,
    enabled: Boolean,
    onMoveSheet: (PlannerSheetPosition) -> Unit,
) {
    fun expandSheet() {
        onMoveSheet(
            when (currentTarget) {
                PlannerSheetPosition.Collapsed -> PlannerSheetPosition.Main
                PlannerSheetPosition.Main -> PlannerSheetPosition.Routes
                PlannerSheetPosition.Routes -> PlannerSheetPosition.Preferences
                PlannerSheetPosition.Preferences -> PlannerSheetPosition.Preferences
            },
        )
    }

    fun collapseSheet() {
        onMoveSheet(
            when (currentTarget) {
                PlannerSheetPosition.Collapsed, PlannerSheetPosition.Main -> PlannerSheetPosition.Collapsed
                PlannerSheetPosition.Routes -> PlannerSheetPosition.Main
                PlannerSheetPosition.Preferences -> PlannerSheetPosition.Routes
            },
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(dragStripHeight)
            .background(TransitComplementary)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) false
                else when (event.key) {
                    Key.DirectionUp -> { expandSheet(); true }
                    Key.DirectionDown -> { collapseSheet(); true }
                    Key.MoveHome -> { onMoveSheet(PlannerSheetPosition.Collapsed); true }
                    Key.MoveEnd -> { onMoveSheet(PlannerSheetPosition.Preferences); true }
                    else -> false
                }
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Expand or collapse preferences",
            ) {
                onMoveSheet(
                    when (currentTarget) {
                        PlannerSheetPosition.Collapsed -> PlannerSheetPosition.Main
                        PlannerSheetPosition.Main -> PlannerSheetPosition.Routes
                        PlannerSheetPosition.Routes -> PlannerSheetPosition.Preferences
                        PlannerSheetPosition.Preferences -> PlannerSheetPosition.Collapsed
                    },
                )
            }
            .semantics {
                contentDescription = "Trip preferences"
                stateDescription = currentTarget.name
                customActions = listOf(
                    CustomAccessibilityAction("Expand preferences") {
                        if (enabled) expandSheet()
                        enabled
                    },
                    CustomAccessibilityAction("Collapse preferences") {
                        if (enabled) collapseSheet()
                        enabled
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(PlannerDragHandleWidth * scale, PlannerDragHandleHeight * scale)
                .background(TransitWhite, RoundedCornerShape(PlannerDragHandleCornerRadius)),
        )
    }
}
