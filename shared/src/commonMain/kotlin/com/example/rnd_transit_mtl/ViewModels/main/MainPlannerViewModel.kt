package com.example.rnd_transit_mtl.ViewModels.main

import androidx.lifecycle.ViewModel
import com.example.rnd_transit_mtl.model.Trip
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
 * @property walkingPreference Scenic or direct walking preference, retained for future routing.
 * @property selectedAttractionIds Attraction preferences retained for future routing.
 * @property selectedZonesByTransport Independent zones for each transit mode, including disabled modes.
 */
@Serializable
data class MainPlannerState(
    val minutes: Int = 30,
    val selectedTransportIds: List<String> = listOf("walk"),
    val selectedZoneIds: List<String> = listOf("A"),
    val intensity: Float = 90f,
    val walkingPreference: String = "Scenic",
    val selectedAttractionIds: List<String> = listOf("Nature", "Surprise"),
    val selectedZonesByTransport: Map<String, List<String>> = emptyMap()
) {
    /** Returns this mode's saved zones, falling back to the shared choice from older drafts.
     * @param transportId Transit mode whose zones are requested.
     */
    fun zonesForTransport(transportId: String): List<String> =
        selectedZonesByTransport[transportId] ?: if (transportId in selectedTransportIds) selectedZoneIds else emptyList()
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

    private val mutableUiState = MutableStateFlow(initialState)

    /** Read-only planner state observed by the view. */
    val uiState = mutableUiState.asStateFlow()

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
    fun toggleTransport(id: String, availableIds: Set<String>) {
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
    fun toggleZone(transportId: String, id: String) {
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
     * @param value Scenic or Direct; unavailable choices are ignored.
     */
    fun setWalkingPreference(value: String) {
        if (value !in walkingPreferences) return
        mutableUiState.update { it.copy(walkingPreference = value) }
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
        val zoneIds = listOf("A", "B", "C", "D")
        /** Modes offering their own transit-zone constraints. */
        val transitIds = setOf("bus", "metro", "train", "rem")
        /** Walking styles displayed when walking is enabled. */
        val walkingPreferences = listOf("Scenic", "Direct")
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
private fun List<String>.toggled(id: String): List<String> =
    if (id in this) filterNot { it == id } else this + id
