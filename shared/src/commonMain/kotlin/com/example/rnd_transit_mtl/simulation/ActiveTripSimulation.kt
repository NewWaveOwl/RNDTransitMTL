package com.example.rnd_transit_mtl.simulation

import com.example.rnd_transit_mtl.state.TRIP_SIMULATION_DURATION_MILLIS
import com.example.rnd_transit_mtl.state.TripActionResult
import com.example.rnd_transit_mtl.state.TripsStore
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Measures active simulation time for one stored trip.
 *
 * This object is transient. Its time marks are never saved.
 * TripsStore saves the accumulated elapsed milliseconds.
 *
 * The screen owns frame scheduling, lifecycle observation, and navigation.
 * All methods must be called on the UI thread.
 */
internal class ActiveTripSimulation(
    private val tripsStore: TripsStore,
    private val tripId: String,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {
    /**
     * Identifies one continuous active interval.
     *
     * Keeping the identity lets an older coroutine's finally block
     * avoid pausing a newer resumed interval.
     */
    internal class Session internal constructor(
        internal val startedAt: TimeMark,
        internal val initialElapsedMillis: Long
    )

    private var currentSession: Session? = null

    /**
     * Begins an interval from the authoritative saved elapsed value.
     *
     * Does not create a trip or regenerate endpoints.
     */
    fun resume(): Session? {
        pause()

        val active = tripsStore.state.value.activeTrip
            ?.takeIf { it.trip.id == tripId }
            ?: return null

        return Session(
            startedAt = timeSource.markNow(),
            initialElapsedMillis = active.elapsedMillis
        ).also {
            currentSession = it
        }
    }

    /**
     * Publishes total elapsed active time.
     *
     * Returns false when the session has stopped, the trip is missing,
     * or the final update has completed the trip.
     */
    fun checkpoint(session: Session): Boolean {
        if (currentSession !== session) return false

        val stillActive = publishElapsed(session)

        if (!stillActive && currentSession === session) {
            currentSession = null
        }

        return stillActive
    }

    /**
     * Flushes the final partial-frame interval and stops this session.
     *
     * A stale session cannot stop a newer session.
     * Calling pause repeatedly is harmless.
     */
    fun pause(session: Session? = currentSession) {
        if (session == null || currentSession !== session) return

        currentSession = null
        publishElapsed(session)
    }

    private fun publishElapsed(session: Session): Boolean {
        val active = tripsStore.state.value.activeTrip
            ?.takeIf { it.trip.id == tripId }
            ?: return false

        val availableMillis =
            TRIP_SIMULATION_DURATION_MILLIS -
                    session.initialElapsedMillis

        val measuredMillis = session.startedAt
            .elapsedNow()
            .inWholeMilliseconds
            .coerceAtLeast(0L)
            .coerceAtMost(availableMillis)

        val totalElapsedMillis =
            session.initialElapsedMillis + measuredMillis

        /*
         * A session normally owns every update for this active trip.
         * The maximum also prevents a stale value from regressing
         * an elapsed value already present in the store.
         */
        val authoritativeElapsedMillis =
            maxOf(totalElapsedMillis, active.elapsedMillis)

        val result = tripsStore.updateElapsed(
            tripId = tripId,
            elapsedMillis = authoritativeElapsedMillis
        )

        /*
         * updateElapsed records completion in one snapshot at 10000 ms.
         * An Applied result can therefore mean that active state
         * has just been replaced by a completed record.
         */
        return result == TripActionResult.Applied &&
                tripsStore.activeTrip?.id == tripId
    }
}