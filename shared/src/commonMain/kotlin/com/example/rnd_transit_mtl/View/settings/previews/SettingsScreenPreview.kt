package com.example.rnd_transit_mtl.View.settings.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.View.settings.SettingsScreen
import com.example.rnd_transit_mtl.navigation.SettingsScreenKey
import com.example.rnd_transit_mtl.layout.previews.NavigationPreviewHost

@Preview(showBackground = true, widthDp = 402, heightDp = 716)
@Composable
fun SettingsScreenPreview() {
    NavigationPreviewHost(
        MainScreenKey,
        SettingsScreenKey
    ) {
        SettingsScreen()
    }
}
