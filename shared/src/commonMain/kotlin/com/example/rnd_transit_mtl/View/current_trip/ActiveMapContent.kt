package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.MockTripMap
import com.example.rnd_transit_mtl.ui.theme.*
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_recenter
import rnd_transit_mtl.shared.generated.resources.rnd_turn_left
import rnd_transit_mtl.shared.generated.resources.rnd_warning

// Layout settings — dimensions in dp; the panel fraction keeps part of the route visible.
private const val ActiveMapPanelMaximumHeightFraction = 0.56f
private val ActiveMapHorizontalPadding = 24.dp
private val ActiveMapPanelPadding = 16.dp
private val ActiveMapActionSpacing = 12.dp
private val ActiveMapDialogMaximumWidth = 620.dp
private val ActiveMapProgressHeight = 64.dp
private val ActiveMapProgressCornerRadius = 24.dp
private val ActiveMapPercentageWidth = 64.dp
private val ActiveMapPercentageInset = 8.dp
private val ActiveMapButtonMinimumHeight = 52.dp
private val ActiveMapRecenterSize = 48.dp
private val ActiveMapInstructionIconSize = 32.dp
private val ActiveMapWarningIconSize = 26.dp
private val ActiveMapWarningMinimumHeight = 88.dp

/**
 * Supply instructions from routing data.
 * Leave null when using the current mock generator.
 * @param title Routing instruction heading.
 * @param detail Routing instruction details.
 * @param showLeftTurnIcon Whether to use the exported instruction icon.
 */
data class ActiveMapInstruction(
    val title: String,
    val detail: String,
    val showLeftTurnIcon: Boolean = false
)

/** Explicit location availability; sample progress is not a GPS reading. */
enum class TripLocationState {
    Sample,
    Stale,
    Missing
}

/** Shared active-trip/overview map with explicit completion and confirmed cancellation.
 * @param trip Stored trip whose identity and route remain unchanged.
 * @param progress Estimated fraction from 0 to 1; invalid values display as zero.
 * @param actionsEnabled Whether lifecycle and navigation permit interaction.
 * @param onFinish Finishes the demo early, or requests review after completion.
 * @param onConfirmCancel Cancels only after confirmation.
 * @param modifier Parent layout constraints.
 * @param instruction Real routing instructions, or null for mock routes.
 * @param staleMessage Real location warning, or null when unavailable.
 * @param operationMessage Feedback from a trip operation.
 * @param mainOverview Whether to show the Main resume presentation.
 * @param onResume Opens the same stored trip.
 * @param onShowInformation Opens the existing information dialog.
 * @param locationState Explicit sample, stale, or missing location state.
 * @param sampleStateLabel Optional fixture label, never inferred from provider errors.
 */
