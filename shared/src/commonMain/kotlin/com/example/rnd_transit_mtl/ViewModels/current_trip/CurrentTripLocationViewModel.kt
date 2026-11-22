package com.example.rnd_transit_mtl.ViewModels.current_trip

import androidx.lifecycle.ViewModel
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripRouteGeometry
import com.example.rnd_transit_mtl.model.trip.routePosition
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.time.Clock

internal const val TRIP_LOCATION_MAX_AGE_MILLIS = 15_000L
internal const val TRIP_LOCATION_GRACE_MILLIS = 30_000L
private const val PredictionDelayMillis = 4_000L
private const val MotionWindowMillis = 10_000L
private const val TripLocationMaximumAccuracyMeters = 50.0
private const val TripRouteToleranceMeters = 35.0
private const val TripArrivalToleranceMeters = 20.0

/** Estimates are explicit and never become recorded GPS progress. */
internal enum class TripLocationAvailability { Waiting, Available, Estimated, Stale, PermissionDenied, Unsupported }

/** Keeps the real reading separate from a temporary route estimate. */
internal data class CurrentTripLocationState(
    val fix: LocationUpdate.Fix? = null,
    val availability: TripLocationAvailability = TripLocationAvailability.Waiting,
    val offRoute: Boolean = false,
    val estimatedPosition: GeoPoint? = null,
    val estimatedProgress: Float? = null,
)

/**
 * Matches fresh GPS to one accepted trip and keeps its real progress in the shared store.
 * Platform permission requests and lifecycle collection remain at the screen boundary.
 * @param trip Accepted route; geometry and identity are never regenerated.
 * @param tripsStore Shared authoritative trip state.
 * @param nowEpochMillis Clock for freshness and route-bounded, display-only gap estimates.
 */
