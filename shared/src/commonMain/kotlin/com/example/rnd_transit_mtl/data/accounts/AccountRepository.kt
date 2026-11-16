package com.example.rnd_transit_mtl.data.accounts
import com.example.rnd_transit_mtl.data.authentication.AccountSession
import com.example.rnd_transit_mtl.data.remote.dto.AccountDto
import com.example.rnd_transit_mtl.data.supabase.SupabaseBackend
import com.example.rnd_transit_mtl.model.Accounts.Account
import com.example.rnd_transit_mtl.model.ids.UserId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpVerifyResult
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AccountRepository(
    private val authenticatedUserId: UserId,
    private val client: SupabaseClient = SupabaseBackend.client,
) : IAccountRepository {

    private val mutableAccount = MutableStateFlow<AccountDto?>(null)
    override val loadedAccount: StateFlow<AccountDto?> = mutableAccount.asStateFlow()

    override suspend fun deleteAccount() {
        client.from(TABLE).delete {
            filter {
                eq("user_id", authenticatedUserId.value)
            }
        }
    }

    override suspend fun updateAccount(account: AccountDto): Boolean {
        val updated = client.from(TABLE).update(
            {
                set("username", account.username)
                set("preferences", account.preferences)
            },
            {
                select()
                filter {
                    eq("user_id", authenticatedUserId.value)
                }
            }).decodeList<Account>().firstOrNull()?.toDto()

        // Publish the saved row so screens reading loadedAccount (Profile, Settings) see the change.
        if (updated != null) mutableAccount.value = updated
        return updated != null
    }

    override suspend fun getAccount(): AccountDto? {
        return client.from(TABLE)
            .select {
                filter {
                    eq("user_id", authenticatedUserId.value)
                }
            }
            .decodeSingleOrNull<Account>()
            ?.toDto()
    }

    private companion object {
        const val TABLE = "users"
    }

    suspend fun ensureAccount(): Unit {

        client.from(TABLE).upsert(Account(authenticatedUserId, UsernameGenerator.generate(), null)) {
            onConflict = "user_id"
            ignoreDuplicates = true
        }
        if (mutableAccount.value == null) {

            mutableAccount.value = getAccount()

        }

    }
}
