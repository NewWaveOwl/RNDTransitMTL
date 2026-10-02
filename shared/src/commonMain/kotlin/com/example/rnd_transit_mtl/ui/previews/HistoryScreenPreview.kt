package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.rnd_transit_mtl.HistoryScreen
import com.example.rnd_transit_mtl.HistoryScreenKey
import com.example.rnd_transit_mtl.LocalNavigator
import com.example.rnd_transit_mtl.MainScreenKey
import com.example.rnd_transit_mtl.Navigator
import com.example.rnd_transit_mtl.backStackConfig
import com.example.rnd_transit_mtl.layout.MainLayout
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

/**
 * Previews History with the shared theme, header, and navigator provider.
 */
@Preview(showBackground = true, widthDp = 402, heightDp = 716)
@Composable
fun HistoryScreenPreview() {
    val backStack = rememberNavBackStack(backStackConfig, MainScreenKey, HistoryScreenKey)
    val navigator = remember(backStack) { Navigator(backStack) }
    RNDTransitTheme {
        CompositionLocalProvider(LocalNavigator provides navigator) {
            MainLayout { HistoryScreen() }
        }
    }
}
