package com.example.rnd_transit_mtl.data.authentication

import com.example.rnd_transit_mtl.data.authentication.AccountSession
import com.example.rnd_transit_mtl.data.authentication.AuthRepository
import com.example.rnd_transit_mtl.data.supabase.SupabaseBackend
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
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
                is SessionStatus.RefreshFailure -> AccountSession.Unavailable
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

    override suspend fun signUp(email: String, password: String) {
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}