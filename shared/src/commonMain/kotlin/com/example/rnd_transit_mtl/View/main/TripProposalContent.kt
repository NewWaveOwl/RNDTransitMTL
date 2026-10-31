package com.example.rnd_transit_mtl.View.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.MockTripMap
import com.example.rnd_transit_mtl.ui.theme.*
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_field_location
import rnd_transit_mtl.shared.generated.resources.rnd_field_chevron

// Layout settings — dimensions in dp for the supplied loading and proposal layouts.
private val ProposalPanelMaxWidth = 620.dp
private val ProposalFieldMinimumHeight = 68.dp
private val LoadingPanelMaximumHeight = 300.dp
private val ProposalPanelMaximumHeight = 340.dp
private val ProposalButtonMinimumHeight = 52.dp
private val ProposalFieldStartPadding = 14.dp
private val ProposalFieldEndPadding = 12.dp
private val ProposalFieldVerticalPadding = 7.dp
private val ProposalFieldIconSize = 26.dp
private val ProposalFieldChevronSize = 24.dp
private val ProposalFieldContentSpacing = 12.dp
private val ProposalFieldLabelValueSpacing = 3.dp

/**
 * RND / Field / Label & value.
 * The loading field uses the origin/disclosure variant; the destination uses Figma's text-entry variant.
 * @param label Field caption.
 * @param value Displayed location or destination.
 * @param modifier Parent layout constraints.
 * @param showLocationIcon Whether the Figma origin icon and disclosure chevron are shown.
 */
@Composable
internal fun TripLabelValueField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    showLocationIcon: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TransitWhite,
        border = BorderStroke(1.dp, TransitMain)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = ProposalFieldMinimumHeight)
                .padding(start = ProposalFieldStartPadding, end = ProposalFieldEndPadding,
                    top = ProposalFieldVerticalPadding, bottom = ProposalFieldVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ProposalFieldContentSpacing)
        ) {
            if (showLocationIcon) {
                Image(
                    painterResource(Res.drawable.rnd_field_location),
                    contentDescription = null,
                    modifier = Modifier.size(ProposalFieldIconSize)
                )
            }

            Column(
                modifier = Modifier.weight(1f).align(Alignment.Top),
                verticalArrangement = Arrangement.spacedBy(ProposalFieldLabelValueSpacing)
            ) {
                Text(
                    text = label,
                    color = TransitComplementary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Text(
                    text = value,
                    color = TransitComplementary,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showLocationIcon) {
                Image(painterResource(Res.drawable.rnd_field_chevron), contentDescription = null,
                    modifier = Modifier.size(ProposalFieldChevronSize))
            }
        }
    }
}

/** Content below the existing application header.
 * @param minutes Requested trip duration.
 * @param transportSummary Selected transport labels.
 * @param onCancel Cancels the pending search.
 * @param origin Selected sample-map point shown during loading.
 * @param modifier Parent layout constraints.
 */
@Composable
internal fun TripLoadingContent(
    minutes: Int,
    transportSummary: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    origin: TripPoint? =
        TripPoint(
            "Current location",
            0.5f,
            0.35f
        )
) {
    Box(modifier.fillMaxSize()) {
        FigmaOriginMap(
            origin = origin,
            enabled = false,
            modifier = Modifier.fillMaxSize()
        )

        TripLabelValueField(
            label = "Starting point",
            value = origin?.label ?: "Search a place or address",
            showLocationIcon = true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .widthIn(max = ProposalPanelMaxWidth)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = ProposalPanelMaxWidth)
                .fillMaxWidth()
                .heightIn(max = LoadingPanelMaximumHeight)
                .background(TransitComplementary)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = TransitSelected,
                strokeWidth = 3.dp,
                modifier = Modifier.size(26.dp)
            )

            Text(
                text = "Finding your adventure…",
                color = TransitWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                }
            )

            Text(
                text = "Checking routes for $minutes minutes with " +
                    "$transportSummary. Your choices stay saved.",
                color = TransitWhite,
                fontSize = 16.sp
            )

            ProposalButton(
                label = "Cancel search",
                primary = false,
                onClick = onCancel
            )
        }

        MapAttribution(
            Modifier.align(Alignment.TopStart).padding(top = 90.dp, start = 8.dp)
        )
    }
}

/** Shows a generated proposal without activating it.
 * @param trip Generated sample trip awaiting confirmation.
 * @param onStart Requests activation of this proposal.
 * @param onGenerateAnother Requests a fresh proposal.
 * @param onEditSettings Returns to the saved planner choices.
 * @param modifier Parent layout constraints.
 */
@Composable
internal fun TripProposalContent(
    trip: Trip,
    onStart: () -> Unit,
    onGenerateAnother: () -> Unit,
    onEditSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        MockTripMap(
            start = trip.start,
            destination = trip.destination,
            progress = 0f,
            matchPlannerBackground = true,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = ProposalPanelMaxWidth)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TripLabelValueField(
                label = "Trip destination",
                value = trip.title
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = TransitWhite
            ) {
                Text(
                    text = "Sample trip · #${trip.id.value.takeLast(3)}",
                    color = TransitMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = ProposalPanelMaxWidth)
                .fillMaxWidth()
        ) {
            MapAttribution(Modifier.padding(start = 8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ProposalPanelMaximumHeight)
                    .background(TransitComplementary)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = trip.title,
                    color = TransitWhite,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                val distance = (trip.distanceKm * 10).toInt() / 10.0
                Text(
                    text = "${trip.plannedMinutes} min · $distance km · One way",
                    color = TransitWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = trip.selectedTransports.joinToString(" + ") { it.label },
                    color = TransitWhite,
                    fontSize = 15.sp
                )
                Text(
                    text = "Sample route · opening hours not confirmed",
                    color = TransitWhite,
                    fontSize = 12.sp
                )

                ProposalButton("Start Trip", true, onStart)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProposalButton(
                        "Generate another",
                        false,
                        onGenerateAnother,
                        Modifier.weight(1f)
                    )
                    ProposalButton(
                        "Edit settings",
                        false,
                        onEditSettings,
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Proposal action with the supplied primary/secondary treatment.
 * @param label Visible action label.
 * @param primary Whether to use the blue primary style.
 * @param onClick Requests the parent-owned action.
 * @param modifier Parent layout constraints.
 */
@Composable
private fun ProposalButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = ProposalButtonMinimumHeight),
        shape = RoundedCornerShape(24.dp),
        border = if (primary) null else BorderStroke(1.dp, TransitMain),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) TransitMain else TransitWhite,
            contentColor = if (primary) TransitWhite else TransitMain
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

/** Sample-map attribution.
 * @param modifier Placement within the map.
 */
@Composable
private fun MapAttribution(modifier: Modifier = Modifier) {
    Text(
        text = "© OpenStreetMap contributors · Sample map",
        color = TransitComplementary,
        fontSize = 10.sp,
        modifier = modifier
            .background(TransitWhite, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}
