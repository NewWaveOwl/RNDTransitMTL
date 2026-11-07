package com.example.rnd_transit_mtl.data.authentication

import androidx.compose.runtime.compositionLocalOf
import com.example.rnd_transit_mtl.data.authentication.AccountSession
import com.example.rnd_transit_mtl.data.authentication.AuthRepository

internal val LocalAccountSession =
    compositionLocalOf<AccountSession> { AccountSession.Guest }

internal val LocalAuthRepository = compositionLocalOf<AuthRepository> {
    error("AuthRepository must be provided by App")
}