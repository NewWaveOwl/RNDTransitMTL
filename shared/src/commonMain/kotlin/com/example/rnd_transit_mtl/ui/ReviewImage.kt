package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Renders a saved review photo only while its expanded review is composed.
 * Coil handles caching/cancellation; image loading never changes trip state.
 * Fit preserves the complete photo. Previews do not issue network requests.
 */
@Composable
fun ReviewImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (imageUrl.isBlank()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(TransitWhite),
        contentAlignment = Alignment.Center
    ) {
        if (LocalInspectionMode.current) {
            ReviewImageStatus(
                isLoading = false,
                message = "Saved review photo preview",
                modifier = Modifier.fillMaxSize()
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
                contentScale = ContentScale.Fit
            )
            val state by painter.state.collectAsState()

            Image(
                painter = painter,
                contentDescription = "Photo attached to this trip review",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().then(sizeResolver)
            )

            when (state) {
                is AsyncImagePainter.State.Empty,
                is AsyncImagePainter.State.Loading -> ReviewImageStatus(
                    isLoading = true,
                    message = "Loading review photo…",
                    modifier = Modifier.fillMaxSize()
                )
                is AsyncImagePainter.State.Error -> ReviewImageStatus(
                    isLoading = false,
                    message = "Photo unavailable. Your saved review is still available.",
                    onRetry = { painter.restart() },
                    enabled = enabled,
                    modifier = Modifier.fillMaxSize()
                )
                is AsyncImagePainter.State.Success -> Unit
            }
        }
    }
}

/** Static status component shared by runtime loading/error states and previews. */
@Composable
internal fun ReviewImageStatus(
    isLoading: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = TransitMain,
                modifier = Modifier.size(28.dp)
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
                enabled = enabled,
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
