package com.example.rnd_transit_mtl

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * Launches the shared application in a desktop window.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "RND_Transit_MTL",
    ) {
        App()
    }
}