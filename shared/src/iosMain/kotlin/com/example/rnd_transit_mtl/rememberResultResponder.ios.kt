package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable

// No external caller to return a result to on this platform.
@Composable
actual fun rememberResultResponder(): (resultData: String) -> Unit = {}
