package com.example.rnd_transit_mtl.ViewModels.current_trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rnd_transit_mtl.ViewModels.main.isPlannerTransportAvailable
import com.example.rnd_transit_mtl.data.generation.TripGenerationInput
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.data.routing.OtpMode
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.*
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.time.Clock

internal enum class ReturnTransportOption { Fastest, SameTransport }
internal enum class ReturnPhase { Arrival, Finding, Ready, Unavailable, AtStart }
internal data class ReturnTripState(
    val phase: ReturnPhase = ReturnPhase.Arrival,
    val option: ReturnTransportOption = ReturnTransportOption.Fastest,
    val proposal: Trip? = null,
    val message: String? = null,
    val hasRequested: Boolean = false,
    val startLabel: String? = null
)

/** Catalogue entries actually available in this build; REM/Train are not falsely compared. */
internal val ReturnTransportCatalogue: List<TransportType> = OtpMode.entries
    .filter { isPlannerTransportAvailable(it.id) }
    .map { TransportType(it.id, it.snapshot.label, it.snapshot.usesRoutes) }

/**
 * Owns one cancellable return proposal while TripsStore keeps the completed outbound record.
 * @param store Application trip repository; an outbound trip is never overwritten by a return.
 * @param outboundTripId Stable ID whose original start remains the fixed destination.
 * @param generate Cancellable backend adapter, also injectable for deterministic tests.
 * @param now Clock used only to reject stale starting-location readings.
 */
internal class ReturnTripViewModel(
    private val store: TripsStore,
    private val outboundTripId: TripId,
    private val generate: suspend (TripGenerationInput, Set<TripId>) -> TripGenerationResult,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReturnTripState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var revision = 0L

    /** Starts a fresh real route, from a usable GPS fix or the saved completed destination.
     * @param option Fastest available route, or the recorded outbound transport selection.
     * @param fix Optional current position; stale/imprecise fixes do not replace the saved destination.
     */
    fun findReturn(option: ReturnTransportOption, fix: LocationUpdate.Fix? = null) {
        if (request?.isActive == true) return
        val outbound = store.findCompleted(outboundTripId)
        if (outbound == null || store.activeTrip != null) {
            mutableState.value = ReturnTripState(ReturnPhase.Unavailable, option,
                message = if (outbound == null) "The completed trip is no longer available."
                else "Finish the current trip before starting a return.", hasRequested = true)
            return
        }
        val usableFix = fix?.takeIf { it.accuracyMeters <= 200 &&
            (it.timestampEpochMillis == null || now() - it.timestampEpochMillis in 0..30_000) }
        val start = usableFix?.let { Place("Current location", it.point) } ?: outbound.destination
        if (start.point.distanceMetersTo(outbound.origin.point) <= 20) {
            mutableState.value = ReturnTripState(ReturnPhase.AtStart, option, hasRequested = true)
            return
        }
        val modes = if (option == ReturnTransportOption.Fastest) ReturnTransportCatalogue.map { it.id }
            else outbound.requestedTransportIds ?: outbound.selectedTransports.map { it.id }
        if (modes.isEmpty() || modes.any { id -> ReturnTransportCatalogue.none { it.id == id } }) {
            mutableState.value = ReturnTripState(ReturnPhase.Unavailable, option,
                message = "An outbound transport is unavailable. Try the fastest option.", hasRequested = true)
            return
        }
        val transit = ReturnTransportCatalogue.filter { it.usesRoutes && it.id in modes }.map { it.id }
        val zones = transit.associateWith { id ->
            if (option == ReturnTransportOption.Fastest) listOf("A", "B", "C", "D").map(::ZoneId)
            else outbound.selectedZonesByTransport[id] ?: outbound.selectedZoneIds.takeIf { it.isNotEmpty() }
                ?: listOf("A", "B", "C", "D").map(::ZoneId)
        }
        val input = TripGenerationInput("Return to your starting point", "Return to ${outbound.origin.label}", "",
            Trip.MAX_PLANNED_MINUTES, modes,
            // Same transport means the saved modes, not a provider line ID or a reversed bus itinerary.
            emptyList(),
            outbound.attractionIntensity, selectedZoneIds = zones.values.flatten().distinct(),
            selectedZonesByTransport = zones, ownerId = outbound.ownerId, originPlace = start,
            destinationPlace = outbound.origin, walkingPreference = outbound.walkingPreference ?: "Amble",
            compareModes = option == ReturnTransportOption.Fastest)
        val token = ++revision
        mutableState.value = ReturnTripState(ReturnPhase.Finding, option, hasRequested = true, startLabel = start.label)
        request = viewModelScope.launch {
            val result = try {
                generate(input, store.completedTrips.map { it.id }.toSet() + store.cancelledTrips.map { it.id })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { TripGenerationResult.RoutingUnavailable("Return routing is unavailable. Retry.") }
            currentCoroutineContext().ensureActive()
            if (token != revision) return@launch
            mutableState.value = when (result) {
                is TripGenerationResult.Success -> {
                    val candidate = result.trip
                    if (candidate.destination != outbound.origin || candidate.origin != start ||
                        candidate.legs.none { it.isRouted }) {
                        mutableState.value.copy(phase = ReturnPhase.Unavailable,
                            message = "The provider did not return a real route to your saved starting point.")
                    } else {
                        val minutes = ceil((candidate.routedDurationSeconds ?: candidate.legs.sumOf { it.durationSeconds }) / 60.0)
                            .toInt().coerceIn(Trip.MIN_PLANNED_MINUTES, Trip.MAX_PLANNED_MINUTES)
                        mutableState.value.copy(phase = ReturnPhase.Ready, proposal = candidate.copy(plannedMinutes = minutes,
                            journey = TripJourney(TripJourneyKind.Return, outboundTripId)))
                    }
                }
                is TripGenerationResult.RoutingUnavailable -> mutableState.value.copy(phase = ReturnPhase.Unavailable, message = result.message)
                is TripGenerationResult.InvalidInput -> mutableState.value.copy(phase = ReturnPhase.Unavailable, message = result.message)
                else -> mutableState.value.copy(phase = ReturnPhase.Unavailable,
                    message = "No suitable route is available with those transport choices. Your trip and starting point are saved.")
            }
        }
    }

    /** Discards only this temporary return request/proposal; the completed record and review remain. */
    fun cancelReturn() {
        revision++
        request?.cancel(); request = null
        mutableState.value = mutableState.value.copy(phase = ReturnPhase.Arrival, proposal = null, message = null)
    }

    /** Atomically defers the outbound review when the user accepts the independently routed trip. */
    fun startReturn(): TripActionResult {
        val proposal = mutableState.value.proposal ?: return TripActionResult.InvalidInput("Choose a return route first.")
        if (store.activeTrip?.id == proposal.id) return TripActionResult.Applied
        return store.startReturn(proposal, outboundTripId)
    }
}
