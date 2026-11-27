package com.example.rnd_transit_mtl.model.group

import com.example.rnd_transit_mtl.model.ids.GroupId
import com.example.rnd_transit_mtl.model.ids.GroupTripId
import com.example.rnd_transit_mtl.model.ids.UserId

/**
 * One member of an open group.
 *
 * @property userId The member's account.
 * @property displayName Name copied from the member's username when they joined.
 * @property joinedAt Join time in epoch milliseconds.
 */
data class GroupMember(
    val userId: UserId,
    val displayName: String,
    val joinedAt: Long,
) {
    init {
        require(displayName.isNotBlank()) { "Member name must not be blank." }
    }
}

/**
 * An open group as the signed-in member sees it.
 *
 * @property id Group session ID.
 * @property joinCode Code others type to join while the group is open.
 * @property hostId The member who created the group. The group closes when they leave.
 * @property members Current members, ordered by join time.
 * @property currentTripId The trip the host shared most recently, or null before any trip is shared.
 */
data class Group(
    val id: GroupId,
    val joinCode: GroupJoinCode,
    val hostId: UserId,
    val members: List<GroupMember>,
    val currentTripId: GroupTripId? = null,
) {
    /** Number of people currently in the group. */
    val partySize: Int get() = members.size

    /** The host's member entry, or null while the snapshot does not include them. */
    val host: GroupMember? get() = members.firstOrNull { it.userId == hostId }

    /**
     * @param userId Account to check.
     * @return True when [userId] hosts this group.
     */
    fun isHost(userId: UserId): Boolean = hostId == userId
}
