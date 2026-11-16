package com.example.rnd_transit_mtl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.data.supabase.SupabaseBackend
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SupabaseTestScreen() {
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Enter your confirmed test account.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Supabase connection test")

            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Test email") },
                singleLine = true,
                enabled = !busy,
            )

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Test password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !busy,
            )

            Button(
                enabled = !busy && emailInput.isNotBlank() && passwordInput.isNotEmpty(),
                onClick = {
                    busy = true
                    status = "Signing in…"
                    scope.launch {
                        try {
                            val auth = SupabaseBackend.client.auth
                            auth.signInWith(Email) {
                                email = emailInput.trim()
                                password = passwordInput
                            }
                            status = if (auth.currentSessionOrNull() != null) {
                                "PASS: Supabase returned an authenticated session."
                            } else {
                                "FAIL: Sign-in returned without a session."
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            status = "FAIL: ${failure::class.simpleName}"
                        } finally {
                            passwordInput = ""
                            busy = false
                        }
                    }
                },
            ) {
                Text("Test sign-in")
            }

            Text(status)
        }
    }
}