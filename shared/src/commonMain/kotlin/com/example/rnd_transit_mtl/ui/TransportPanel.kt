package com.example.rnd_transit_mtl.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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

/** Renders transport buttons and the route editor for the expanded transport type. */
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
    val routesByTransport = remember(transportRoutes) {
        transportRoutes.groupBy { it.transportTypeId }
    }

    Column(
        modifier = modifier
            .background(TransitMain, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .padding(horizontal = 12.dp * layoutScale, vertical = 10.dp * layoutScale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp * layoutScale)
    ) {
        Text("Types Of Transport", color = TransitWhite, fontSize = 26.sp * layoutScale)
        Spacer(Modifier.height(2.dp * layoutScale))

        transportTypes.chunked(3).forEach { transportRow ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp * layoutScale)
            ) {
                transportRow.forEachIndexed { column, transport ->
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

    }
    // Route selection overlays the screen so opening it does not move the main panels.
    transportTypes.firstOrNull { it.id == expandedTransportId }?.let { transport ->
        AlertDialog(
            onDismissRequest = { onExpandedTransportChange(transport.id) },
            containerColor = TransitMain,
            title = { Text("${transport.label} routes", color = TransitWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    routesByTransport[transport.id].orEmpty().chunked(3).forEach { routeRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            routeRow.forEach { route ->
                                RouteChoice(
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
            },
            confirmButton = {
                TextButton(onClick = { onExpandedTransportChange(transport.id) }) {
                    Text("Done", color = TransitWhite)
                }
            }
        )
    }
}

/** Draws one transport control and summarizes its selected routes. */
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
    val buttonBrush = when {
        selected && isRouteTransport -> Brush.horizontalGradient(
            0.00f to TransitSelected,
            0.62f to TransitSelected,
            1.00f to TransitHighlight
        )

        selected -> Brush.horizontalGradient(listOf(TransitSelected, TransitSelected))
        else -> Brush.horizontalGradient(listOf(TransitHighlight, TransitHighlight))
    }
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

/** Draws a selectable route chip, including its selected gradient state. */
@Composable
private fun RouteChoice(
    route: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            .height(48.dp)
            .background(brush, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Text(route, color = TransitWhite, fontSize = 20.sp, maxLines = 1)
    }
}
