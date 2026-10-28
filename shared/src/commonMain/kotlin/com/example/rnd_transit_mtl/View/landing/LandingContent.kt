package com.example.rnd_transit_mtl.View.landing

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
import androidx.compose.material3.TextButton
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

// Layout settings — the form fills narrow screens and stops growing at LandingFormMaxWidth.
private val LandingFormMaxWidth = 480.dp
private val LandingPagePadding = 24.dp
private val LandingCardPadding = 24.dp
private val LandingCardCornerRadius = 24.dp
private val LandingSectionSpacing = 24.dp
private val LandingFormFieldSpacing = 18.dp
private val LandingContinueButtonMinHeight = 52.dp
private val LandingGuestButtonMinHeight = 48.dp
private val LandingGuestButtonBottomPadding = 8.dp

/**
 * Stateless landing form with a guest shortcut at the bottom of the page.
 *
 * @param nickname Current nickname draft.
 * @param showError Whether the nickname was rejected.
 * @param onNicknameChange Updates the nickname draft.
 * @param onContinue Requests entry with the supplied nickname.
 * @param onContinueAsGuest Requests entry using the placeholder guest identity.
 * @param modifier Layout supplied by the caller.
 */
@Composable
fun LandingContent(
    nickname: String,
    showError: Boolean,
    onNicknameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onContinueAsGuest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val continueAction = {
        focusManager.clearFocus()
        onContinue()
    }

    Column(
        modifier = modifier.fillMaxSize().background(TransitMain)
            .safeDrawingPadding().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = LandingFormMaxWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(LandingPagePadding),
                verticalArrangement = Arrangement.spacedBy(LandingSectionSpacing)
            ) {
                Text(
                    "RND Transit",
                    color = TransitHighlight,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(TransitWhite, RoundedCornerShape(LandingCardCornerRadius)).padding(LandingCardPadding),
                    verticalArrangement = Arrangement.spacedBy(LandingFormFieldSpacing)
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = LandingContinueButtonMinHeight)
                    ) { Text("Continue to GO") }
                    Text("Demo access — no password or account required.",
                        color = TransitMain, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TextButton(
            onClick = {
                focusManager.clearFocus()
                onContinueAsGuest()
            },
            modifier = Modifier.padding(bottom = LandingGuestButtonBottomPadding).heightIn(min = LandingGuestButtonMinHeight)
        ) {
            Text(
                "Continue as guest",
                color = TransitWhite,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