internal class CurrentTripLocationViewModel(
    private val trip: Trip,
    private val tripsStore: TripsStore,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(CurrentTripLocationState())
    val state = mutableState.asStateFlow()
    private var lastFixAt: Long? = null
    private var lastAcceptedTimestamp: Long? = null
    private var trackingStartedAt: Long? = null
    private data class MotionSample(val time: Long, val distance: Double)
    private val motionSamples = mutableListOf<MotionSample>()
    private val routeGeometry = TripRouteGeometry(trip)
    private val legLengths = routeGeometry.legLengths
    private val routeLength = routeGeometry.totalLength
    private var motionLegIndex: Int? = null
    private var velocityMetersPerSecond = 0.0
    private var anchorProgress: Float? = null
    private val restoredProgress = tripsStore.state.value.activeTrip
        ?.takeIf { it.trip.id == trip.id }?.routeProgress
    private var previousProgress = restoredProgress ?: 0f
    private var hasObservedRoute = restoredProgress?.let { it < 0.98f } == true

    /** Starts the initial-fix grace period when the active screen resumes. */
    fun startTracking() {
        if (trackingStartedAt == null) trackingStartedAt = nowEpochMillis()
    }

    /** @param update Latest provider reading or actual provider failure. */
    fun receive(update: LocationUpdate) {
        startTracking()
        when (update) {
            is LocationUpdate.Fix -> receiveFix(update)
            LocationUpdate.PermissionDenied -> {
                clearMotion()
                lastFixAt = null
                mutableState.value = CurrentTripLocationState(availability = TripLocationAvailability.PermissionDenied)
            }
            // Provider outages use the same grace period, retaining the last good position.
            LocationUpdate.Unavailable -> refreshFreshness()
        }
    }

    private fun receiveFix(fix: LocationUpdate.Fix) {
        val now = nowEpochMillis()
        val measuredAt = fix.timestampEpochMillis ?: now
        if (now - measuredAt !in 0L..TRIP_LOCATION_MAX_AGE_MILLIS ||
            lastAcceptedTimestamp?.let { measuredAt <= it } == true ||
            fix.accuracyMeters > TripLocationMaximumAccuracyMeters) {
            refreshFreshness()
            return
        }
        lastAcceptedTimestamp = measuredAt
        lastFixAt = measuredAt
        val matched = trip.routePosition(fix.point, previousProgress)
        val onRoute = matched != null && matched.distanceFromRouteMeters <= TripRouteToleranceMeters
        mutableState.value = CurrentTripLocationState(fix, TripLocationAvailability.Available, !onRoute)
        if (!onRoute) {
            clearMotion()
            return
        }
        recordMotion(fix, matched.progress, measuredAt)
        var progress = matched.progress
        val atDestination = fix.point.distanceMetersTo(trip.destination.point) <= TripArrivalToleranceMeters
        if (progress >= 0.98f && atDestination && hasObservedRoute) progress = 1f
        if (progress >= 0.999f && (!hasObservedRoute || !atDestination)) progress = 0.999f
        previousProgress = progress
        if (progress < 0.98f) hasObservedRoute = true
        tripsStore.updateRouteProgress(trip.id, progress)
    }

    private fun clearMotion() {
        motionSamples.clear()
        motionLegIndex = null
        velocityMetersPerSecond = 0.0
        anchorProgress = null
    }

    private fun recordMotion(fix: LocationUpdate.Fix, progress: Float, measuredAt: Long) {
        if (routeLength <= 0.0) return
        val distance = routeLength * progress
        var end = 0.0
        val legIndex = legLengths.indices.firstOrNull { end += legLengths[it]; distance < end }
            ?: trip.legs.lastIndex
        if (motionLegIndex != legIndex) clearMotion()
        motionLegIndex = legIndex
        anchorProgress = progress
        val leg = trip.legs[legIndex]
        val label = trip.selectedTransports.firstOrNull { it.id == leg.transportId }?.label.orEmpty().lowercase()
        val maximumSpeed = when {
            "walk" in label || "walk" in leg.transportId.value.lowercase() -> 3.5
            "bik" in label || "cycl" in label -> 15.0
            else -> 45.0
        }
        val previous = motionSamples.lastOrNull()
        if (previous != null && abs(distance - previous.distance) >
            maximumSpeed * (measuredAt - previous.time) / 1_000.0 + fix.accuracyMeters) motionSamples.clear()
        motionSamples.removeAll { measuredAt - it.time > MotionWindowMillis }
        motionSamples.add(MotionSample(measuredAt, distance))
        val first = motionSamples.first()
        val seconds = (measuredAt - first.time) / 1_000.0
        val displacement = distance - first.distance
        val speed = if (seconds >= 1.0 && abs(displacement) >= maxOf(2.0, fix.accuracyMeters / 2.0)) displacement / seconds else 0.0
        // Zero provider speed cancels drift; implausible jumps and reversals never create a vector.
        val movingSameDirection = previous == null || (distance - previous.distance) * speed >= 0.0
        val providerSpeed = fix.speedMetersPerSecond
        velocityMetersPerSecond = if (leg.isRouted && movingSameDirection && abs(speed) <= maximumSpeed &&
            (providerSpeed == null || providerSpeed in 0.3..maximumSpeed)) {
            if (providerSpeed == null) speed else speed.coerceIn(-providerSpeed, providerSpeed)
        } else 0.0
    }

    /** Coasts within the saved leg; after 30 seconds the estimate carries a GPS warning. */
    fun refreshFreshness() {
        val current = mutableState.value
        if (current.availability == TripLocationAvailability.PermissionDenied ||
            current.availability == TripLocationAvailability.Unsupported) return
        val measuredAt = lastFixAt ?: trackingStartedAt ?: return
        val age = (nowEpochMillis() - measuredAt).coerceAtLeast(0L)
        val anchor = anchorProgress
        val legIndex = motionLegIndex
        var estimatedProgress: Float? = null
        if (age > PredictionDelayMillis && anchor != null && legIndex != null && velocityMetersPerSecond != 0.0) {
            val start = legLengths.take(legIndex).sum()
            val end = start + legLengths[legIndex]
            val anchorDistance = anchor * routeLength
            val distance = anchorDistance + velocityMetersPerSecond * age / 1_000.0
            // Do not estimate a transfer, boarding, or arrival without a confirming measurement.
            val bounded = if (velocityMetersPerSecond > 0.0) distance.coerceIn(anchorDistance, maxOf(anchorDistance, end - 0.5))
                else distance.coerceIn(minOf(anchorDistance, start + 0.5), anchorDistance)
            estimatedProgress = (bounded / routeLength).toFloat().coerceIn(0f, 0.999f)
        }
        mutableState.value = current.copy(
            availability = when {
                age > TRIP_LOCATION_GRACE_MILLIS -> TripLocationAvailability.Stale
                estimatedProgress != null -> TripLocationAvailability.Estimated
                current.fix != null -> TripLocationAvailability.Available
                else -> TripLocationAvailability.Waiting
            },
            estimatedProgress = estimatedProgress,
            estimatedPosition = estimatedProgress?.let(routeGeometry::pointAt),
        )
    }

    /** Clears the visible reading when collection stops or debug playback takes over. */
    fun stop() {
        mutableState.value = CurrentTripLocationState()
        lastFixAt = null
        lastAcceptedTimestamp = null
        trackingStartedAt = null
        clearMotion()
    }

    /** This platform has no location adapter; the accepted route stays usable. */
    fun unsupported() {
        stop()
        mutableState.value = CurrentTripLocationState(availability = TripLocationAvailability.Unsupported)
    }
}
