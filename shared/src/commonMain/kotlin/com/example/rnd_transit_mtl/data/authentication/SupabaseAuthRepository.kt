package com.example.rnd_transit_mtl.data.authentication

import com.example.rnd_transit_mtl.data.supabase.SupabaseBackend
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
                    user?.let {
                        AccountSession.SignedIn(userId = it.id, email = it.email)
                    } ?: AccountSession.Unavailable
                }
                is SessionStatus.Authenticated -> {
                    val user = status.session.user
                    if (user == null) {
                        AccountSession.Unavailable
                    } else {
                        AccountSession.SignedIn(
                            userId = user.id,
                            email = user.email,
                        )
                    }
                }
            }
        }.distinctUntilChanged()

    override suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signUp(email: String, password: String): Boolean {
        val user = auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        return user != null
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}
