package com.example.rnd_transit_mtl.ui.global_ui_ellements.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — shared section and feature padding are defined in TripV3Shared.kt.
private val FeedbackChoiceSpacing = 8.dp
private val FeedbackChoiceCornerRadius = 14.dp
private val FeedbackChoiceMinimumHeight = 48.dp
private val FeedbackHeaderSpacing = 12.dp
private val FeedbackFormSpacing = 16.dp

/** Per-leg or per-place sentiment selection for [FeedbackV3]. */
enum class FeedbackChoice {
    NONE,
    LIKED,
    DISLIKED,
}

/**
 * Target-feedback form. Keep its live entry disabled until target-based persistence and navigation are connected.
 *
 * @param tripReference Short display suffix identifying the parent trip.
 * @param targetTitle Name of the route leg or attraction being rated.
 * @param targetDescription Supporting description of the rated target.
 * @param choice Current selected sentiment choice.
 * @param note Optional free-text note for this target.
 * @param onChoiceChange Called when the user selects Like, Dislike, or Clear.
 * @param onNoteChange Called when the user edits the note text.
 * @param onSave Called when the user saves feedback for this target.
 * @param onGo Called when the user activates the header GO shortcut.
 * @param modifier Configuration applied to the page container.
 * @param enabled Whether controls accept user interaction.
 * @param errorMessage Optional error text displayed above the save button.
 */
@Composable
fun FeedbackV3(
    tripReference: String,
    targetTitle: String,
    targetDescription: String,
    choice: FeedbackChoice,
    note: String,
    onChoiceChange: (FeedbackChoice) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
) {
    V3Page(modifier) {
        V3Heading(
            title = "Trip feedback",
            subtitle = "Trip #$tripReference · Saved route is unchanged",
            onGo = onGo,
            enabled = enabled,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .background(TransitComplementary)
                .padding(V3FeaturePadding),
            verticalArrangement = Arrangement.spacedBy(FeedbackHeaderSpacing),
        ) {
            Text(
                targetTitle,
                color = TransitWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(targetDescription, color = TransitWhite)
            Text(
                "Current feedback: " + feedbackChoiceLabel(choice),
                color = TransitWhite,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            Modifier.padding(V3SectionPadding),
            verticalArrangement = Arrangement.spacedBy(FeedbackFormSpacing),
        ) {
            FeedbackChoiceRow(
                choice = choice,
                enabled = enabled,
                onChoiceChange = onChoiceChange,
            )
            V3Note(note, onNoteChange, "Optional note", enabled)
            V3Error(errorMessage)
            V3Button("Save feedback", onSave, enabled)
            Text(
                "The same controls apply to an attraction on this trip. " +
                    "Clear returns it to No Rating.",
                color = TransitComplementary,
            )
        }
    }
}

/** Formats a [FeedbackChoice] into its user-facing status label. */
private fun feedbackChoiceLabel(choice: FeedbackChoice): String = when (choice) {
    FeedbackChoice.NONE -> "No Rating"
    FeedbackChoice.LIKED -> "Liked"
    FeedbackChoice.DISLIKED -> "Disliked"
}

/**
 * Renders the three radio-selectable sentiment options for [FeedbackV3].
 *
 * @param choice Currently selected choice.
 * @param enabled Whether option taps are enabled.
 * @param onChoiceChange Called with the tapped sentiment value.
 */
@Composable
private fun FeedbackChoiceRow(
    choice: FeedbackChoice,
    enabled: Boolean,
    onChoiceChange: (FeedbackChoice) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(FeedbackChoiceSpacing)) {
        listOf(
            "Like" to FeedbackChoice.LIKED,
            "Dislike" to FeedbackChoice.DISLIKED,
            "Clear" to FeedbackChoice.NONE,
        ).forEach { (label, value) ->
            val selected = choice == value
            Surface(
                color = if (selected) TransitSelected else TransitWhite,
                shape = RoundedCornerShape(FeedbackChoiceCornerRadius),
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onChoiceChange(value) },
                    ),
            ) {
                Box(
                    Modifier.heightIn(min = FeedbackChoiceMinimumHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (selected) TransitComplementary else TransitMain,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
