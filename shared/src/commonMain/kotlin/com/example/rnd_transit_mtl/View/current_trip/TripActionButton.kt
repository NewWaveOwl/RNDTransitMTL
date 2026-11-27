package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp minimum height, corner radius, and padding for trip actions.
private val TripActionButtonMinimumHeight = 52.dp
private val TripActionButtonCornerRadius = 24.dp
private val TripActionButtonContentPadding = 12.dp

/**
 * Shared primary or secondary trip action button.
 *
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
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = TripActionButtonMinimumHeight),
        shape = RoundedCornerShape(TripActionButtonCornerRadius),
        border = if (secondary) BorderStroke(1.dp, TransitMain) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) TransitWhite else TransitMain,
            contentColor = if (secondary) TransitMain else TransitWhite,
        ),
        contentPadding = PaddingValues(TripActionButtonContentPadding),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
