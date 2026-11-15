package com.example.rnd_transit_mtl.data.accounts

import androidx.compose.runtime.staticCompositionLocalOf

/** The signed-in user's account repository; null for guests. */
internal val LocalAccountRepository = staticCompositionLocalOf<IAccountRepository?> { null }
