package com.example.rnd_transit_mtl.ui.global_ui_ellements.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.roundToInt

// Layout settings — dp dimensions shared across the V3 trip screens.
internal val V3SectionPadding = 16.dp
internal val V3FeaturePadding = 24.dp
internal val V3ButtonMinimumHeight = 52.dp
private val V3GoWidth = 72.dp
private val V3GoHeight = 44.dp
private val V3GoCornerRadius = 18.dp
private val V3ButtonCornerRadius = 24.dp
private val V3NoteCornerRadius = 16.dp

/**
 * Scrollable page container shared by V3 screens between the app header and bottom navigation.
 *
 * @param modifier Configuration applied to the root column.
 * @param content Vertically arranged sections rendered inside the scrollable container.
 */
@Composable
internal fun V3Page(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(TransitWhite)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}

/**
 * Header band displaying a screen title, optional subtitle, and GO shortcut back to Main.
 *
 * @param title Primary heading for the V3 screen.
 * @param subtitle Optional supporting context shown beneath the title.
 * @param onGo Called when the user activates the GO shortcut.
 * @param enabled Whether the GO shortcut accepts interaction.
 */
@Composable
internal fun V3Heading(
    title: String,
    subtitle: String?,
    onGo: () -> Unit,
    enabled: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().padding(V3SectionPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = TransitComplementary,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
            )

            Box(
                modifier = Modifier
                    .size(V3GoWidth, V3GoHeight)
                    .background(
                        Brush.horizontalGradient(
                            listOf(TransitMain, TransitSelected),
                        ),
                        RoundedCornerShape(V3GoCornerRadius),
                    )
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = "Return to main",
                        onClick = onGo,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "GO",
                    color = TransitWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (subtitle != null) {
            Text(subtitle, color = TransitMain, fontSize = 14.sp)
        }
    }
}

/**
 * Primary or secondary full-width action button styled for V3 trip screens.
 *
 * @param label Visible button text.
 * @param onClick Called when the user taps the button.
 * @param enabled Whether the button accepts clicks.
 * @param secondary When true, renders the outlined white secondary variant.
 * @param modifier Configuration applied to the button.
 */
@Composable
internal fun V3Button(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    secondary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = V3ButtonMinimumHeight),
        shape = RoundedCornerShape(V3ButtonCornerRadius),
        border = if (secondary) BorderStroke(1.dp, TransitMain) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) TransitWhite else TransitMain,
            contentColor = if (secondary) TransitMain else TransitWhite,
            disabledContainerColor = TransitMain.copy(alpha = 0.08f),
            disabledContentColor = TransitComplementary.copy(alpha = 0.45f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/**
 * Multi-line note input styled for V3 review and feedback forms.
 *
 * @param value Current note text supplied by the caller.
 * @param onValueChange Called with updated text after an edit.
 * @param label Input field label text.
 * @param enabled Whether the field accepts edits.
 */
@Composable
internal fun V3Note(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(V3NoteCornerRadius),
        minLines = 2,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TransitMain,
            unfocusedBorderColor = TransitMain,
            focusedTextColor = TransitComplementary,
            unfocusedTextColor = TransitComplementary,
            focusedLabelColor = TransitComplementary,
            unfocusedLabelColor = TransitComplementary,
        ),
    )
}

/**
 * Displays an error message using the Material error color when non-blank.
 *
 * @param message Error text to show, or null/blank to render nothing.
 */
@Composable
internal fun V3Error(message: String?) {
    if (!message.isNullOrBlank()) {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

/**
 * Formats a trip's distance rounded to one decimal place in kilometres.
 *
 * @param trip Trip whose distance is formatted.
 * @return Formatted distance label such as `"3.4 km"`.
 */
internal fun v3DistanceText(trip: Trip): String =
    "${(trip.distanceKm * 10).roundToInt() / 10.0} km"

/**
 * Joins a trip's selected transport mode labels with `" + "`.
 *
 * @param trip Trip whose transport modes are summarized.
 * @return Human-readable transport summary string.
 */
internal fun v3TransportText(trip: Trip): String =
    trip.selectedTransports.joinToString(" + ") { it.label }
