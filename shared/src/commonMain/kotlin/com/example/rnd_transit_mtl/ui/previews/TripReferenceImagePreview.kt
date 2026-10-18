package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.ui.TripReferenceImageStatus
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

@Preview(
    name = "Reference image loading",
    showBackground = true,
    widthDp = 350,
    heightDp = 200
)
@Composable
fun TripReferenceImageLoadingPreview() {
    RNDTransitTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TransitWhite)
        ) {
            TripReferenceImageStatus(
                isLoading = true,
                message = "Loading reference image…",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(
    name = "Reference image unavailable",
    showBackground = true,
    widthDp = 350,
    heightDp = 240
)
@Composable
fun TripReferenceImageFailurePreview() {
    RNDTransitTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TransitWhite)
        ) {
            TripReferenceImageStatus(
                isLoading = false,
                message = "Reference image unavailable. " +
                        "Your trip and mock map are still available.",
                onRetry = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
