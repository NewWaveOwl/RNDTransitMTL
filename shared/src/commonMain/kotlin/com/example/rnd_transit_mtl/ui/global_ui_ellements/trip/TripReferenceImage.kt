package com.example.rnd_transit_mtl.ui.global_ui_ellements.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.example.rnd_transit_mtl.ui.theme.TransitSelected

// Layout settings — aspect ratio and dp corner radius for the trip reference image frame.
private const val ReferenceImageAspectRatio = 16f / 9f
private val ReferenceImageCornerRadius = 16.dp

/**
 * Loads the trip's separate reference image.
 * This component never changes endpoints, progress, or the bundled map.
 * Coil owns request cancellation and image caching.
 * Preview mode displays a fixed placeholder without a network request.
 *
 * @param imageUrl Remote URL of the trip reference image.
 * @param tripTitle Display title used in the accessible image description.
 * @param modifier Configuration applied to the image container.
 */
@Composable
fun TripReferenceImage(
    imageUrl: String,
    tripTitle: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ReferenceImageAspectRatio)
            .clip(RoundedCornerShape(ReferenceImageCornerRadius))
            .background(TransitSelected.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (LocalInspectionMode.current) {
            TripReferenceImageStatus(
                isLoading = false,
                message = "Trip reference image preview",
                modifier = Modifier.fillMaxSize(),
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
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                error = {
                    TripReferenceImageStatus(
                        isLoading = false,
                        message = "Reference image unavailable. " +
                            "Your trip and mock map are still available.",
                        onRetry = { painter.restart() },
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
    }
}
