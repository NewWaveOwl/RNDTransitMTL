package com.example.rnd_transit_mtl.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.ui.theme.TransitMain

/**
 * Places shared navigation above screen content and applies system and Scaffold padding.
 *
 * @param content Screen content rendered below the shared header.
 */
@Composable
fun MainLayout(content: @Composable () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = TransitMain,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { SharedTopBar() }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            content()
        }
    }
}
