package com.example.rnd_transit_mtl.ViewModels.main
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.ids.TransportId

import com.example.rnd_transit_mtl.model.trip.TripPoint
import androidx.lifecycle.ViewModel
import com.example.rnd_transit_mtl.model.trip.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/**
 * Stores the editable planner draft.
 *
 * @property minutes Available trip time in minutes.
 * @property selectedTransportIds Enabled transport-mode IDs.
 * @property selectedZoneIds Legacy shared zones used to restore older drafts.
 * @property intensity Attraction intensity from 0 to 100.
 * @property walkingPreference Walking pace (Amble, Walk, or Dash), retained for future routing.
 * @property bikeSpeedKmh Preferred cycling speed in km/h, retained for future routing.
 * @property selectedAttractionIds Attraction preferences retained for future routing.
 * @property selectedZonesByTransport Independent zones for each transit mode, including disabled modes.
 * @property origin Selected source-image point; default coordinates are labelled sample data.
 * @property locationAccessOff Explicit adapter or preview permission state; no GPS is queried.
 * @property originIsManual Whether an origin was explicitly confirmed rather than inferred from GPS.
 */
@Serializable
data class MainPlannerState(
    val minutes: Int = 30,
    val selectedTransportIds: List<TransportId> = listOf(TransportId("walk")),
    val selectedZoneIds: List<ZoneId> = listOf(ZoneId("A")),
    val intensity: Float = 90f,
    val walkingPreference: String = "Amble",
    val selectedAttractionIds: List<String> = listOf("Nature", "Surprise"),
    val selectedZonesByTransport: Map<TransportId, List<ZoneId>> = emptyMap(),
    val bikeSpeedKmh: Int = 18,

    // Labelled sample data; these are PNG coordinates, not GPS.
    val origin: TripPoint? = TripPoint("Current location", 0.5f, 0.35f),

    // Supplied by a future permission adapter, or a labelled preview fixture.
    val locationAccessOff: Boolean = false,
    val originIsManual: Boolean = false
) {
    /** Returns independently saved zones, with legacy shared-zone fallback.
     * @param transportId Transit mode whose zones are requested.
     */
    fun zonesForTransport(transportId: TransportId): List<ZoneId> =
        selectedZonesByTransport[transportId]
            ?: if (transportId in selectedTransportIds) {
                selectedZoneIds
            } else {
                emptyList()
            }
}

/**
 * Owns planner selections and exposes immutable state.
 *
 * Trip generation and navigation remain in the existing coordinator during
 * this first migration step.
 *
 * @param initialState Restored planner draft, or the initial defaults.
 */
