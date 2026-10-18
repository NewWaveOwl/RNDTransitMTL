package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.ReviewImage
import com.example.rnd_transit_mtl.ui.ReviewImageStatus
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/** Fixed preview states: no store, timer, navigation or network request. */
@Preview(name = "Saved review photo — phone", widthDp = 320, showBackground = true)
@Composable
fun ReviewImagePhonePreview() {
    RNDTransitTheme {
        ReviewImage(
            imageUrl = "https://example.com/review-photo.jpg",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "Review photo — loading", widthDp = 320, showBackground = true)
@Composable
fun ReviewImageLoadingPreview() {
    RNDTransitTheme {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(TransitWhite)) {
            ReviewImageStatus(
                isLoading = true,
                message = "Loading review photo…",
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Preview(name = "Review photo — unavailable", widthDp = 320, showBackground = true)
@Composable
fun ReviewImageUnavailablePreview() {
    RNDTransitTheme {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(TransitWhite)) {
            ReviewImageStatus(
                isLoading = false,
                message = "Photo unavailable. Your saved review is still available.",
                onRetry = {},
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Preview(name = "Saved review photo — desktop", widthDp = 640, showBackground = true)
@Composable
fun ReviewImageDesktopPreview() {
    RNDTransitTheme {
        ReviewImage(
            imageUrl = "https://example.com/review-photo.jpg",
            modifier = Modifier.padding(16.dp)
        )
    }
}
