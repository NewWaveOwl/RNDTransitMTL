package com.example.rnd_transit_mtl.ViewModels.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rnd_transit_mtl.data.accounts.AccountRepository
import com.example.rnd_transit_mtl.data.accounts.IAccountRepository
import com.example.rnd_transit_mtl.data.authentication.AuthRepository
import com.example.rnd_transit_mtl.data.remote.dto.AccountDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ProfileViewModel(private val accountRepository: IAccountRepository, val authRepository:  AuthRepository) : ViewModel() {

    suspend fun loadAccount(): AccountDto? {

        return accountRepository.getAccount()

    }

    fun signOut() {

        authRepository.signOut()

    }

}