class MainPlannerViewModel(
    initialState: MainPlannerState = MainPlannerState()
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(
        initialState.copy(minutes = Trip.coercePlannedMinutes(initialState.minutes),
            walkingPreference = when (initialState.walkingPreference) {
            "Scenic" -> "Amble"
            "Direct", "Normal", "Stroll" -> "Walk"
            "Fast" -> "Dash"
            in walkingPreferences -> initialState.walkingPreference
            else -> "Amble"
        }, bikeSpeedKmh = initialState.bikeSpeedKmh.coerceIn(MIN_BIKE_SPEED_KMH, MAX_BIKE_SPEED_KMH))
    )

    /** Read-only planner state observed by the view. */
    val uiState = mutableUiState.asStateFlow()

    /** Copies only the preferences actually recorded in a completed trip.
     * @param trip Saved trip; its identity, endpoints, and review are never changed.
     */
    fun copyTripPreferences(trip: Trip) {
        mutableUiState.update {
            it.copy(
                minutes = Trip.coercePlannedMinutes(trip.plannedMinutes),
                selectedTransportIds = trip.selectedTransports.map { mode ->
                    mode.id
                },
                selectedZoneIds = trip.selectedZoneIds.toList(),
                selectedZonesByTransport =
                    trip.selectedZonesByTransport.mapValues { entry ->
                        entry.value.toList()
                    },
                intensity = trip.attractionIntensity,
                origin = trip.start.copy(),
                originIsManual = true
            )
        }
    }

    /** Updates the selected sample-map origin.
     * @param point Source-image coordinates and a nonblank display label.
     */
    fun setOrigin(point: TripPoint) {
        val label = point.label.trim()
        if (label.isBlank()) return

        mutableUiState.update {
            it.copy(origin = point.copy(label = label), originIsManual = true)
        }
    }

    /**
     * Updates the time using the existing five-minute bounds.
     *
     * @param minutes Requested available minutes.
     */
    fun setMinutes(minutes: Int) {
        mutableUiState.update {
            it.copy(minutes = Trip.coercePlannedMinutes(minutes))
        }
    }

    /**
     * Toggles one supported transport mode independently of route selection.
     *
     * @param id Mode being toggled.
     * @param availableIds IDs from the loaded transport catalogue.
     */
    fun toggleTransport(id: TransportId, availableIds: Set<TransportId>) {
        if (id !in availableIds) return

        mutableUiState.update {
            it.copy(
                selectedTransportIds = it.selectedTransportIds.toggled(id)
            )
        }
    }

    /**
     * Toggles a zone only for the requested transit mode. As in As3, choosing a zone includes
     * the mode and clearing its last zone removes it; opening or closing a dropdown does neither.
     *
     * @param transportId Bus, Metro, Train, or REM catalogue ID.
     * @param id Zone A, B, C, or D; unsupported identifiers are ignored.
     */
    fun toggleZone(transportId: TransportId, id: ZoneId) {
        if (transportId !in transitIds || id !in zoneIds) return
        mutableUiState.update { state ->
            val zones = state.zonesForTransport(transportId).toggled(id)
            val transports = if (zones.isEmpty()) {
                state.selectedTransportIds.filterNot { it == transportId }
            } else {
                (state.selectedTransportIds + transportId).distinct()
            }
            state.copy(
                selectedTransportIds = transports,
                selectedZonesByTransport = state.selectedZonesByTransport + (transportId to zones)
            )
        }
    }

    /**
     * Updates attraction intensity.
     *
     * @param value Finite intensity from 0 to 100; outside values are clamped.
     */
    fun setIntensity(value: Float) {
        if (!value.isFinite()) return

        mutableUiState.update {
            it.copy(
                intensity = value.coerceIn(
                    Trip.MIN_ATTRACTION_INTENSITY,
                    Trip.MAX_ATTRACTION_INTENSITY
                )
            )
        }
    }

    /** Selects a supported walking preference without changing transport or zones.
     * @param value Amble (slow), Walk, or Dash; unavailable choices are ignored.
     */
    fun setWalkingPreference(value: String) {
        if (value !in walkingPreferences) return
        mutableUiState.update { it.copy(walkingPreference = value) }
    }

    /** Updates cycling pace without enabling Bike or changing other selections.
     * @param value Requested speed in km/h, clamped to the supported 8–32 range.
     */
    fun setBikeSpeedKmh(value: Int) {
        mutableUiState.update { it.copy(bikeSpeedKmh = value.coerceIn(MIN_BIKE_SPEED_KMH, MAX_BIKE_SPEED_KMH)) }
    }

    /** Toggles one attraction preference in the restorable draft.
     * @param id A supported attraction label; unavailable choices are ignored.
     */
    fun toggleAttraction(id: String) {
        if (id !in attractionIds) return
        mutableUiState.update { it.copy(selectedAttractionIds = it.selectedAttractionIds.toggled(id)) }
    }

    companion object {
        /** Supported zone identifiers for the planner interface. */
        val zoneIds = listOf("A", "B", "C", "D").map(::ZoneId)
        /** Modes offering their own transit-zone constraints. */
        val transitIds = setOf("bus", "metro", "train", "rem").map(::TransportId).toSet()
        /** Walking paces displayed from slowest to fastest when walking is enabled. */
        val walkingPreferences = listOf("Amble", "Walk", "Dash")
        const val MIN_BIKE_SPEED_KMH = 8
        const val MAX_BIKE_SPEED_KMH = 32

        /** Shared endpoints belong to the faster band: 14 Normal, 20 Steady, 26 Fast.
         * @param speedKmh Cycling speed whose display band is requested.
         */
        fun bikePaceLabel(speedKmh: Int): String = when {
            speedKmh < 14 -> "Slow"
            speedKmh < 20 -> "Normal"
            speedKmh < 26 -> "Steady"
            else -> "Fast"
        }
        /** Multi-select attraction labels from the Figma preferences band. */
        val attractionIds = listOf("Nature", "Culture", "Food", "Surprise", "Free places", "Outdoors")
    }
}

/**
 * Returns a selection with the requested ID added or removed.
 *
 * @param id Selection identifier to toggle.
 * @return A new selection list.
 */
private fun <T> List<T>.toggled(id: T): List<T> =
    if (id in this) filterNot { it == id } else this + id
