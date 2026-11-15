package com.example.rnd_transit_mtl.model.Accounts

import com.example.rnd_transit_mtl.data.remote.dto.AccountDto
import com.example.rnd_transit_mtl.model.ids.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Account(
    @SerialName("user_id") val userId: UserId,
    val username: String,
    val preferences: Preferences
) {

    fun toDto(): AccountDto {
        return AccountDto(preferences, username)
    }
}
