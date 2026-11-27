package com.example.rnd_transit_mtl.data.group

import com.example.rnd_transit_mtl.model.group.Group
import com.example.rnd_transit_mtl.model.group.GroupJoinCode
import com.example.rnd_transit_mtl.model.ids.GroupId
import com.example.rnd_transit_mtl.model.ids.GroupTripId
import com.example.rnd_transit_mtl.model.trip.Trip
import kotlinx.coroutines.flow.Flow

/**
 * Live state of one group, as delivered by [GroupRepository.observeGroup].
 */
sealed interface GroupSync {
    /**
     * The latest group snapshot from the server.
     *
     * @property group Current members and shared trip.
     */
    data class Live(val group: Group) : GroupSync

    /** The connection dropped; the last snapshot may be out of date until it reconnects. */
    data object Reconnecting : GroupSync

    /** The group is gone for this user: the host left, or this user is no longer a member. */
    data object Closed : GroupSync
}

/**
 * Why a group action failed. Each case has a message that can be shown to the user.
 *
 * @param message Short, user-facing explanation.
 */
sealed class GroupFailure(message: String) : Exception(message) {
    /** The code does not match an open group, or the group closed when its host left. */
    class InvalidCode : GroupFailure("That code doesn't match an open group. It may have expired.")

    /** The user is already in another open group and must leave it first. */
    class AlreadyInGroup : GroupFailure("You're already in a group. Leave it before joining another.")

    /** Only the host can do this. */
    class NotHost : GroupFailure("Only the group's host can share a trip.")

    /** The user is no longer a member of this group. */
    class NotMember : GroupFailure("You're no longer in this group.")

    /** The shared trip is no longer the group's current trip. */
    class TripUnavailable : GroupFailure("That group trip is no longer available.")

    /** The server could not find a free code. Trying again normally works. */
    class CodeGenerationFailed : GroupFailure("Couldn't create a group code. Please try again.")

    /** The server could not be reached. */
    class Network : GroupFailure("Can't reach the server. Check your connection and try again.")

    /** Any other server refusal. */
    class Unexpected : GroupFailure("Something went wrong with the group. Please try again.")
}

/**
 * Creates, joins and follows live groups for the signed-in user.
 *
 * Every write is checked by the server; failures are thrown as [GroupFailure].
 */
internal interface GroupRepository {
    /** Returns the open group the user is in, or null. */
    suspend fun currentGroup(): Group?

    /** Creates a group hosted by the user and returns it with its join code. */
    suspend fun createGroup(): Group

    /**
     * Joins an open group. Joining the same group again returns it unchanged.
     *
     * @param code Code typed by the user.
     */
    suspend fun joinGroup(code: GroupJoinCode): Group

    /**
     * Leaves a group. When the host leaves, the group closes for everyone.
     *
     * @param id Group to leave.
     */
    suspend fun leaveGroup(id: GroupId)

    /**
     * Shares the host's planned route with the group.
     *
     * @param id Group the user hosts.
     * @param trip Route to share; only its route, title, description and image are sent.
     * @return The new shared group trip.
     */
    suspend fun shareTrip(id: GroupId, trip: Trip): GroupTripId

    /**
     * Records the user as a participant and returns their own copy of the shared trip,
     * ready to start: a new trip ID, Generated status, and [Trip.groupTripId] set.
     *
     * @param id The group's current shared trip.
     */
    suspend fun startGroupTrip(id: GroupTripId): Trip

    /**
     * Follows a group live. Emits a snapshot once connected and after every change.
     *
     * @param id Group to follow.
     */
    fun observeGroup(id: GroupId): Flow<GroupSync>
}
