package com.example.rnd_transit_mtl.state

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Memory-only identity. No Saver, credentials, file, or database is used. */
class NicknameSession {
    var nickname by mutableStateOf<String?>(null)
        private set

    fun signIn(value: String): Boolean {
        val entered = value.trim()
        if (entered.isEmpty()) return false
        nickname = entered
        return true
    }
}

// One process lifetime keeps the nickname across Android activity recreation.
// A new process (or browser reload) starts a new session with no nickname.
internal val appNicknameSession = NicknameSession()

val LocalNicknameSession = compositionLocalOf<NicknameSession> {
    error("NicknameSession must be provided by App.")
}
