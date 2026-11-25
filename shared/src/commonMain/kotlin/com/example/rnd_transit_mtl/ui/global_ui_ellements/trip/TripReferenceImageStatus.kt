package com.example.rnd_transit_mtl.ui.global_ui_ellements.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp padding, spacing, and progress indicator size for reference image status.
private val ReferenceImageStatusPadding = 16.dp
private val ReferenceImageStatusSpacing = 12.dp
private val ReferenceImageSpinnerSize = 32.dp

/**
 * Stateless loading/failure presentation for [TripReferenceImage], also usable by static previews.
 *
 * @param isLoading Whether to display a loading progress indicator.
 * @param message User-facing status description.
 * @param modifier Configuration applied to the status column.
 * @param onRetry Optional retry callback shown when loading failed.
 */
@Composable
internal fun TripReferenceImageStatus(
    isLoading: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(ReferenceImageStatusPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = ReferenceImageStatusSpacing,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                progress = { 0.3f },
                color = TransitMain,
                trackColor = TransitMain.copy(alpha = 0.15f),
                modifier = Modifier.size(ReferenceImageSpinnerSize),
            )
        }

        Text(
            text = message,
            color = TransitMain,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        if (onRetry != null) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TransitMain,
                    contentColor = TransitWhite,
                ),
            ) {
                Text("Retry image")
            }
        }
    }
}
