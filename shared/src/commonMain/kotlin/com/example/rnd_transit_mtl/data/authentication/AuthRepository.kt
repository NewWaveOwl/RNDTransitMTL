package com.example.rnd_transit_mtl.data.authentication

import kotlinx.coroutines.flow.Flow

internal sealed interface AccountSession {
    data object Restoring : AccountSession
    data object Guest : AccountSession
    data object Unavailable : AccountSession

    data class SignedIn(
        val userId: String,
        val email: String?,
    ) : AccountSession
}

internal interface AuthRepository {
    val session: Flow<AccountSession>

    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun signOut()
}