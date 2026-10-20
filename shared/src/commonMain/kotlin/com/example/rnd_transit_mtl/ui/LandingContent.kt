package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/** Stateless landing form. The parent decides whether the nickname is accepted. */
@Composable
fun LandingContent(
    nickname: String,
    showError: Boolean,
    onNicknameChange: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val continueAction = {
        focusManager.clearFocus()
        onContinue()
    }

    Box(
        modifier = modifier.fillMaxSize().background(TransitMain)
            .safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "RND Transit",
                color = TransitHighlight,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() }
            )
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(TransitWhite, RoundedCornerShape(24.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("Welcome", color = TransitMain,
                    style = MaterialTheme.typography.headlineMedium)
                Text("Choose a nickname to get started. It will appear in your Profile.",
                    color = TransitMain, style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = nickname,
                    onValueChange = onNicknameChange,
                    label = { Text("Nickname") },
                    singleLine = true,
                    isError = showError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TransitMain,
                        unfocusedTextColor = TransitMain,
                        errorTextColor = TransitMain,
                        cursorColor = TransitMain,
                        focusedLabelColor = TransitMain,
                        unfocusedLabelColor = TransitMain,
                        focusedSupportingTextColor = TransitMain,
                        unfocusedSupportingTextColor = TransitMain,
                        focusedBorderColor = TransitMain,
                        unfocusedBorderColor = TransitMain,
                        focusedContainerColor = TransitWhite,
                        unfocusedContainerColor = TransitWhite,
                        errorContainerColor = TransitWhite
                    ),
                    supportingText = {
                        Text(if (showError) "Enter a nickname." else "Any nickname is welcome.")
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { continueAction() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = continueAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TransitSelected, contentColor = TransitMain
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("Continue to GO") }
                Text("Demo access — no password or account required.",
                    color = TransitMain, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
