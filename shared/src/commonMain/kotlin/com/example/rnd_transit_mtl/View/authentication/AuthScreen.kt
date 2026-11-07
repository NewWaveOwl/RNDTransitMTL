package com.example.rnd_transit_mtl.View.authentication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_logo_on_blue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rnd_transit_mtl.ViewModels.authentication.AuthViewModel

@Composable
internal fun AuthScreen(
    viewModel: AuthViewModel,
    initiallyRegistering: Boolean = false,
    onContinueAsGuest: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var registering by remember(initiallyRegistering) { mutableStateOf(initiallyRegistering) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8F8F8))
            .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.confirmationEmail != null) {
            Text("Check your email", color = TransitComplementary,
                fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("We sent a confirmation link to ${state.confirmationEmail}. Confirm your address, then sign in.",
                color = TransitComplementary)
            OutlinedButton(onClick = {
                viewModel.dismissCheckEmail()
                registering = false
                password = ""
                confirmation = ""
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Back to sign in")
            }
            return@Column
        }
        Text(if (registering) "Create your account" else "Welcome back",
            color = TransitComplementary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(if (registering) "Enter your details to start planning trips."
            else "Sign in to continue planning your trips.",
            color = TransitComplementary, style = MaterialTheme.typography.bodyMedium)
        if (!registering) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(TransitMain),
                contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.rnd_logo_on_blue),
                    contentDescription = "RND Transit logo", modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            placeholder = { Text("you@example.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = authFieldColors(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            placeholder = { Text("Enter your password") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = authFieldColors(),
        )
        if (registering) OutlinedTextField(
            value = confirmation, onValueChange = { confirmation = it },
            label = { Text("Confirm password") },
            placeholder = { Text("Enter your password again") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            enabled = !state.submitting, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = authFieldColors(),
        )
        (validation ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !state.submitting,
            onClick = {
                validation = when {
                    !email.contains("@") -> "Enter a valid email address."
                    password.length < 6 -> "Password must be at least 6 characters."
                    registering && password != confirmation -> "Passwords do not match."
                    else -> null
                }
                if (validation == null) {
                    if (registering) viewModel.signUp(email, password) else viewModel.signIn(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TransitMain),
        ) {
            Text(if (state.submitting) "Please wait…" else if (registering) "Create account" else "Sign in")
        }
        OutlinedButton(onClick = { registering = !registering; validation = null }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)) {
            Text(if (registering) "Already registered? Sign in" else "Create an account", color = TransitMain)
        }
        if (!registering && onContinueAsGuest != null) OutlinedButton(onClick = onContinueAsGuest, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)) {
            Text("Continue as guest", color = TransitMain)
        }
        Text(if (registering) "You may need to confirm your email before signing in."
            else "Guests can generate basic trips. Groups and account changes require sign-in.",
            style = MaterialTheme.typography.bodySmall, color = TransitComplementary)
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TransitMain, unfocusedBorderColor = TransitMain,
    focusedTextColor = TransitComplementary, unfocusedTextColor = TransitComplementary,
    focusedLabelColor = TransitComplementary, unfocusedLabelColor = TransitComplementary,
    focusedPlaceholderColor = TransitComplementary.copy(alpha = 0.65f),
    unfocusedPlaceholderColor = TransitComplementary.copy(alpha = 0.65f),
    cursorColor = TransitMain,
    focusedContainerColor = Color(0xFFF8F8F8),
    unfocusedContainerColor = Color(0xFFF8F8F8),
)
