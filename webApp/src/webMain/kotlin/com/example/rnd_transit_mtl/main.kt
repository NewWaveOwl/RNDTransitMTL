package com.example.rnd_transit_mtl

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window

/**
 * Mounts the shared transit application in the browser Compose viewport.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ExternalUriHandler.onNewUri(window.location.href)
    window.addEventListener("hashchange") {
        ExternalUriHandler.onNewUri(window.location.href)
    }
    window.addEventListener("popstate") {
        ExternalUriHandler.onNewUri(window.location.href)
    }

    ComposeViewport {
        App()
    }
}