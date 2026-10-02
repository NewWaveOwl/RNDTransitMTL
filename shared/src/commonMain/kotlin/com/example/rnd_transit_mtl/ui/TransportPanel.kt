package com.example.rnd_transit_mtl.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary

/**
 * Displays transport choices and expands route selection inside the same panel.
 *
 * Selection and expansion state are supplied by the parent. User actions are sent
 * through callbacks so the parent can update that state.
 *
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 * @param transportTypes Available transport options in display order.
 * @param transportRoutes Available routes, each linked to its owning transport type.
 * @param selectedTransportIds IDs of the transport modes currently included in the trip.
 * @param selectedRouteIds IDs of all selected routes across transport modes.
 * @param expandedTransportId ID of the transport whose routes are expanded, or null when all are collapsed.
 * @param onExpandedTransportChange Requests expansion, collapse, or switching of the route list for a transport ID.
 * @param onToggleTransport Toggles a transport without route choices, such as Walking or Bike.
 * @param onToggleRoute Toggles a route using its transport ID and route ID.
 * @param modifier Layout and appearance modifiers supplied by the parent.
 */
@Composable
internal fun TransportPanel(
    layoutScale: Float = 1f,
    transportTypes: List<TransportType>,
    transportRoutes: List<TransportRoute>,
    selectedTransportIds: List<String>,
    selectedRouteIds: List<String>,
    expandedTransportId: String?,
    onExpandedTransportChange: (String) -> Unit,
    onToggleTransport: (String) -> Unit,
    onToggleRoute: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    /** Group routes by transport ID and reuse the result until the route list changes. */
    val routesByTransport = remember(transportRoutes) {
        transportRoutes.groupBy { it.transportTypeId }
    }

    Column(
        modifier = modifier
            .animateContentSize()
            .background(TransitMain, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .padding(horizontal = 12.dp * layoutScale, vertical = 10.dp * layoutScale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp * layoutScale)
    ) {
        Text("Types Of Transport", color = TransitWhite, fontSize = 26.sp * layoutScale)
        Spacer(Modifier.height(2.dp * layoutScale))

        /** Arrange transport choices in rows of up to three buttons. */
        transportTypes.chunked(3).forEach { transportRow ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp * layoutScale)
            ) {
                transportRow.forEachIndexed { column, transport ->
                    /** Show only the selected route labels belonging to this transport. */
                    val selectedRouteLabels = routesByTransport[transport.id]
                        .orEmpty()
                        .filter { it.id in selectedRouteIds }
                        .map { it.label }
                    TransportChoice(
                        layoutScale = layoutScale,
                        title = transport.label,
                        isRouteTransport = transport.usesRoutes,
                        selected = transport.id in selectedTransportIds,
                        selectedRoutes = selectedRouteLabels,
                        expanded = expandedTransportId == transport.id,
                        onClick = {
                            /** Open route choices for route-based transport; toggle other modes directly. */
                            if (transport.usesRoutes) onExpandedTransportChange(transport.id)
                            else onToggleTransport(transport.id)
                        },
                        modifier = Modifier.weight(if (column == 2) 1f else 1.6f)
                    )
                }
                repeat(3 - transportRow.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        /** Display route choices below the buttons only for the currently expanded transport. */
        transportTypes.firstOrNull { it.id == expandedTransportId }?.let { transport ->
            Spacer(Modifier.height(2.dp * layoutScale))
            Text("${transport.label} routes", color = TransitWhite, fontSize = 20.sp * layoutScale)
            routesByTransport[transport.id].orEmpty().chunked(3).forEach { routeRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp * layoutScale)
                ) {
                    routeRow.forEach { route ->
                        RouteChoice(
                            layoutScale = layoutScale,
                            route = route.label,
                            selected = route.id in selectedRouteIds,
                            onClick = { onToggleRoute(transport.id, route.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - routeRow.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Draws one transport button with its selected route summary and expanded indicator.
 *
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 * @param title Transport label displayed before any selected route labels.
 * @param isRouteTransport Whether the transport exposes individually selectable routes.
 * @param selected Whether this transport is included in the current trip.
 * @param selectedRoutes Display labels of the selected routes belonging to this transport.
 * @param expanded Whether this transport currently has its route list open.
 * @param onClick Opens or collapses route choices, or toggles a transport without routes.
 * @param modifier Layout and appearance modifiers supplied by the parent.
 */
@Composable
private fun TransportChoice(
    layoutScale: Float,
    title: String,
    isRouteTransport: Boolean,
    selected: Boolean,
    selectedRoutes: List<String>,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    /** Use a gradient for selected route-based modes and a solid color for other states. */
    val buttonBrush = when {
        selected && isRouteTransport -> Brush.horizontalGradient(
            0.00f to TransitSelected,
            0.62f to TransitSelected,
            1.00f to TransitHighlight
        )

        selected -> Brush.horizontalGradient(listOf(TransitSelected, TransitSelected))
        else -> Brush.horizontalGradient(listOf(TransitHighlight, TransitHighlight))
    }
    /** Append a compact route summary only when at least one route is selected. */
    val label = if (selectedRoutes.isEmpty()) {
        title
    } else {
        "$title ${selectedRoutes.joinToString(",")}"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp * layoutScale)
            .background(buttonBrush, RoundedCornerShape(14.dp * layoutScale))
            .then(
                if (expanded) Modifier.border(1.dp, TransitComplementary, RoundedCornerShape(14.dp * layoutScale))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp)
    ) {
        Text(
            text = label,
            color = TransitWhite,
            fontSize = 26.sp * layoutScale,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Draws an individually selectable route chip with selected-state colors.
 *
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 * @param route Display label of the route.
 * @param selected Whether this route is selected.
 * @param onClick Toggles this route in the parent-owned selection list.
 * @param modifier Layout and appearance modifiers supplied by the parent.
 */
@Composable
private fun RouteChoice(
    layoutScale: Float,
    route: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    /** Highlight selected routes so their state is visible independently of the open route list. */
    val brush = if (selected) {
        Brush.horizontalGradient(
            0.00f to TransitSelected,
            0.65f to TransitSelected,
            1.00f to TransitHighlight
        )
    } else {
        Brush.horizontalGradient(listOf(TransitHighlight, TransitHighlight))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(36.dp * layoutScale)
            .background(brush, RoundedCornerShape(10.dp * layoutScale))
            .clickable(onClick = onClick)
    ) {
        Text(route, color = TransitWhite, fontSize = 20.sp * layoutScale, maxLines = 1)
    }
}