package com.example.rnd_transit_mtl.data.trips

import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.review.TripReview
import com.example.rnd_transit_mtl.model.trip.Trip

internal interface TripRepository {
    /** Saves a completed or cancelled trip and returns the stored record. */
    suspend fun saveFinalTrip(trip: Trip): Trip

    /** Returns the signed-in owner's completed and cancelled trips. */
    suspend fun listHistory(): List<Trip>

    /** Returns null if this trip is absent from the owner's History. */
    suspend fun getTrip(id: TripId): Trip?
    /** Returns true if an owned History trip was updated. */
    suspend fun updateTrip(id: TripId, title: String, description: String): Boolean

    /** Removes an owned History trip. An already absent trip is a no-op. */
    suspend fun deleteTrip(id: TripId)
    suspend fun saveReview(tripId: TripId, review: TripReview): TripReview
    suspend fun deleteReview(tripId: TripId)
}