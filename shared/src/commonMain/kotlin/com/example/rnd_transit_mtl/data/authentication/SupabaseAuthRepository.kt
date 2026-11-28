package com.example.rnd_transit_mtl.data.authentication

import com.example.rnd_transit_mtl.data.supabase.SupabaseBackend
import com.example.rnd_transit_mtl.data.trips.GUEST_TRANSFER_TOKEN
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class SupabaseAuthRepository(
    private val auth: Auth = SupabaseBackend.client.auth,
) : AuthRepository {
    override val session: Flow<AccountSession> =
        auth.sessionStatus.map { status ->
            when (status) {
                SessionStatus.Initializing -> AccountSession.Restoring
                is SessionStatus.NotAuthenticated -> AccountSession.Guest
                is SessionStatus.RefreshFailure -> {
                    // The SDK hides the expired session from currentSessionOrNull while retrying.
                    val user = try {
                        auth.sessionManager.loadSession()?.user
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    user?.toAccountSession() ?: AccountSession.Unavailable
                }
                is SessionStatus.Authenticated ->
                    status.session.user?.toAccountSession() ?: AccountSession.Unavailable
            }
        }.distinctUntilChanged()

    override suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    private fun UserInfo.toAccountSession(): AccountSession =
        if (isAnonymous == true) AccountSession.Guest else AccountSession.SignedIn(
            userId = id,
            email = email,
            guestTransferToken = (userMetadata?.get(GUEST_TRANSFER_TOKEN) as? JsonPrimitive)?.content,
        )

    override suspend fun signUp(email: String, password: String): Boolean =
        signUp(email, password, guestTransferToken = null)

    override suspend fun signUp(email: String, password: String, guestTransferToken: String?): Boolean {
        try {
            auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
                data = guestTransferToken?.let { JsonObject(mapOf(GUEST_TRANSFER_TOKEN to JsonPrimitive(it))) }
            }
        } catch (error: RestException) {
            when (error.statusCode) {
                400, 401, 403, 409, 422, 429 -> throw SignupRejected(error)
                else -> throw error
            }
        }
        // Email confirmation may leave the guest generation session active.
        // Only a permanent account session means registration signed the user in.
        val user = auth.currentSessionOrNull()?.user
        return user == null || user.isAnonymous == true
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun changePassword(password: String) {
        auth.updateUser(config = {
            this.password = password
        })
    }
}
