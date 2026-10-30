package com.example.rnd_transit_mtl.state

import androidx.compose.runtime.saveable.Saver
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId

/*
 * rememberSaveable cannot store a value class on its own: Android would throw
 * when saving instance state. These savers store the underlying String instead.
 */

/** Saves an optional [TripId] for rememberSaveable; null is not saved. */
val NullableTripIdSaver: Saver<TripId?, String> = Saver(
    save = { id -> id?.value },
    restore = { value -> TripId(value) }
)

/** Saves an optional [TransportId] for rememberSaveable; null is not saved. */
val NullableTransportIdSaver: Saver<TransportId?, String> = Saver(
    save = { id -> id?.value },
    restore = { value -> TransportId(value) }
)
