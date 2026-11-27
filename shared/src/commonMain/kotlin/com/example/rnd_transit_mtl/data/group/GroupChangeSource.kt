package com.example.rnd_transit_mtl.data.group

import com.example.rnd_transit_mtl.model.ids.GroupId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A signal that a group's server state may have changed. */
internal enum class GroupChange {
    /** Subscribed (first time or after a reconnect): events may have been missed. */
    Connected,

    /** A row of the group or its membership changed. */
    Changed,

    /** The live connection is down; changes are not being received. */
    Disconnected,
}

/**
 * Tells when to refetch a group. Events carry no data the app relies on: under row
 * security, delete events only include keys, so the repository always rereads the group.
 */
internal fun interface GroupChangeSource {
    /**
     * @param id Group to follow.
     * @return Signals until the collector stops; collecting opens the live subscription.
     */
    fun changes(id: GroupId): Flow<GroupChange>
}

/**
 * [GroupChangeSource] backed by Supabase Realtime postgres changes on groups and group_members.
 *
 * @param client Supabase client with the Realtime plugin installed.
 */
internal class RealtimeGroupChangeSource(
    private val client: SupabaseClient,
) : GroupChangeSource {
    override fun changes(id: GroupId): Flow<GroupChange> = channelFlow {
        val channel = client.channel("group-${id.value}")
        // Change flows must be registered before subscribing.
        val groupRows = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "groups"
            filter("id", FilterOperator.EQ, id.value)
        }
        val memberRows = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "group_members"
            filter("group_id", FilterOperator.EQ, id.value)
        }
        launch { merge(groupRows, memberRows).collect { send(GroupChange.Changed) } }
        launch {
            var subscribedBefore = false
            channel.status.collect { status ->
                when (status) {
                    RealtimeChannel.Status.SUBSCRIBED -> {
                        subscribedBefore = true
                        send(GroupChange.Connected)
                    }
                    RealtimeChannel.Status.SUBSCRIBING,
                    RealtimeChannel.Status.UNSUBSCRIBED ->
                        if (subscribedBefore) send(GroupChange.Disconnected)
                    RealtimeChannel.Status.UNSUBSCRIBING -> Unit
                }
            }
        }
        try {
            channel.subscribe()
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { client.realtime.removeChannel(channel) }
        }
    }
}
