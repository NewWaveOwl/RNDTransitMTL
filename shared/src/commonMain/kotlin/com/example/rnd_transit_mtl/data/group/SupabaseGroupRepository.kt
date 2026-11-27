package com.example.rnd_transit_mtl.data.group

import com.example.rnd_transit_mtl.data.remote.dto.GroupDto
import com.example.rnd_transit_mtl.data.remote.dto.GroupTripRefDto
import com.example.rnd_transit_mtl.data.remote.dto.TripDto
import com.example.rnd_transit_mtl.data.remote.dto.toDomainOrNull
import com.example.rnd_transit_mtl.data.remote.dto.toDto
import com.example.rnd_transit_mtl.data.supabase.SupabaseBackend
import com.example.rnd_transit_mtl.data.trips.SupabaseTripRepository
import com.example.rnd_transit_mtl.model.group.Group
import com.example.rnd_transit_mtl.model.group.GroupJoinCode
import com.example.rnd_transit_mtl.model.ids.GroupId
import com.example.rnd_transit_mtl.model.ids.GroupTripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.trip.Trip
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * [GroupRepository] backed by the Supabase group functions and tables
 * (`supabase/migrations/20261127200000_create_groups.sql`).
 *
 * @param userId The signed-in user; owner of the trips this repository creates.
 * @param client Supabase client with Postgrest (and Realtime for the default change source).
 * @param changes Live change signals; Realtime by default.
 * @param newTripId Creates the ID of a member's own copy of a group trip.
 * @param now Current time in epoch milliseconds.
 */
internal class SupabaseGroupRepository(
    private val userId: UserId,
    private val client: SupabaseClient = SupabaseBackend.client,
    private val changes: GroupChangeSource = RealtimeGroupChangeSource(client),
    private val newTripId: () -> String = { Uuid.random().toString() },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : GroupRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val writeJson = Json { encodeDefaults = true }

    override suspend fun currentGroup(): Group? = guarded {
        readGroups(null).firstOrNull()
    }

    override suspend fun createGroup(): Group = guarded {
        val created = client.postgrest.rpc("create_group").decodeAs<GroupDto>()
        requireGroup(GroupId(created.id))
    }

    override suspend fun joinGroup(code: GroupJoinCode): Group = guarded {
        val joined = client.postgrest.rpc(
            "join_group",
            JsonObject(mapOf("p_code" to JsonPrimitive(code.value))),
        ).decodeAs<GroupDto>()
        requireGroup(GroupId(joined.id))
    }

    override suspend fun leaveGroup(id: GroupId) = guarded {
        client.postgrest.rpc(
            "leave_group",
            JsonObject(mapOf("p_group_id" to JsonPrimitive(id.value))),
        )
        Unit
    }

    override suspend fun shareTrip(id: GroupId, trip: Trip): GroupTripId = guarded {
        val fields = writeJson.encodeToJsonElement(trip.toDto()).jsonObject
        val shared = JsonObject(fields.filter { (name, value) ->
            name in SHARED_TRIP_FIELDS && value != JsonNull
        })
        val created = client.postgrest.rpc(
            "share_group_trip",
            JsonObject(mapOf("p_group_id" to JsonPrimitive(id.value), "p_trip" to shared)),
        ).decodeAs<GroupTripRefDto>()
        GroupTripId(created.id)
    }

    override suspend fun startGroupTrip(id: GroupTripId): Trip = guarded {
        val row = client.postgrest.rpc(
            "start_group_trip",
            JsonObject(mapOf("p_group_trip_id" to JsonPrimitive(id.value))),
        ).decodeAs<JsonObject>()
        memberTripFrom(row, id)
    }

    override fun observeGroup(id: GroupId): Flow<GroupSync> = flow {
        changes.changes(id).collect { change ->
            when (change) {
                GroupChange.Disconnected -> emit(GroupSync.Reconnecting)
                GroupChange.Connected, GroupChange.Changed -> {
                    val group = try {
                        readGroups(id).firstOrNull()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep following; the next signal or reconnect retries the read.
                        emit(GroupSync.Reconnecting)
                        return@collect
                    }
                    emit(if (group == null) GroupSync.Closed else GroupSync.Live(group))
                }
            }
        }
    }

    /**
     * Builds the member's own trip from a group_trips row: the shared route with a fresh
     * identity owned by this user.
     */
    private fun memberTripFrom(row: JsonObject, id: GroupTripId): Trip {
        val own = JsonObject(
            row.filterKeys { it in SHARED_TRIP_FIELDS } + mapOf(
                "id" to JsonPrimitive(newTripId()),
                "owner_id" to JsonPrimitive(userId.value),
                "status" to JsonPrimitive("generated"),
                "created_at" to JsonPrimitive(Instant.fromEpochMilliseconds(now()).toString()),
                "group_trip_id" to JsonPrimitive(id.value),
            )
        )
        return json.decodeFromJsonElement<TripDto>(own).toDomainOrNull()
            ?: throw GroupFailure.TripUnavailable()
    }

    /** Reads open groups visible to this user, optionally only [id]. */
    private suspend fun readGroups(id: GroupId?): List<Group> {
        val body = client.from("groups").select(Columns.raw(GROUP_COLUMNS)) {
            filter {
                exact("closed_at", null)
                if (id != null) eq("id", id.value)
            }
        }.data
        return json.parseToJsonElement(body).jsonArray.mapNotNull {
            json.decodeFromJsonElement<GroupDto>(it).toDomainOrNull()
        }
    }

    private suspend fun requireGroup(id: GroupId): Group =
        readGroups(id).firstOrNull() ?: throw GroupFailure.NotMember()

    /** Runs a server call and turns its failures into [GroupFailure]. */
    private suspend fun <T> guarded(call: suspend () -> T): T = try {
        call()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: GroupFailure) {
        throw failure
    } catch (refused: RestException) {
        throw failureFor(listOfNotNull(refused.error, refused.description, refused.message).joinToString(" "))
    } catch (_: HttpRequestException) {
        throw GroupFailure.Network()
    } catch (_: HttpRequestTimeoutException) {
        throw GroupFailure.Network()
    }

    internal companion object {
        private const val GROUP_COLUMNS =
            "id,join_code,host_id,current_group_trip_id,members:group_members(user_id,display_name,joined_at)"

        /** What a group trip shares: the route plus the title, description and image it was planned with. */
        val SHARED_TRIP_FIELDS: Set<String> =
            SupabaseTripRepository.SHARED_ROUTE_FIELDS + setOf("title", "description", "image_url")

        /**
         * Maps a server refusal to a failure, using the fixed messages the group functions raise.
         *
         * @param serverText Error text returned by PostgREST.
         */
        fun failureFor(serverText: String): GroupFailure = when {
            "GROUP_CODE_INVALID" in serverText -> GroupFailure.InvalidCode()
            "GROUP_ALREADY_JOINED" in serverText -> GroupFailure.AlreadyInGroup()
            "GROUP_NOT_HOST" in serverText -> GroupFailure.NotHost()
            "GROUP_NOT_MEMBER" in serverText -> GroupFailure.NotMember()
            "GROUP_TRIP_INVALID" in serverText -> GroupFailure.TripUnavailable()
            "GROUP_CODE_GENERATION_FAILED" in serverText -> GroupFailure.CodeGenerationFailed()
            else -> GroupFailure.Unexpected()
        }
    }
}
