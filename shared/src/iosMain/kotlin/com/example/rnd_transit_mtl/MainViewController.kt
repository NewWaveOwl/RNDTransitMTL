package com.example.rnd_transit_mtl

import androidx.compose.ui.window.ComposeUIViewController

/**
 * Creates the iOS view controller hosting the shared transit application.
 *
 * @return UIKit view controller containing the shared Compose interface.
 */
fun MainViewController() = ComposeUIViewController { App() }