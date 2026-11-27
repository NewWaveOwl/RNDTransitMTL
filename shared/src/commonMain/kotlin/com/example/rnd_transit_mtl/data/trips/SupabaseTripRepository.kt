package com.example.rnd_transit_mtl.data.trips

import com.example.rnd_transit_mtl.data.remote.dto.TripDto
import com.example.rnd_transit_mtl.data.remote.dto.toDomainOrNull
import com.example.rnd_transit_mtl.data.remote.dto.toDto
import com.example.rnd_transit_mtl.data.supabase.SupabaseBackend
import com.example.rnd_transit_mtl.data.remote.dto.TripReviewDto
import com.example.rnd_transit_mtl.model.review.TripReview
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal class SupabaseTripRepository(
    private val ownerId: UserId,
    private val client: SupabaseClient = SupabaseBackend.client,
) : TripRepository {
    private val finalStatuses = listOf("completed", "cancelled")
    private val historyColumns = Columns.raw(
        "*,review:reviews(overall,quality,interesting,fun,comment,image_url)," +
            "group_trip:group_trips(${SHARED_ROUTE_FIELDS.joinToString(",")})"
    )
    private val writeJson = Json { encodeDefaults = true }

    override suspend fun saveFinalTrip(trip: Trip): Trip {
        require(trip.status == TripStatus.Completed ||
                trip.status == TripStatus.Cancelled) {
            "Only completed or cancelled trips can be saved."
        }
        require(trip.ownerId == null || trip.ownerId == ownerId) {
            "The trip belongs to a different account."
        }

        val ownedTrip = trip.copy(ownerId = ownerId)
        val fields = writeJson.encodeToJsonElement(ownedTrip.toDto()).jsonObject

        // The DTO includes an embedded review for reads. Reviews are a separate table.
        // A group trip's route lives once in group_trips; this row only points to it.
        val excluded = if (ownedTrip.groupTripId == null) setOf("review") else SHARED_ROUTE_FIELDS + "review"
        val payload = JsonObject((fields - excluded).filterNot { (name, value) ->
            name in OPTIONAL_NULL_COLUMNS && value == JsonNull
        })
        client.from("trips").upsert(payload) {
            onConflict = "id"
            ignoreDuplicates = true
        }

        val stored = checkNotNull(getTrip(trip.id)) {
            "The saved trip could not be read back."
        }
        // A prior insert may have succeeded before its response was lost. Remote edits
        // must not prevent acknowledging that insert; only record identity is stable.
        check(stored.id == ownedTrip.id &&
                stored.ownerId == ownedTrip.ownerId &&
                stored.createdAt == ownedTrip.createdAt &&
                stored.groupTripId == ownedTrip.groupTripId) {
            "The trip ID is already associated with a different record."
        }
        return stored
    }

    override suspend fun listHistory(): List<Trip> =
        client.from("trips").select(historyColumns) {
            filter {
                eq("owner_id", ownerId.value)
                isIn("status", finalStatuses)
            }
        }.data.decodeTrips()
            .map { it.requireTrip() }
            .sortedByDescending { it.completedAt ?: it.cancelledAt ?: it.createdAt }

    override suspend fun getTrip(id: TripId): Trip? =
        client.from("trips").select(historyColumns) {
            filter {
                eq("owner_id", ownerId.value)
                eq("id", id.value)
                isIn("status", finalStatuses)
            }
        }.data.decodeTrips().singleOrNull()?.requireTrip()

    override suspend fun updateTrip(id: TripId, title: String, description: String): Boolean {
        require(title.isNotBlank() && description.isNotBlank())
        return client.from("trips").update({
            set("title", title.trim())
            set("description", description.trim())
        }) {
            filter {
                eq("owner_id", ownerId.value)
                eq("id", id.value)
                isIn("status", finalStatuses)
            }
            select(Columns.raw("id"))
        }.let { Json.parseToJsonElement(it.data).jsonArray.isNotEmpty() }
    }

    override suspend fun deleteTrip(id: TripId) {
        client.from("trips").delete {
            filter {
                eq("owner_id", ownerId.value)
                eq("id", id.value)
                isIn("status", finalStatuses)
            }
        }
    }

    override suspend fun saveReview(tripId: TripId, review: TripReview): TripReview {
        val fields = writeJson.encodeToJsonElement(
            TripReviewDto(review.overall, review.quality, review.interesting, review.`fun`, review.comment, review.imageUrl)
        ).jsonObject
        val stored = client.postgrest.rpc(
            "save_trip_review",
            JsonObject(mapOf("p_trip_id" to JsonPrimitive(tripId.value), "p_review" to fields)),
        ).decodeSingle<TripReviewDto>()
        return TripReview(stored.overall, stored.quality, stored.interesting, stored.funRating, stored.comment, stored.imageUrl)
    }

    override suspend fun deleteReview(tripId: TripId) {
        client.from("reviews").delete {
            filter { eq("trip_id", tripId.value) }
        }
    }

    private fun String.decodeTrips(): List<TripDto> =
        Json.parseToJsonElement(this).jsonArray.mapNotNull { element ->
            val row = element.jsonObject
            // The reverse relationship can be represented as an array by PostgREST.
            val embeddedReview = row["review"]
            val withReview = if (embeddedReview is JsonArray) {
                check(embeddedReview.size <= 1) { "Multiple reviews for one trip." }
                JsonObject(row + ("review" to (embeddedReview.singleOrNull() ?: JsonNull)))
            } else {
                row
            }
            withSharedRoute(withReview)?.let { Json.decodeFromJsonElement<TripDto>(it) }
        }

    /**
     * Copies a group trip's shared route onto the member's own row.
     *
     * @param row One trips row, possibly with an embedded group_trip object.
     * @return The row ready for TripDto, or null for a group row whose shared route
     * this account can no longer read; such a row is left out of History rather than
     * failing the whole list.
     */
    private fun withSharedRoute(row: JsonObject): JsonObject? {
        val ownFields = JsonObject(row - "group_trip")
        if (row["group_trip_id"].let { it == null || it == JsonNull }) return ownFields
        val sharedRoute = row["group_trip"] as? JsonObject ?: return null
        return JsonObject(ownFields + sharedRoute.filterKeys { it in SHARED_ROUTE_FIELDS })
    }

    private fun TripDto.requireTrip(): Trip =
        checkNotNull(toDomainOrNull()) { "Invalid stored trip: $id" }

    internal companion object {
        /**
         * Route columns a group trip stores once in group_trips. A member's trips row for a
         * group trip leaves them out; reads copy them back from the embedded group trip.
         */
        val SHARED_ROUTE_FIELDS: Set<String> = setOf(
            "origin", "destination", "legs", "planned_minutes", "transports", "routes",
            "attraction_intensity", "zone_ids", "zones_by_transport", "routed_duration_seconds",
            "waiting_time_seconds", "walking_preference", "orientation_buffer_seconds",
            "requested_transport_ids", "journey",
        )

        /** Newer columns left out of a save when empty, so older schemas still accept solo rows. */
        private val OPTIONAL_NULL_COLUMNS = setOf("journey", "group_trip_id")
    }
}
