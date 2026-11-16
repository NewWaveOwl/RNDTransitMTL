package com.example.rnd_transit_mtl.data.accounts

import com.example.rnd_transit_mtl.data.remote.dto.AccountDto
import com.example.rnd_transit_mtl.model.Accounts.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal interface IAccountRepository {

    val loadedAccount: StateFlow<AccountDto?>
    /** Returns null if the signed-in user has no account row. */
    suspend fun getAccount(): AccountDto?
    /** Returns true if the signed-in user's account row was updated. */
    suspend fun updateAccount(account: AccountDto): Boolean
    suspend fun deleteAccount()
}
