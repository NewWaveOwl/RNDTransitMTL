package com.example.rnd_transit_mtl.state

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver as ComposeSaver
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.model.TripReview
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Owns active and completed trip state, pending review handling,
 * and unsaved review drafts.
 *
 * All operations are synchronous and must be called from the UI
 * thread. They replace one observable snapshot rather than mutating
 * separate collections or exposing mutable state setters.
 *
 * The store does not own a timer or Navigator. A screen supplies
 * accumulated active time; navigation acknowledges pending review
 * only after its destination has been established.
 *
 * nowEpochMillis supplies record timestamps, not simulation timing.
 * An injected clock function must not throw.
 */
class TripsStore private constructor(
    initialState: TripsState,
    private val nowEpochMillis: () -> Long
) {
    constructor(
        nowEpochMillis: () -> Long = {
            Clock.System.now().toEpochMilliseconds()
        }
    ) : this(
        initialState = TripsState(),
        nowEpochMillis = nowEpochMillis
    )

    private val mutableState = mutableStateOf(
        detachedSnapshot(initialState)
    )

    /**
     * Read-only observable access to the one source of truth.
     *
     * Treat all supplied lists and maps as immutable values.
     */
    val state: State<TripsState>
        get() = mutableState

    val activeTrip: Trip?
        get() = mutableState.value.activeTrip?.trip

    val elapsedMillis: Long
        get() = mutableState.value.activeTrip?.elapsedMillis ?: 0L

    /**
     * Progress for the current active trip, or zero when none exists.
     *
     * Use progressFor(tripId) when displaying a specific trip through
     * the transition from active to completed.
     */
    val progress: Float
        get() = elapsedMillis.toFloat() /
                TRIP_SIMULATION_DURATION_MILLIS.toFloat()

    val completedTrips: List<Trip>
        get() = mutableState.value.completedTrips

    val pendingReviewTripId: String?
        get() = mutableState.value.pendingReviewTripId

    val reviewDrafts: Map<String, ReviewDraft>
        get() = mutableState.value.reviewDrafts

    /**
     * Finds either the active trip or a completed trip.
     *
     * Null is the unsuccessful lookup result for a missing/deleted ID.
     * A lookup never creates or restores a record from a route parameter.
     */
    fun findTrip(tripId: String): Trip? {
        val current = mutableState.value
        return current.activeTrip?.trip?.takeIf { it.id == tripId }
            ?: current.completedTrips.firstOrNull { it.id == tripId }
    }

    fun findCompleted(tripId: String): Trip? =
        mutableState.value.completedTrips.firstOrNull {
            it.id == tripId
        }

    /**
     * Progress for an existing trip.
     *
     * A completed record always has progress 1, without restarting
     * a simulation. A missing/deleted ID returns null.
     */
    fun progressFor(tripId: String): Float? {
        val current = mutableState.value
        val active = current.activeTrip

        if (active != null && active.trip.id == tripId) {
            return active.elapsedMillis.toFloat() /
                    TRIP_SIMULATION_DURATION_MILLIS.toFloat()
        }

        return if (current.completedTrips.any { it.id == tripId }) {
            1f
        } else {
            null
        }
    }

    /**
     * Starts one unfinished trip at zero elapsed active time.
     *
     * Another start cannot replace the existing active trip.
     */
    fun start(trip: Trip): TripActionResult {
        val current = mutableState.value

        current.activeTrip?.let {
            return TripActionResult.ActiveTripExists(it.trip.id)
        }

        if (current.pendingReviewTripId != null) {
            return TripActionResult.InvalidInput(
                "Handle the pending trip review before starting another trip."
            )
        }
        if (current.completedTrips.any { it.id == trip.id }) {
            return TripActionResult.InvalidInput(
                "A completed trip already uses this ID."
            )
        }
        if (trip.completedAtEpochMillis != null || trip.review != null) {
            return TripActionResult.InvalidInput(
                "Only an unfinished trip can be started."
            )
        }

        val ownedTrip = try {
            detachedTrip(trip)
        } catch (invalid: IllegalArgumentException) {
            return TripActionResult.InvalidInput(
                invalid.message ?: "Invalid trip record."
            )
        }

        mutableState.value = current.copy(
            activeTrip = ActiveTripState(ownedTrip, elapsedMillis = 0L)
        )
        return TripActionResult.Applied
    }

    /**
     * Receives total accumulated active time, not a per-frame delta.
     *
     * Negative or regressing time is rejected. Values above the demo
     * duration are clamped. Reaching 10000 milliseconds immediately
     * records completion in one snapshot update.
     *
     * Pausing means that the caller stops sending updates. Resuming
     * continues from elapsedMillis without counting the paused interval.
     */
    fun updateElapsed(
        tripId: String,
        elapsedMillis: Long
    ): TripActionResult {
        if (elapsedMillis < 0L) {
            return TripActionResult.InvalidInput(
                "Elapsed active time must not be negative."
            )
        }

        val current = mutableState.value

        if (current.completedTrips.any { it.id == tripId }) {
            return TripActionResult.AlreadyCompleted
        }

        val active = current.activeTrip
        if (active == null || active.trip.id != tripId) {
            return TripActionResult.MissingTrip(tripId)
        }
        if (elapsedMillis < active.elapsedMillis) {
            return TripActionResult.InvalidInput(
                "Elapsed active time cannot move backwards."
            )
        }

        val boundedElapsed = elapsedMillis.coerceAtMost(
            TRIP_SIMULATION_DURATION_MILLIS
        )

        if (boundedElapsed == TRIP_SIMULATION_DURATION_MILLIS) {
            return commitCompletion(current, active)
        }

        if (boundedElapsed != active.elapsedMillis) {
            mutableState.value = current.copy(
                activeTrip = active.copy(
                    elapsedMillis = boundedElapsed
                )
            )
        }

        return TripActionResult.Applied
    }

    /**
     * Completes a trip only after its full active-time duration.
     *
     * updateElapsed() normally performs this transition automatically.
     * This operation also supports an explicit completion check.
     * Repeated completion cannot append a second record.
     */
    fun complete(tripId: String): TripActionResult {
        val current = mutableState.value

        if (current.completedTrips.any { it.id == tripId }) {
            return TripActionResult.AlreadyCompleted
        }

        val active = current.activeTrip
        if (active == null || active.trip.id != tripId) {
            return TripActionResult.MissingTrip(tripId)
        }
        if (active.elapsedMillis < TRIP_SIMULATION_DURATION_MILLIS) {
            return TripActionResult.InvalidInput(
                "The trip has not reached 10000 milliseconds of active time."
            )
        }

        return commitCompletion(current, active)
    }

    /**
     * Cancels only an unfinished active trip.
     *
     * Cancellation does not append anything to completedTrips.
     */
    fun cancel(tripId: String): TripActionResult {
        val current = mutableState.value

        if (current.completedTrips.any { it.id == tripId }) {
            return TripActionResult.AlreadyCompleted
        }

        val active = current.activeTrip
        if (active == null || active.trip.id != tripId) {
            return TripActionResult.MissingTrip(tripId)
        }

        mutableState.value = current.copy(activeTrip = null)
        return TripActionResult.Applied
    }

    /**
     * Initializes a draft only when one does not already exist.
     *
     * Opening an existing draft preserves its unsaved edits.
     * Otherwise, editing starts from the saved review or empty ratings.
     */
    fun beginReview(tripId: String): TripActionResult {
        val current = mutableState.value
        val trip = current.completedTrips.firstOrNull {
            it.id == tripId
        } ?: return TripActionResult.MissingTrip(tripId)

        if (tripId in current.reviewDrafts) {
            return TripActionResult.Applied
        }

        val savedReview = trip.review
        val draft = if (savedReview == null) {
            ReviewDraft()
        } else {
            ReviewDraft(
                overall = savedReview.overall,
                quality = savedReview.quality,
                interesting = savedReview.interesting,
                `fun` = savedReview.`fun`,
                comment = savedReview.comment,
                imageUrl = savedReview.imageUrl
            )
        }

        mutableState.value = current.copy(
            reviewDrafts = current.reviewDrafts + (tripId to draft)
        )
        return TripActionResult.Applied
    }

    fun updateReviewDraft(
        tripId: String,
        draft: ReviewDraft
    ): TripActionResult {
        val current = mutableState.value

        if (current.completedTrips.none { it.id == tripId }) {
            return TripActionResult.MissingTrip(tripId)
        }

        mutableState.value = current.copy(
            reviewDrafts = current.reviewDrafts + (tripId to draft)
        )
        return TripActionResult.Applied
    }

    /**
     * Saves the stored draft using the agreed one-argument operation.
     *
     * Missing overall feedback leaves the draft and saved review intact.
     */
    fun saveReview(tripId: String): TripActionResult {
        val current = mutableState.value

        if (current.completedTrips.none { it.id == tripId }) {
            return TripActionResult.MissingTrip(tripId)
        }

        val draft = current.reviewDrafts[tripId]
            ?: return TripActionResult.InvalidInput(
                "Open a review before saving it."
            )

        val overall = draft.overall
            ?: return TripActionResult.InvalidInput(
                "Select an overall rating before saving."
            )

        val imageUrl = draft.imageUrl.trim()
        if (imageUrl.isNotEmpty() && !Trip.isSupportedImageUrl(imageUrl)) {
            return TripActionResult.InvalidInput(
                "Enter a valid HTTPS image link, or leave it empty."
            )
        }

        val review = TripReview(
            overall = overall,
            quality = draft.quality,
            interesting = draft.interesting,
            `fun` = draft.`fun`,
            comment = draft.comment.trim(),
            imageUrl = imageUrl
        )

        return saveReview(tripId, review)
    }

    /**
     * Saves/replaces supplied valid feedback on the same Trip ID.
     *
     * This overload supports callers that already have a TripReview.
     * It cannot recreate a removed trip.
     */
    fun saveReview(
        tripId: String,
        review: TripReview
    ): TripActionResult {
        val current = mutableState.value

        if (current.completedTrips.none { it.id == tripId }) {
            return TripActionResult.MissingTrip(tripId)
        }

        mutableState.value = current.copy(
            completedTrips = current.completedTrips.map { trip ->
                if (trip.id == tripId) {
                    trip.copy(
                        review = review.copy(
                            comment = review.comment.trim()
                        )
                    )
                } else {
                    trip
                }
            },
            pendingReviewTripId = current.pendingReviewTripId
                .takeUnless { it == tripId },
            reviewDrafts = current.reviewDrafts - tripId
        )

        return TripActionResult.Applied
    }

    /**
     * Discards unsaved feedback while preserving the completed Trip.
     *
     * On an initial review, review remains null. When skipping edits,
     * any previously saved review remains unchanged.
     */
    fun skipReview(tripId: String): TripActionResult {
        val current = mutableState.value

        if (current.completedTrips.none { it.id == tripId }) {
            return TripActionResult.MissingTrip(tripId)
        }

        mutableState.value = current.copy(
            pendingReviewTripId = current.pendingReviewTripId
                .takeUnless { it == tripId },
            reviewDrafts = current.reviewDrafts - tripId
        )

        return TripActionResult.Applied
    }

    /**
     * Removes a completed record and all associated transient state.
     */
    fun removeCompleted(tripId: String): TripActionResult {
        val current = mutableState.value

        if (current.completedTrips.none { it.id == tripId }) {
            return TripActionResult.MissingTrip(tripId)
        }

        mutableState.value = current.copy(
            completedTrips = current.completedTrips.filterNot {
                it.id == tripId
            },
            pendingReviewTripId = current.pendingReviewTripId
                .takeUnless { it == tripId },
            reviewDrafts = current.reviewDrafts - tripId
        )

        return TripActionResult.Applied
    }

    /**
     * Acknowledges that navigation has handled this automatic review.
     *
     * Call after the matching review destination exists. A repeated
     * acknowledgement succeeds without changing anything. An
     * acknowledgement for another pending trip is rejected.
     */
    fun acknowledgeReviewNavigation(
        tripId: String
    ): TripActionResult {
        val current = mutableState.value

        if (current.completedTrips.none { it.id == tripId }) {
            return TripActionResult.MissingTrip(tripId)
        }

        val pendingId = current.pendingReviewTripId
        if (pendingId == null) {
            return TripActionResult.Applied
        }
        if (pendingId != tripId) {
            return TripActionResult.InvalidInput(
                "This trip does not match the pending review."
            )
        }

        mutableState.value = current.copy(
            pendingReviewTripId = null
        )
        return TripActionResult.Applied
    }

    /**
     * Returns a detached data snapshot for restoration or inspection.
     * Jobs, callbacks, UI components, and clock functions are excluded.
     */
    fun snapshot(): TripsState =
        detachedSnapshot(mutableState.value)

    fun toSavedStateJson(): String =
        savedStateJson.encodeToString(
            TripsState.serializer(),
            snapshot()
        )

    private fun commitCompletion(
        current: TripsState,
        active: ActiveTripState
    ): TripActionResult {
        if (current.completedTrips.any {
                it.id == active.trip.id
            }
        ) {
            return TripActionResult.AlreadyCompleted
        }

        /*
         * Wall-clock adjustments must not create a completion timestamp
         * earlier than creation. Active duration is measured separately.
         */
        val completedAt = nowEpochMillis().coerceAtLeast(
            active.trip.createdAtEpochMillis
        )
        val completedTrip = active.trip.copy(
            completedAtEpochMillis = completedAt,
            review = null
        )

        /*
         * One observable assignment publishes the complete transition:
         * final record added, active state cleared, pending review set.
         */
        mutableState.value = current.copy(
            activeTrip = null,
            completedTrips = current.completedTrips + completedTrip,
            pendingReviewTripId = completedTrip.id
        )

        return TripActionResult.Applied
    }

    companion object {
        private val savedStateJson = Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }

        /**
         * Saves the latest store snapshot as a platform-saveable String.
         *
         * A failed/unsupported restoration returns null, allowing
         * rememberSaveable to initialize a fresh empty store.
         */
        val Saver: ComposeSaver<TripsStore, String> = ComposeSaver(
            save = { store -> store.toSavedStateJson() },
            restore = { encoded -> fromSavedStateJson(encoded) }
        )

        /**
         * Explicit restoration entry point for the Saver and manual checks.
         *
         * Decoding invokes record and snapshot validation. Malformed JSON,
         * invalid records, or an unsupported schema return null.
         *
         * A defensive boundary case at exactly 10000 milliseconds is
         * completed immediately. Ordinary saves never expose that
         * intermediate state because completion is committed atomically.
         */
        fun fromSavedStateJson(
            encoded: String,
            nowEpochMillis: () -> Long = {
                Clock.System.now().toEpochMilliseconds()
            }
        ): TripsStore? {
            val decoded = try {
                savedStateJson.decodeFromString(
                    TripsState.serializer(),
                    encoded
                )
            } catch (_: SerializationException) {
                return null
            } catch (_: IllegalArgumentException) {
                return null
            }

            val restored = TripsStore(
                initialState = decoded,
                nowEpochMillis = nowEpochMillis
            )

            decoded.activeTrip?.let { active ->
                if (active.elapsedMillis ==
                    TRIP_SIMULATION_DURATION_MILLIS
                ) {
                    restored.complete(active.trip.id)
                }
            }

            return restored
        }

        private fun detachedTrip(trip: Trip): Trip =
            trip.copy(
                selectedTransports = trip.selectedTransports.toList(),
                selectedRoutes = trip.selectedRoutes.toList()
            )

        private fun detachedSnapshot(source: TripsState): TripsState =
            source.copy(
                activeTrip = source.activeTrip?.let {
                    it.copy(trip = detachedTrip(it.trip))
                },
                completedTrips = source.completedTrips.map {
                    detachedTrip(it)
                },
                reviewDrafts = source.reviewDrafts.toMap()
            )
    }
}
