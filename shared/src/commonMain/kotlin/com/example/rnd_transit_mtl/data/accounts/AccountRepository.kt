package com.example.rnd_transit_mtl.data.accounts
import com.example.rnd_transit_mtl.data.remote.dto.AccountDto
import com.example.rnd_transit_mtl.data.supabase.SupabaseBackend
import com.example.rnd_transit_mtl.model.Accounts.Account
import com.example.rnd_transit_mtl.model.ids.UserId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

internal class AccountRepository(
    private val authenticatedUserId: UserId,
    private val client: SupabaseClient = SupabaseBackend.client,
) : IAccountRepository {

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
            }).decodeList<Account>()

        return updated.isNotEmpty()
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
}
