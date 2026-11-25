package com.example.rnd_transit_mtl.ui.global_ui_ellements.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — aspect ratio and dp corner radius for the review photo frame.
private const val ReviewImageAspectRatio = 16f / 9f
private val ReviewImageCornerRadius = 16.dp

/**
 * Renders a saved review photo only while its expanded review is composed.
 * Coil handles caching/cancellation; image loading never changes trip state.
 * Fit preserves the complete photo. Previews do not issue network requests.
 *
 * @param imageUrl Remote photo URL attached to the review; blank renders nothing.
 * @param modifier Configuration applied to the framed image container.
 * @param enabled Whether the retry action accepts clicks if loading fails.
 */
@Composable
fun ReviewImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (imageUrl.isBlank()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ReviewImageAspectRatio)
            .clip(RoundedCornerShape(ReviewImageCornerRadius))
            .background(TransitWhite),
        contentAlignment = Alignment.Center,
    ) {
        if (LocalInspectionMode.current) {
            ReviewImageStatus(
                isLoading = false,
                message = "Saved review photo preview",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val context = LocalPlatformContext.current
            val sizeResolver = rememberConstraintsSizeResolver()
            val request = remember(imageUrl, context, sizeResolver) {
                ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(sizeResolver)
                    .crossfade(true)
                    .build()
            }
            val painter = rememberAsyncImagePainter(
                model = request,
                contentScale = ContentScale.Fit,
            )
            val state by painter.state.collectAsState()

            Image(
                painter = painter,
                contentDescription = "Photo attached to this trip review",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().then(sizeResolver),
            )

            when (state) {
                is AsyncImagePainter.State.Empty,
                is AsyncImagePainter.State.Loading,
                -> ReviewImageStatus(
                    isLoading = true,
                    message = "Loading review photo…",
                    modifier = Modifier.fillMaxSize(),
                )

                is AsyncImagePainter.State.Error -> ReviewImageStatus(
                    isLoading = false,
                    message = "Photo unavailable. Your saved review is still available.",
                    onRetry = { painter.restart() },
                    enabled = enabled,
                    modifier = Modifier.fillMaxSize(),
                )

                is AsyncImagePainter.State.Success -> Unit
            }
        }
    }
}
