package com.example.rnd_transit_mtl.data.remote.dto

import com.example.rnd_transit_mtl.model.group.Group
import com.example.rnd_transit_mtl.model.group.GroupJoinCode
import com.example.rnd_transit_mtl.model.group.GroupMember
import com.example.rnd_transit_mtl.model.ids.GroupId
import com.example.rnd_transit_mtl.model.ids.GroupTripId
import com.example.rnd_transit_mtl.model.ids.UserId
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One group_members row embedded in a group read. */
@Serializable
data class GroupMemberDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("joined_at") val joinedAt: String,
)

/**
 * One groups row. A full read embeds its members; function results leave them out.
 */
@Serializable
data class GroupDto(
    val id: String,
    @SerialName("join_code") val joinCode: String,
    @SerialName("host_id") val hostId: String,
    @SerialName("current_group_trip_id") val currentGroupTripId: String? = null,
    val members: List<GroupMemberDto> = emptyList(),
)

/** The part of a group_trips row the app needs besides the shared route. */
@Serializable
data class GroupTripRefDto(val id: String)

/**
 * Converts a group read into a [Group]; members are ordered by join time.
 *
 * @return The group, or null when the row breaks a model rule.
 */
fun GroupDto.toDomainOrNull(): Group? = runCatchingInvalid {
    Group(
        id = GroupId(id),
        joinCode = GroupJoinCode.parse(joinCode),
        hostId = UserId(hostId),
        members = members
            .map { GroupMember(UserId(it.userId), it.displayName, Instant.parse(it.joinedAt).toEpochMilliseconds()) }
            .sortedBy { it.joinedAt },
        currentTripId = currentGroupTripId?.let(::GroupTripId),
    )
}
