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
        "*,review:reviews(overall,quality,interesting,fun,comment,image_url)"
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
        val payload = JsonObject(fields - "review")
        client.from("trips").upsert(payload) {
            onConflict = "id"
            ignoreDuplicates = true
        }

        val stored = checkNotNull(getTrip(trip.id)) {
            "The saved trip could not be read back."
        }
        val originalFields = stored.copy(
            title = ownedTrip.title,
            description = ownedTrip.description,
            review = null,
        )
        check(originalFields == ownedTrip.copy(review = null)) {
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
        check(getTrip(tripId)?.status == TripStatus.Completed) { "Completed trip unavailable." }
        val fields = writeJson.encodeToJsonElement(
            TripReviewDto(review.overall, review.quality, review.interesting, review.`fun`, review.comment, review.imageUrl)
        ).jsonObject
        // The foreign key is immutable. On conflict update only the feedback fields.
        client.from("reviews").upsert(JsonObject(fields + ("trip_id" to JsonPrimitive(tripId.value)))) {
            onConflict = "trip_id"
            ignoreDuplicates = true
        }
        val stored = client.from("reviews").update(JsonObject(fields)) {
            filter { eq("trip_id", tripId.value) }
            select(Columns.raw("overall,quality,interesting,fun,comment,image_url"))
        }.decodeSingle<TripReviewDto>()
        return TripReview(stored.overall, stored.quality, stored.interesting, stored.funRating, stored.comment, stored.imageUrl)
    }

    override suspend fun deleteReview(tripId: TripId) {
        client.from("reviews").delete {
            filter { eq("trip_id", tripId.value) }
        }
    }

    private fun String.decodeTrips(): List<TripDto> =
        Json.parseToJsonElement(this).jsonArray.map { element ->
            val row = element.jsonObject
            // The reverse relationship can be represented as an array by PostgREST.
            val embeddedReview = row["review"]
            val normalized = if (embeddedReview is JsonArray) {
                check(embeddedReview.size <= 1) { "Multiple reviews for one trip." }
                JsonObject(row + ("review" to (embeddedReview.singleOrNull() ?: JsonNull)))
            } else {
                row
            }
            Json.decodeFromJsonElement<TripDto>(normalized)
        }

    private fun TripDto.requireTrip(): Trip =
        checkNotNull(toDomainOrNull()) { "Invalid stored trip: $id" }
}