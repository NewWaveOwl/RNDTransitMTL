package com.example.rnd_transit_mtl.View.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.View.main.components.TripSearchBar
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData
import com.example.rnd_transit_mtl.ui.global_ui_ellements.transitions.tripPanelEntrance
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlinx.coroutines.delay

// Layout settings — dp limits for the trip search loading overlay; shared max width is in TripLabelValueField.kt.
private val LoadingPanelMaximumHeight = 300.dp
private val LoadingFieldPadding = 16.dp
private val LoadingPanelPadding = 24.dp
private val LoadingPanelSpacing = 16.dp
private val LoadingAttributionTopPadding = 90.dp
private val LoadingAttributionStartPadding = 8.dp

// Animation settings — milliseconds between rotating indeterminate search status messages.
private const val SEARCH_STATUS_ROTATION_DURATION_MILLIS = 4_000L

/**
 * Trip-generation loading overlay displayed below the application header while searching for routes.
 *
 * @param minutes Requested trip duration in minutes.
 * @param transportSummary Selected transport labels.
 * @param onCancel Cancels the pending search.
 * @param modifier Parent layout constraints.
 * @param origin Selected sample-map point shown during loading.
 * @param liveOrigin Confirmed geographic origin shown on the live map, when available.
 * @param originIsManual Whether the live map should center directly on [liveOrigin].
 */
@Composable
internal fun TripLoadingContent(
    minutes: Int,
    transportSummary: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    origin: TripPoint? = TripPoint("Current location", 0.5f, 0.35f),
    liveOrigin: Place? = null,
    originIsManual: Boolean = true,
) {
    val liveMap = LocalTransitMap.current != null
    Box(modifier.fillMaxSize()) {
        TransitMap(
            data = TransitMapData(
                interactive = false,
                sharedTripFlow = true,
                browsing = true,
                pin = liveOrigin?.point,
                centerOnPin = originIsManual,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            FigmaOriginMap(origin = origin, enabled = false, modifier = Modifier.fillMaxSize())
        }

        TripLabelValueField(
            label = "Starting point",
            value = liveOrigin?.label ?: origin?.label ?: "Search a place or address",
            showLocationIcon = true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LoadingFieldPadding)
                .widthIn(max = ProposalPanelMaxWidth),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = ProposalPanelMaxWidth)
                .fillMaxWidth()
                .heightIn(max = LoadingPanelMaximumHeight)
                .tripPanelEntrance(liveMap)
                .background(TransitComplementary)
                .verticalScroll(rememberScrollState())
                .padding(LoadingPanelPadding),
            verticalArrangement = Arrangement.spacedBy(LoadingPanelSpacing),
        ) {
            // Indeterminate: the synchronous search cannot report a truthful percentage.
            TripSearchBar()
            val searchMessage by produceState(0) {
                while (true) {
                    delay(SEARCH_STATUS_ROTATION_DURATION_MILLIS)
                    value = (value + 1) % 3
                }
            }
            Crossfade(searchMessage, label = "Search status") { message ->
                Text(
                    listOf("Looking around...", "Trying a few routes...", "Finding a good fit...")[message],
                    color = TransitWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Text(
                text = "Checking routes for $minutes minutes with $transportSummary. Your choices stay saved.",
                color = TransitWhite,
                fontSize = 16.sp,
            )

            ProposalButton(
                label = "Cancel search",
                primary = false,
                onClick = onCancel,
            )
        }

        if (!liveMap) {
            SampleMapAttribution(
                Modifier.align(Alignment.TopStart).padding(
                    top = LoadingAttributionTopPadding,
                    start = LoadingAttributionStartPadding,
                ),
            )
        }
    }
}
