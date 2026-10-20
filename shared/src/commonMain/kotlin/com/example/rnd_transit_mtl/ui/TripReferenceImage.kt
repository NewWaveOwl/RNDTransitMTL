package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Loads the trip's separate reference image.
 *
 * This component never changes endpoints, progress, or the bundled map.
 * Coil owns request cancellation and image caching.
 *
 * Preview mode displays a fixed placeholder without a network request.
 */
@Composable
fun TripReferenceImage(
    imageUrl: String,
    tripTitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(TransitSelected.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (LocalInspectionMode.current) {
            TripReferenceImageStatus(
                isLoading = false,
                message = "Trip reference image preview",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = "Reference image for $tripTitle",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    TripReferenceImageStatus(
                        isLoading = true,
                        message = "Loading reference image…",
                        modifier = Modifier.fillMaxSize()
                    )
                },
                error = {
                    TripReferenceImageStatus(
                        isLoading = false,
                        message = "Reference image unavailable. " +
                                "Your trip and mock map are still available.",
                        onRetry = { painter.restart() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        }
    }
}

/**
 * Stateless loading/failure presentation, also usable by static previews.
 */
@Composable
internal fun TripReferenceImageStatus(
    isLoading: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = 12.dp,
            alignment = Alignment.CenterVertically
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                progress = { 0.3f },
                color = TransitMain,
                trackColor = TransitMain.copy(alpha = 0.15f),
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = message,
            color = TransitMain,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        if (onRetry != null) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TransitMain,
                    contentColor = TransitWhite
                )
            ) {
                Text("Retry image")
            }
        }
    }
}
