package com.example.rnd_transit_mtl.View.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.View.main.TripLabelValueField
import com.example.rnd_transit_mtl.ViewModels.Settings.SaveStatus
import com.example.rnd_transit_mtl.ViewModels.Settings.SettingsUIState
import com.example.rnd_transit_mtl.layout.PageTitle
import com.example.rnd_transit_mtl.model.Accounts.TransportModes
import com.example.rnd_transit_mtl.navigation.SettingsScreenKey
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TransitFilledButton
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TransitOutlineButton
import com.example.rnd_transit_mtl.ui.global_ui_ellements.location.LocationAccuracySettingsEntry
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dimensions in dp from SettingsUIDesign.png (412 px reference width).
private val SettingsHorizontalPadding = 16.dp
private val SettingsVerticalPadding = 16.dp
private val SettingsSectionSpacing = 16.dp
private val TransportSectionInset = 8.dp
private val TransportChipHeight = 44.dp
private val TransportChipCornerRadius = 14.dp
private val TransportChipSpacing = 8.dp
private val SettingsFieldMinimumHeight = 66.dp
private val SettingsFieldCornerRadius = 16.dp
private val SettingsButtonHeight = 52.dp

/**
 * Stateless Settings layout, previewable without a ViewModel.
 *
 * @param state Current editable settings.
 * @param newPassword Password typed so far; empty means "don't change it".
 * @param onModeSelected Requests a new default transport mode.
 * @param onUsernameChange Requests a display-name edit.
 * @param onNewPasswordChange Requests a password-field edit.
 * @param onOpenTripPreferences Opens the planner's trip preferences.
 * @param onSave Saves the defaults and, if typed, the new password.
 */
@Composable
internal fun SettingsContent(
    state: SettingsUIState,
    newPassword: String,
    onModeSelected: (TransportModes) -> Unit,
    onUsernameChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onOpenTripPreferences: () -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(TransitWhite)) {
        PageTitle(title = SettingsScreenKey.screenTitle, layoutScale = 1f, highlighted = false)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(TransitWhite)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsHorizontalPadding, vertical = SettingsVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsSectionSpacing),
        ) {
            Text(
                "Settings",
                color = TransitComplementary,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
            )

            TripLabelValueField(
                label = "Default available time",
                value = "${state.availableTime.duration.inWholeMinutes} minutes",
                showLocationIcon = true,
            )

            DefaultTransportSection(state.mode, onModeSelected)

            TransitOutlineButton(
                "Open trip preferences",
                onClick = onOpenTripPreferences,
                height = SettingsButtonHeight,
                fontSize = 15.sp,
            )

            LocationAccuracySettingsEntry()

            SettingsTextField("Display name", state.username, onUsernameChange)

            SettingsTextField("New password", newPassword, onNewPasswordChange, isPassword = true)

            val saving = state.saveStatus == SaveStatus.Saving
            TransitFilledButton(
                if (saving) "Saving…" else "Save changes",
                onClick = onSave,
                height = SettingsButtonHeight,
                fontSize = 15.sp,
                enabled = state.username.isNotBlank() && !saving,
            )

            when (state.saveStatus) {
                SaveStatus.Saved -> Text(
                    "Changes saved.",
                    color = TransitComplementary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )

                SaveStatus.Failed -> Text(
                    "Couldn’t save your changes. Check your connection and try again.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )

                else -> {}
            }

            Text(
                "Saved defaults apply to the next request, not the route already in progress.",
                color = TransitComplementary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

/**
 * Guest fallback for Settings when no signed-in account repository is available.
 *
 * @param onSignIn Opens the sign-in screen so the guest can authenticate and manage settings.
 */
@Composable
internal fun GuestSettingsContent(onSignIn: () -> Unit) {
    Column(Modifier.fillMaxSize().background(TransitWhite)) {
        PageTitle(title = SettingsScreenKey.screenTitle, layoutScale = 1f, highlighted = false)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(TransitWhite)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsHorizontalPadding, vertical = SettingsVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsSectionSpacing),
        ) {
            Text(
                "Settings",
                color = TransitComplementary,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Sign in to manage account settings.",
                color = TransitComplementary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
            TransitOutlineButton(
                "Sign in",
                onClick = onSignIn,
                height = SettingsButtonHeight,
                fontSize = 15.sp,
            )
        }
    }
}

/**
 * Heading, hint and one chip per [TransportModes]; exactly one mode is selected.
 *
 * @param selected Current default mode.
 * @param onModeSelected Requests a new default mode.
 */
@Composable
private fun DefaultTransportSection(
    selected: TransportModes,
    onModeSelected: (TransportModes) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TransportSectionInset, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Default transport",
            color = TransitComplementary,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Choose only modes supported by the routing service.",
            color = TransitComplementary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(TransportChipSpacing),
        ) {
            TransportModes.entries.forEach { mode ->
                TransportModeChip(mode.label, mode == selected, { onModeSelected(mode) }, Modifier.weight(1f))
            }
        }
    }
}

/**
 * Green filled chip when selected; plain blue text otherwise, as in the design.
 *
 * @param label Visible mode name.
 * @param selected Whether this is the current default.
 * @param onClick Selects this mode.
 * @param modifier Width supplied by the row.
 */
@Composable
private fun TransportModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(TransportChipCornerRadius)
    Box(
        modifier = modifier
            .height(TransportChipHeight)
            .clip(shape)
            .background(if (selected) TransitSelected else TransitWhite, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) TransitComplementary else TransitMain,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Outlined label-above-value field matching the design's Display name and New password boxes.
 *
 * @param label Caption above the value.
 * @param value Current text.
 * @param onValueChange Requests a text edit.
 * @param isPassword Masks the text and shows the password keyboard.
 */
@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
) {
    val shape = RoundedCornerShape(SettingsFieldCornerRadius)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = TransitComplementary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        ),
        cursorBrush = SolidColor(TransitMain),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SettingsFieldMinimumHeight)
                    .background(TransitWhite, shape)
                    .border(1.dp, TransitMain, shape)
                    .padding(horizontal = SettingsHorizontalPadding, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label, color = TransitComplementary, fontSize = 12.sp, lineHeight = 17.sp)
                innerTextField()
            }
        },
    )
}

/** Display name for each mode, e.g. WALK -> "Walk". */
private val TransportModes.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }
