package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.MainScreenKey
import com.example.rnd_transit_mtl.SettingsScreen
import com.example.rnd_transit_mtl.SettingsScreenKey

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
