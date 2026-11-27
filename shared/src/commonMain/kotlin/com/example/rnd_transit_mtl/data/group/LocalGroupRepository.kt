package com.example.rnd_transit_mtl.data.group

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The signed-in user's group repository, provided per account by App.
 * Null for guests and before sign-in, since groups need an account.
 */
internal val LocalGroupRepository = staticCompositionLocalOf<GroupRepository?> { null }
