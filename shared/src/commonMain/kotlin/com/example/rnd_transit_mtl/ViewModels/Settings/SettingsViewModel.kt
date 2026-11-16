package com.example.rnd_transit_mtl.ViewModels.Settings

import com.example.rnd_transit_mtl.ViewModels.Accounts.ProfileUiState
import com.example.rnd_transit_mtl.ViewModels.Accounts.ProfileViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rnd_transit_mtl.data.accounts.IAccountRepository
import com.example.rnd_transit_mtl.data.authentication.AuthRepository
import com.example.rnd_transit_mtl.data.remote.dto.AccountDto
import com.example.rnd_transit_mtl.model.Accounts.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlin.time.TimeMark
import kotlin.time.TimedValue
import com.example.rnd_transit_mtl.model.Accounts.TransportModes


internal enum class SaveStatus { Idle, Saving, Saved, Failed }

internal data class SettingsUIState(
    val mode: TransportModes = TransportModes.WALK,
    val displayName: String = "",
    val availableTime: DateTimeUnit.TimeBased = DateTimeUnit.MINUTE * 30,
    val username: String = "",
    val saveStatus: SaveStatus = SaveStatus.Idle,
)

internal class SettingsViewModel(
    private val accountRepository: IAccountRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(SettingsUIState())
    val state = mutableState.asStateFlow()

    val account = accountRepository.loadedAccount;

    /** Fill the form once from the first loaded account, so later edits aren't overwritten. */
    init {
        viewModelScope.launch {
            val loaded = accountRepository.loadedAccount.filterNotNull().first()
            mutableState.update {
                it.copy(
                    username = loaded.username,
                    mode = loaded.preferences?.transportMode ?: it.mode,
                    availableTime = loaded.preferences?.availabeTime ?: it.availableTime
                )
            }
        }
    }

    // Editing after a save clears the old "Saved" / "Couldn't save" message.
    fun onModeSelected(mode: TransportModes) {
        mutableState.update { it.copy(mode = mode, saveStatus = it.saveStatus.afterEdit()) }
    }

    fun onUsernameChange(username: String) {
        mutableState.update { it.copy(username = username, saveStatus = it.saveStatus.afterEdit()) }
    }

    private fun SaveStatus.afterEdit() = if (this == SaveStatus.Saving) this else SaveStatus.Idle


    /**
     * Saves the username and preferences, then the new password if one was typed.
     *
     * The outcome is reported through [SettingsUIState.saveStatus]; a row that
     * wasn't updated (e.g. blocked by RLS) counts as a failure, not a success.
     */
    fun save(newPassword: String) {
        if (mutableState.value.saveStatus == SaveStatus.Saving) return
        mutableState.update { it.copy(saveStatus = SaveStatus.Saving) }

        viewModelScope.launch {
            val status = try {
                val preference = Preferences(null, state.value.mode, state.value.availableTime)
                val updated = accountRepository.updateAccount(AccountDto(preference, state.value.username))
                if (updated && newPassword.isNotEmpty()) authRepository.changePassword(newPassword)
                if (updated) SaveStatus.Saved else SaveStatus.Failed
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                println("Settings save failed: $e")
                SaveStatus.Failed
            }
            mutableState.update { it.copy(saveStatus = status) }
        }
    }



}
