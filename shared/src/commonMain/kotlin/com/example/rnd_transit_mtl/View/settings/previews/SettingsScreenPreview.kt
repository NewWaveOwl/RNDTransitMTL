package com.example.rnd_transit_mtl.View.settings.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.View.settings.SettingsContent
import com.example.rnd_transit_mtl.ViewModels.Settings.SettingsUIState
import com.example.rnd_transit_mtl.layout.previews.NavigationPreviewHost
import com.example.rnd_transit_mtl.model.Accounts.TransportModes
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.SettingsScreenKey

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
fun SettingsScreenPreview() {
    NavigationPreviewHost(
        MainScreenKey,
        SettingsScreenKey
    ) {
        SettingsContent(
            state = SettingsUIState(mode = TransportModes.BUS, username = "Explorer"),
            newPassword = "password",
            onModeSelected = {},
            onUsernameChange = {},
            onNewPasswordChange = {},
            onOpenTripPreferences = {},
            onSave = {}
        )
    }
}