@Composable
fun ActiveMapContent(
    trip: Trip,
    progress: Float,
    actionsEnabled: Boolean,
    onFinish: () -> Unit,
    onConfirmCancel: () -> Unit,
    modifier: Modifier = Modifier,
    instruction: ActiveMapInstruction? = null,
    staleMessage: String? = null,
    operationMessage: String? = null,
    mainOverview: Boolean = false,
    onResume: () -> Unit = {},
    onShowInformation: () -> Unit = {},
    locationState: TripLocationState = TripLocationState.Sample,
    sampleStateLabel: String? = null
) {
    val fraction = if (progress.isFinite()) {
        progress.coerceIn(0f, 1f)
    } else {
        0f
    }
    val completed = fraction >= 1f
    val reference = trip.id.takeLast(3)

    var recenterRequest by rememberSaveable(trip.id) {
        mutableStateOf(0)
    }
    var retainedMapProgress by rememberSaveable(trip.id) {
        mutableStateOf(fraction)
    }

    LaunchedEffect(fraction, locationState) {
        if (locationState == TripLocationState.Sample) {
            retainedMapProgress = fraction
        }
    }

    var confirmingFinish by rememberSaveable(trip.id) {
        mutableStateOf(false)
    }

    LaunchedEffect(completed) {
        if (completed) confirmingFinish = false
    }

    var confirmingCancel by rememberSaveable(trip.id) {
        mutableStateOf(false)
    }

    // Completion can happen while confirmation is open.
    LaunchedEffect(completed) {
        if (completed) confirmingCancel = false
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelMaxHeight = maxHeight * ActiveMapPanelMaximumHeightFraction

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                MockTripMap(
                    start = trip.start,
                    destination = trip.destination,
                    progress = retainedMapProgress,
                    interactive = actionsEnabled && !confirmingCancel && !confirmingFinish,
                    matchPlannerBackground = true,
                    recenterRequest = recenterRequest,
                    showControls = false,
                    showPosition = locationState != TripLocationState.Missing,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                ) {
                    if (!mainOverview) {
                        Row(
                            Modifier
                                .padding(top = 16.dp)
                                .fillMaxWidth()
                                .background(TransitMain)
                                .clickable(
                                    enabled = actionsEnabled,
                                    role = Role.Button,
                                    onClickLabel = "Open current section information",
                                    onClick = onShowInformation
                                )
                                .padding(horizontal = ActiveMapHorizontalPadding, vertical = ActiveMapPanelPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (instruction?.showLeftTurnIcon == true) {
                                Image(
                                    painterResource(Res.drawable.rnd_turn_left),
                                    contentDescription = null,
                                    modifier = Modifier.size(ActiveMapInstructionIconSize)
                                )
                            }

                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    instruction?.title ?: "Current section",
                                    color = TransitWhite,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    instruction?.detail
                                        ?: trip.selectedRoutes.firstOrNull()?.label
                                        ?: "Following the sample route",
                                    color = TransitWhite,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.padding(16.dp),
                        color = TransitWhite,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "Sample trip · #$reference" +
                                sampleStateLabel?.let { " · $it" }.orEmpty(),
                            color = TransitMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 6.dp
                            )
                        )
                    }

                    if (!mainOverview) {
                        when (locationState) {
                            TripLocationState.Stale -> TripWarning(
                                title = "Location not updating",
                                detail = staleMessage
                                    ?: "No recent reading. Route remains available."
                            )

                            TripLocationState.Missing -> TripWarning(
                                title = "Location unavailable",
                                detail = "Your saved route remains available."
                            )

                            TripLocationState.Sample -> {
                                if (staleMessage != null) {
                                    TripWarning("Location not updating", staleMessage)
                                }
                            }
                        }
                    }
                }

                Image(
                    painterResource(Res.drawable.rnd_recenter),
                    contentDescription = "Recenter on current position",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp)
                        .size(ActiveMapRecenterSize)
                        .clickable(
                            enabled = actionsEnabled && !confirmingCancel && !confirmingFinish &&
                                locationState != TripLocationState.Missing,
                            role = Role.Button
                        ) {
                            recenterRequest += 1
                        }
                )

                Text(
                    "© OpenStreetMap contributors · Sample map",
                    color = TransitComplementary,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(
                            TransitWhite,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = panelMaxHeight)
                    .background(TransitComplementary)
                    .verticalScroll(rememberScrollState())
                    .padding(ActiveMapPanelPadding),
                verticalArrangement = Arrangement.spacedBy(ActiveMapActionSpacing)
            ) {
                if (mainOverview) {
                    Text(
                        "Your adventure is still running",
                        color = TransitWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "Current trip · #$reference",
                    color = TransitWhite,
                    fontSize = 14.sp
                )

                EstimatedTripProgress(trip, fraction)

                if (operationMessage != null) {
                    Text(
                        operationMessage,
                        color = TransitWhite,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        }
                    )
                }

                if (mainOverview) {
                    TripActionButton(
                        label = "Resume current trip",
                        enabled = actionsEnabled,
                        onClick = onResume
                    )
                    Text(
                        "Finish or cancel this trip before generating another.",
                        color = TransitWhite,
                        fontSize = 12.sp
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TripActionButton(
                            label = if (completed) "Review trip" else "Finish trip",
                            enabled = actionsEnabled,
                            onClick = {
                                if (completed) onFinish()
                                else { confirmingCancel = false; confirmingFinish = true }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        TripActionButton(
                            label = "Cancel",
                            secondary = true,
                            enabled = actionsEnabled && !completed,
                            onClick = { confirmingFinish = false; confirmingCancel = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TextButton(
                        onClick = onShowInformation,
                        enabled = actionsEnabled
                    ) {
                        Text("Trip information", color = TransitWhite)
                    }
                }
            }
        }
    }

    if (confirmingCancel && !completed) {
        Dialog(
            onDismissRequest = { confirmingCancel = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ActiveMapDialogMaximumWidth)
                    .fillMaxWidth()
                    .background(TransitWhite)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Cancel this adventure?",
                    color = TransitComplementary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "This ends trip #$reference. " +
                        "Your planner choices will stay saved.",
                    color = TransitComplementary,
                    fontSize = 16.sp
                )

                TripActionButton(
                    label = "Yes, cancel trip",
                    enabled = actionsEnabled,
                    onClick = {
                        confirmingCancel = false
                        onConfirmCancel()
                    }
                )

                TripActionButton(
                    label = "Keep travelling",
                    secondary = true,
                    onClick = { confirmingCancel = false }
                )
            }
        }
    }

    if (confirmingFinish && !completed) {
        Dialog(
            onDismissRequest = { confirmingFinish = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier
                    .widthIn(max = ActiveMapDialogMaximumWidth)
                    .fillMaxWidth()
                    .background(TransitWhite)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Finish this adventure?",
                    color = TransitComplementary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "This marks the sample trip as completed.",
                    color = TransitComplementary
                )
                TripActionButton(
                    "Yes, finish trip",
                    onClick = {
                        confirmingFinish = false
                        onFinish()
                    },
                    enabled = actionsEnabled
                )
                TripActionButton(
                    "Keep travelling",
                    onClick = { confirmingFinish = false },
                    secondary = true
                )
            }
        }
    }
}

