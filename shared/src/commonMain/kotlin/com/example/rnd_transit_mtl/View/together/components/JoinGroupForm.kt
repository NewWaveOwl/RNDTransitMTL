package com.example.rnd_transit_mtl.View.together.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.V3Button
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp spacing and corner radius of the join form on the teal panel.
private val JoinFormSpacing = 12.dp
private val JoinFieldCornerRadius = 16.dp

/**
 * Code field and Join button shown under "Join with a code".
 *
 * @param code Code typed so far, already normalized by the ViewModel.
 * @param codeValid Whether [code] is complete; Join is disabled until it is.
 * @param busy Whether a request is running; disables the form.
 * @param onCodeChange Called with the raw text after each edit.
 * @param onJoin Called when the user submits a valid code.
 */
@Composable
internal fun JoinGroupForm(
    code: String,
    codeValid: Boolean,
    busy: Boolean,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(JoinFormSpacing)) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            enabled = !busy,
            singleLine = true,
            label = { Text("6-character code") },
            textStyle = TextStyle(fontSize = 22.sp, letterSpacing = 4.sp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { if (codeValid && !busy) onJoin() }),
            shape = RoundedCornerShape(JoinFieldCornerRadius),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TransitSelected,
                unfocusedBorderColor = TransitWhite,
                focusedTextColor = TransitWhite,
                unfocusedTextColor = TransitWhite,
                focusedLabelColor = TransitSelected,
                unfocusedLabelColor = TransitWhite,
                cursorColor = TransitSelected,
            ),
        )
        V3Button(label = "Join group", onClick = onJoin, enabled = codeValid && !busy)
    }
}
