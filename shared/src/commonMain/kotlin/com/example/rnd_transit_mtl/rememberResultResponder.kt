package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable

@Composable
expect fun rememberResultResponder(): (resultData: String) -> Unit