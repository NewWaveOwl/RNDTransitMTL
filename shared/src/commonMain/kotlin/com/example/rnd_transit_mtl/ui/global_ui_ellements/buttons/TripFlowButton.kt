package com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons

import androidx.compose.foundation.BorderStroke
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

// Layout settings — dp minimum height and corner radius for origin selection, arrival, and return actions.
private val TripFlowButtonMinimumHeight = 52.dp
private val TripFlowButtonCornerRadius = 24.dp

/**
 * Primary or secondary action for origin selection, arrival, and return sheets.
 *
 * @param label Visible button text.
 * @param onClick Called when the button is tapped.
 * @param enabled Whether the button accepts clicks.
 * @param secondary When true, renders the outlined white secondary variant.
 * @param modifier Configuration applied to the button.
 */
@Composable
internal fun TripFlowButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    secondary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = TripFlowButtonMinimumHeight),
        enabled = enabled,
        shape = RoundedCornerShape(TripFlowButtonCornerRadius),
        border = if (secondary) BorderStroke(1.dp, TransitMain) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) TransitWhite else TransitMain,
            contentColor = if (secondary) TransitMain else TransitWhite,
        ),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
