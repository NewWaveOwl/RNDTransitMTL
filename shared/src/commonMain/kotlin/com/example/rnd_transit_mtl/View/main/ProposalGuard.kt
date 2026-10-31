package com.example.rnd_transit_mtl.View.main
import com.example.rnd_transit_mtl.model.ids.TripId

import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.state.trip.TripsStore

/** Explicit request outcomes; unexpected exceptions remain validation errors. */
enum class Trip33Failure {
    InvalidInput,
    NoRoute,
    ProviderUnavailable,
    PermissionDenied
}

/** Explicit fixtures, never inferred from an arbitrary exception. */
enum class Trip33Sample {
    Normal,
    NoRoute,
    ProviderError,
    PermissionDenied
}

/** Tracks one request revision and permits only its accepted preview to start.
 * Calls are serialized by the planner's UI coordinator.
 * @param initialProposalId Restored preview ID, or null when no valid preview exists.
 */
internal class ProposalGuard(
    initialProposalId: TripId? = null
) {
    private var revision = 0L
    private var waiting = false
    private var proposalId = initialProposalId

    /** Supersedes any previous request and returns the new revision token. */
    fun begin(): Long {
        revision += 1
        waiting = true
        proposalId = null
        return revision
    }

    /** @param token Request revision whose ownership is checked. */
    fun isCurrent(token: Long): Boolean =
        token == revision

    /** Accepts one result only while the matching request is waiting.
     * @param token Request revision returned by begin.
     * @param acceptedProposalId Successful preview ID, or null for a failure.
     */
    fun complete(token: Long, acceptedProposalId: TripId?): Boolean {
        if (!isCurrent(token) || !waiting) return false

        waiting = false
        proposalId = acceptedProposalId
        return true
    }

    /** Invalidates pending results and any previously accepted preview. */
    fun cancel() {
        revision += 1
        waiting = false
        proposalId = null
    }

    /** @param id Preview ID that must match the accepted result. */
    fun canStart(id: TripId): Boolean =
        !waiting && proposalId == id

    /** Consumes an accepted preview through the store's existing active-trip guard.
     * @param store Application trip repository.
     * @param trip Preview being explicitly confirmed.
     */
    fun start(
        store: TripsStore,
        trip: Trip
    ): TripActionResult {
        if (!canStart(trip.id)) {
            return TripActionResult.InvalidInput(
                "This preview is no longer available. Generate another trip."
            )
        }

        val result = store.start(trip)
        cancel()
        return result
    }
}
