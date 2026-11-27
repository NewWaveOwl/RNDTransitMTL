package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp padding and vertical spacing for the route failure panel.
private val RouteFailureContentPadding = 16.dp
private val RouteFailureContentSpacing = 14.dp

/** Distinct routing-provider outcomes; not inferred from mock input validation. */
enum class RouteFailure {
    NoRoute,
    ProviderUnavailable,
}

/**
 * Failure UI for a routing-provider response or an explicit preview fixture.
 *
 * @param failure Distinguishes an unavailable provider from a valid search with no route.
 * @param requestSummary Original request summary retained for retry.
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
    modifier: Modifier = Modifier,
) {
    val unavailable = failure == RouteFailure.ProviderUnavailable

    Column(
        modifier
            .fillMaxWidth()
            .background(TransitComplementary)
            .verticalScroll(rememberScrollState()),
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
            },
        )

        Column(
            Modifier.padding(RouteFailureContentPadding),
            verticalArrangement = Arrangement.spacedBy(RouteFailureContentSpacing),
        ) {
            Text(
                "Your request is unchanged",
                color = TransitWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(requestSummary, color = TransitWhite)

            TripActionButton(
                label = if (unavailable) "Try again" else "Adjust time or modes",
                onClick = if (unavailable) onRetry else onEditSettings,
            )

            TripActionButton(
                label = if (unavailable) "Edit trip settings" else "Try another search",
                secondary = true,
                onClick = if (unavailable) onEditSettings else onRetry,
            )
        }
    }
}
