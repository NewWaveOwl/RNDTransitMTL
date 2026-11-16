package com.example.rnd_transit_mtl.ViewModels.Settings

import com.example.rnd_transit_mtl.ViewModels.Accounts.ProfileUiState
import com.example.rnd_transit_mtl.ViewModels.Accounts.ProfileViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rnd_transit_mtl.data.accounts.IAccountRepository
import com.example.rnd_transit_mtl.data.authentication.AuthRepository
import com.example.rnd_transit_mtl.data.remote.dto.AccountDto
import com.example.rnd_transit_mtl.model.Accounts.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlin.time.TimeMark
import kotlin.time.TimedValue
import com.example.rnd_transit_mtl.model.Accounts.TransportModes


internal data class SettingsUIState(
    val mode: TransportModes = TransportModes.WALK,
    val displayName: String = "",
    val availableTime: DateTimeUnit.TimeBased = DateTimeUnit.MINUTE * 30,
    val username: String = ""
)

internal class SettingsViewModel(
    private val accountRepository: IAccountRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(SettingsUIState())
    val state = mutableState.asStateFlow()

//    init {
//        viewModelScope.launch {
//            try {
//
//            } catch (cancelled: CancellationException) {
//
//            } catch (_: Exception) {
//
//            }
//        }
//    }


    suspend fun updatePassword(password: String) {

        authRepository.changePassword(password)

    }

    suspend fun updateSettings() {

        val preference: Preferences = Preferences(null, state.value.mode, state.value.availableTime)
        accountRepository.updateAccount(AccountDto(preference, state.value.username))

    }



}
