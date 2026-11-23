package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable
import kotlinx.browser.window

@Composable
actual fun rememberResultResponder(): (resultData: String) -> Unit {
    return { resultData ->
        val encodedResult = resultData.replace(" ", "%20")
        window.open("http://localhost:8081/#/callback?result=$encodedResult", "_self")
    }
}
