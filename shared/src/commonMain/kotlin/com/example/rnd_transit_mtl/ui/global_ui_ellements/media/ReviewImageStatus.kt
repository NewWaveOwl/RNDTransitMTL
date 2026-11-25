package com.example.rnd_transit_mtl.ui.global_ui_ellements.media

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

// Layout settings — dp padding, vertical spacing, and indicator size for review image states.
private val ReviewImageStatusPadding = 12.dp
private val ReviewImageStatusSpacing = 8.dp
private val ReviewImageSpinnerSize = 28.dp

/**
 * Static loading/failure presentation shared by runtime [ReviewImage] states and previews.
 *
 * @param isLoading Whether to display an indeterminate loading indicator.
 * @param message User-facing status description.
 * @param modifier Configuration applied to the status column.
 * @param onRetry Optional retry callback shown when loading failed.
 * @param enabled Whether the retry button accepts clicks.
 */
@Composable
internal fun ReviewImageStatus(
    isLoading: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.padding(ReviewImageStatusPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ReviewImageStatusSpacing, Alignment.CenterVertically),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = TransitMain,
                modifier = Modifier.size(ReviewImageSpinnerSize),
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
                enabled = enabled,
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