/** Remaining estimates derived from the demo's progress fraction.
 * @param trip Trip duration, distance, and selected modes.
 * @param fraction Bounded progress from 0 to 1.
 */
@Composable
private fun EstimatedTripProgress(
    trip: Trip,
    fraction: Float
) {
    val remaining = 1f - fraction
    val minutes = ceil(trip.plannedMinutes * remaining).toInt()
    val distance = (trip.distanceKm * remaining * 10).roundToInt() / 10.0
    val percentage = (fraction * 100).roundToInt()

    Column {
        Text(
            if (fraction >= 1f) "Destination reached"
            else "$minutes min remaining",
            color = TransitWhite,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "$distance km · " +
                trip.selectedTransports.joinToString(" + ") { it.label },
            color = TransitWhite,
            fontSize = 14.sp
        )

        Text(
            "Estimated progress",
            color = TransitWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        BoxWithConstraints(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(ActiveMapProgressHeight)
                .clip(RoundedCornerShape(ActiveMapProgressCornerRadius))
                .background(TransitWhite)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        fraction,
                        0f..1f
                    )
                }
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .width(maxWidth * fraction)
                        .fillMaxHeight()
                        .background(
                            TransitMain,
                            RoundedCornerShape(24.dp)
                        )
                )
            }

            val fillWidth = maxWidth * fraction
            val labelWidth = ActiveMapPercentageWidth.coerceAtMost(maxWidth)
            val insideFill = fillWidth >= labelWidth + ActiveMapPercentageInset * 2
            val labelOffset = (if (insideFill) fillWidth - labelWidth - ActiveMapPercentageInset
                else fillWidth + ActiveMapPercentageInset)
                .coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp))

            // Plain percentage inside the blue fill; at low values use blue text on the white track.
            Text(
                "$percentage%",
                color = if (insideFill) TransitWhite else TransitMain,
                fontSize = 24.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = labelOffset)
                    .width(labelWidth)
            )
        }
    }
}

/** Announces a warning while retaining the surrounding trip content.
 * @param title Warning heading.
 * @param detail Explanation of the condition.
 * @param modifier Parent layout constraints.
 */
@Composable
internal fun TripWarning(
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ActiveMapWarningMinimumHeight)
            .background(TransitOrange)
            .padding(
                start = 14.dp, end = 16.dp,
                top = 12.dp, bottom = 14.dp
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painterResource(Res.drawable.rnd_warning),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(ActiveMapWarningIconSize)
        )

        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                color = TransitComplementary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                detail,
                color = TransitComplementary,
                fontSize = 14.sp
            )
        }
    }
}

/** Shared primary or secondary trip action.
 * @param label Visible action label.
 * @param onClick Requests the parent-owned operation.
 * @param modifier Parent layout constraints.
 * @param secondary Whether to use the outlined white style.
 * @param enabled Whether interaction is permitted.
 */
@Composable
internal fun TripActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = ActiveMapButtonMinimumHeight),
        shape = RoundedCornerShape(24.dp),
        border = if (secondary) BorderStroke(1.dp, TransitMain) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) TransitWhite else TransitMain,
            contentColor = if (secondary) TransitMain else TransitWhite
        ),
        contentPadding = PaddingValues(12.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Distinct routing-provider outcomes; not inferred from mock input validation. */
enum class RouteFailure {
    NoRoute,
    ProviderUnavailable
}

/** Failure UI for a future provider response or an explicit preview fixture.
 * @param failure Distinguishes an unavailable provider from a valid search with no route.
 * @param requestSummary Original request, retained for retry.
 * @param onRetry Repeats the request.
 * @param onEditSettings Returns to the saved planner draft.
 * @param modifier Placement over the generation map.
 */
@Composable
fun RouteFailurePanel(
    failure: RouteFailure,
    requestSummary: String,
    onRetry: () -> Unit,
    onEditSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unavailable = failure == RouteFailure.ProviderUnavailable

    Column(
        modifier
            .fillMaxWidth()
            .background(TransitComplementary)
            .verticalScroll(rememberScrollState())
    ) {
        TripWarning(
            title = if (unavailable) {
                "Routing service unavailable"
            } else {
                "No trip fits these choices"
            },
            detail = if (unavailable) {
                "The service could not be reached. Your choices are saved."
            } else {
                "Try more time or another supported transport mode."
            }
        )

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Your request is unchanged",
                color = TransitWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(requestSummary, color = TransitWhite)

            TripActionButton(
                label = if (unavailable) "Try again"
                else "Adjust time or modes",
                onClick = if (unavailable) onRetry else onEditSettings
            )

            TripActionButton(
                label = if (unavailable) "Edit trip settings"
                else "Try another search",
                secondary = true,
                onClick = if (unavailable) onEditSettings else onRetry
            )
        }
    }
}
