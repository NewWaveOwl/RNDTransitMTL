package com.example.rnd_transit_mtl.data.authentication

import kotlinx.coroutines.flow.Flow

internal sealed interface AccountSession {
    data object Restoring : AccountSession
    data object Guest : AccountSession
    data object Unavailable : AccountSession

    data class SignedIn(
        val userId: String,
        val email: String?,
        val guestTransferToken: String? = null,
    ) : AccountSession
}

internal class SignupRejected(cause: Exception) : Exception(cause.message, cause)

internal interface AuthRepository {
    val session: Flow<AccountSession>

    suspend fun signIn(email: String, password: String)
    /** True when signup requires email verification before a non-anonymous account session exists. */
    suspend fun signUp(email: String, password: String): Boolean
    suspend fun signUp(email: String, password: String, guestTransferToken: String?): Boolean =
        signUp(email, password)

    suspend fun signOut()

    suspend fun changePassword(password: String)
}
