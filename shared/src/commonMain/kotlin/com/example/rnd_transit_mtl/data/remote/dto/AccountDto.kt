package com.example.rnd_transit_mtl.data.remote.dto

import com.example.rnd_transit_mtl.model.Accounts.Preferences
import kotlinx.serialization.Serializable

/**
 * Backend form of an Account's editable fields: the username and the
 * preferences, which are stored as a jsonb object.
 */
@Serializable
data class AccountDto(
    val preferences: Preferences?,
    val username: String
)
