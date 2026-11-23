package com.example.rnd_transit_mtl.ui.global_ui_ellements.refresh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.AnimatedStartupOverlay
import com.example.rnd_transit_mtl.state.refresh.InactivityRefreshCoordinator
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/** Replays the boot animation while the existing screen and its state remain composed underneath. */
@Composable
internal fun BoxScope.InactivityRefreshFeedback(refresh: InactivityRefreshCoordinator) {
    if (refresh.cycle > 0) key(refresh.cycle) {
        Box(Modifier.fillMaxSize().semantics {
            if (refresh.isRefreshing) contentDescription = "Refreshing app data"
        }) {
            AnimatedStartupOverlay(readyToReveal = { !refresh.isRefreshing }, revealTimeoutMillis = null)
            if (refresh.isRefreshing) Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = TransitWhite)
                Text("Refreshing app data…", color = TransitWhite)
            }
        }
    }
    if (refresh.failed && !refresh.isRefreshing) {
        Snackbar(Modifier.align(Alignment.BottomCenter).padding(12.dp), action = {
            TextButton(onClick = refresh::request) { Text("Retry") }
        }) { Text("Could not refresh app data. Your current screen and trip are kept.") }
    }
}
