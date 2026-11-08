package com.example.rnd_transit_mtl.ViewModels.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rnd_transit_mtl.data.authentication.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class AuthUiState(
    val submitting: Boolean = false,
    val error: String? = null,
    val confirmationEmail: String? = null,
)

internal class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state = mutableState.asStateFlow()

    fun signIn(email: String, password: String) {
        if (mutableState.value.submitting) return
        if (email.isBlank() || password.isEmpty()) {
            mutableState.value = AuthUiState(error = "Enter your email and password.")
            return
        }

        mutableState.value = AuthUiState(submitting = true)
        viewModelScope.launch {
            try {
                repository.signIn(email, password)
                mutableState.value = AuthUiState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = AuthUiState(
                    error = "Couldn’t sign in. Check your email, password, and connection, then try again.",
                )
            }
        }
    }

    fun signUp(email: String, password: String) {
        if (mutableState.value.submitting) return
        mutableState.value = AuthUiState(submitting = true)
        viewModelScope.launch {
            try {
                val needsConfirmation = repository.signUp(email, password)
                mutableState.value = AuthUiState(
                    confirmationEmail = email.trim().takeIf { needsConfirmation },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = AuthUiState(error = "Couldn’t create your account. Check your details and try again.")
            }
        }
    }

    fun dismissCheckEmail() {
        mutableState.value = AuthUiState()
    }
}
