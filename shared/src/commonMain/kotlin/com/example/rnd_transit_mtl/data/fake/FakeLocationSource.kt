package com.example.rnd_transit_mtl.data.fake

import com.example.rnd_transit_mtl.data.location.LocationSource
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Replays fixed location events, for previews, tests and the demo before
 * real platform location exists. Each collector gets the full sequence.
 *
 * @param events Events to emit in order, such as fixes along a route or a
 * PermissionDenied to exercise the manual-origin fallback.
 * @param intervalMillis Pause before each event after the first; never negative.
 */
class FakeLocationSource(
    private val events: List<LocationUpdate>,
    private val intervalMillis: Long = 1_000L
) : LocationSource {
    init {
        require(intervalMillis >= 0L) { "Interval must not be negative." }
    }

    override val updates: Flow<LocationUpdate> = flow {
        events.forEachIndexed { index, event ->
            if (index > 0) delay(intervalMillis)
            emit(event)
        }
    }
}
