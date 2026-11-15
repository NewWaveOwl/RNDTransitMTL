package com.example.rnd_transit_mtl.data.accounts

import androidx.compose.runtime.compositionLocalOf
import com.example.rnd_transit_mtl.data.authentication.AuthRepository

internal val LocalAccountRespository = compositionLocalOf<AccountRepository?> {
    null
}