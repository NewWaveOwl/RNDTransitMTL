package com.example.rnd_transit_mtl.ViewModels.Accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rnd_transit_mtl.data.accounts.IAccountRepository
import com.example.rnd_transit_mtl.data.authentication.AuthRepository
import com.example.rnd_transit_mtl.data.remote.dto.AccountDto
import com.example.rnd_transit_mtl.model.Accounts.Account
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ProfileUiState(
    val signingOut: Boolean = false,
    val signOutError: Boolean = false,
    val account: AccountDto? = null,
    val accountLoadFailed: Boolean = false,
)

internal class ProfileViewModel(
    private val accountRepository: IAccountRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ProfileUiState())
    val state = mutableState.asStateFlow()


    init {
        viewModelScope.launch {
            try {
                viewModelScope.launch {
                    accountRepository.loadedAccount.collect { account ->
                        mutableState.update { it.copy(account = account) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(accountLoadFailed = true) }
            }
        }

    }

    fun signOut() {
        if (mutableState.value.signingOut) return
        mutableState.update { it.copy(signingOut = true, signOutError = false) }
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(signOutError = true) }
            } finally {
                mutableState.update { it.copy(signingOut = false) }
            }
        }
    }
}
