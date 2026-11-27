package com.example.rnd_transit_mtl.View.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rnd_transit_mtl.ViewModels.Settings.SettingsViewModel
import com.example.rnd_transit_mtl.navigation.AuthScreenKey
import com.example.rnd_transit_mtl.navigation.LocalNavigator
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.SettingsScreenKey

// Layout settings — delegated to SettingsContent.kt.

/**
 * Connects the Settings design to [SettingsViewModel], or renders a sign-in prompt for guests.
 *
 * The new password is held only in this composable (never saved state) and is cleared after Save.
 * "Open trip preferences" returns to the last planner entry already in the back stack.
 *
 * @param viewModel Destination-scoped ViewModel owning editable settings state, or null for guests.
 */
@Composable
internal fun SettingsScreen(viewModel: SettingsViewModel?) {
    val navigator = LocalNavigator.current
    if (viewModel == null) {
        GuestSettingsContent(
            onSignIn = {
                if (navigator.current == SettingsScreenKey) {
                    navigator.navigate(AuthScreenKey())
                }
            },
        )
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var newPassword by remember { mutableStateOf("") }

    SettingsContent(
        state = state,
        newPassword = newPassword,
        onModeSelected = viewModel::onModeSelected,
        onUsernameChange = viewModel::onUsernameChange,
        onNewPasswordChange = { newPassword = it },
        onOpenTripPreferences = { navigator.popUntil(MainScreenKey) },
        onSave = {
            viewModel.save(newPassword)
            newPassword = ""
        },
    )
}
