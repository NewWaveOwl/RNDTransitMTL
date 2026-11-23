package com.example.rnd_transit_mtl.ViewModels.current_trip

import androidx.lifecycle.ViewModel
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.data.location.LocationJumpFilter
import com.example.rnd_transit_mtl.data.location.LOCATION_FIX_MAX_AGE_MILLIS
import com.example.rnd_transit_mtl.data.location.LOCATION_UPDATE_TIMEOUT_MILLIS
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripRouteGeometry
import com.example.rnd_transit_mtl.model.trip.routePosition
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.time.Clock

internal const val TRIP_LOCATION_MAX_AGE_MILLIS = LOCATION_FIX_MAX_AGE_MILLIS
internal const val TRIP_LOCATION_GRACE_MILLIS = LOCATION_UPDATE_TIMEOUT_MILLIS
private const val PredictionDelayMillis = 4_000L
private const val MotionWindowMillis = 10_000L
private const val TripLocationMaximumAccuracyMeters = 50.0
private const val TripRouteToleranceMeters = 35.0
private const val TripArrivalToleranceMeters = 20.0

/** Estimates are explicit and never become recorded GPS progress. */
internal enum class TripLocationAvailability { Waiting, LastKnown, Available, LowAccuracy, Estimated, Stale, PermissionDenied, Unsupported }

/** Keeps the real reading separate from a temporary route estimate. */
internal data class CurrentTripLocationState(
    val fix: LocationUpdate.Fix? = null,
    val availability: TripLocationAvailability = TripLocationAvailability.Waiting,
    val offRoute: Boolean = false,
    val estimatedPosition: GeoPoint? = null,
    val estimatedProgress: Float? = null,
    val matchedPosition: GeoPoint? = null,
    val matchedProgress: Float? = null,
    val uncertaintyRadiusMeters: Double? = null,
    val retainedPosition: GeoPoint? = null,
    val retainedProgress: Float? = null,
) {
    val displayedPosition: GeoPoint? get() = retainedPosition ?: estimatedPosition ?: matchedPosition ?: fix?.point
    val displayedProgress: Float? get() = retainedProgress ?: estimatedProgress ?: matchedProgress
}

/**
 * Matches fresh GPS to one accepted trip and keeps its real progress in the shared store.
 * Platform permission requests and lifecycle collection remain at the screen boundary.
 * @param trip Accepted route; geometry and identity are never regenerated.
 * @param tripsStore Shared authoritative trip state.
 * @param initialFix Shared position from the planner, displayed before live collection starts.
 * @param nowEpochMillis Clock for freshness and route-bounded, display-only gap estimates.
 */
internal class CurrentTripLocationViewModel(
    private val trip: Trip,
    private val tripsStore: TripsStore,
    initialFix: LocationUpdate.Fix? = null,
    private val jumpFilter: LocationJumpFilter = LocationJumpFilter(),
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(CurrentTripLocationState())
    val state = mutableState.asStateFlow()
    private var lastFixAt: Long? = null
    private var lastReadingAt: Long? = null
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
    private var lastProgressAt: Long? = null
    private data class ProgressRecovery(val point: GeoPoint, val progress: Float, val time: Long)
    private var progressRecovery: ProgressRecovery? = null
    private val restoredProgress = tripsStore.state.value.activeTrip
        ?.takeIf { it.trip.id == trip.id }?.routeProgress
    private var previousProgress = restoredProgress ?: 0f
    private var hasObservedRoute = restoredProgress?.let { it < 0.98f } == true

    init {
        useSharedFix(initialFix)
    }

    /** Reuses the app's cached position without treating it as new progress or a motion sample. */
    fun useSharedFix(fix: LocationUpdate.Fix?) {
        if (fix == null || mutableState.value.fix != null ||
            mutableState.value.availability == TripLocationAvailability.PermissionDenied ||
            mutableState.value.availability == TripLocationAvailability.Unsupported) return
        val now = nowEpochMillis()
        val measuredAt = fix.timestampEpochMillis ?: now
        if (measuredAt > now) return
        if (trackingStartedAt == null) trackingStartedAt = now
        lastReadingAt = measuredAt
        jumpFilter.seed(fix, now)
        if (fix.accuracyMeters <= TripLocationMaximumAccuracyMeters) lastFixAt = measuredAt
        mutableState.value = CurrentTripLocationState(fix, TripLocationAvailability.LastKnown)
        refreshFreshness()
    }

    /** Starts the initial-fix grace period when the active screen resumes. */
    fun startTracking() {
        if (trackingStartedAt == null) trackingStartedAt = nowEpochMillis()
        jumpFilter.useMaximumSpeed(this, trackingMaximumSpeed())
    }

    /** @param update Latest provider reading or actual provider failure. */
    fun receive(update: LocationUpdate) {
        startTracking()
        when (update) {
            is LocationUpdate.Fix -> receiveFix(update)
            LocationUpdate.PermissionDenied -> {
                clearMotion()
                mutableState.value = withUncertainty(mutableState.value.copy(availability = TripLocationAvailability.PermissionDenied))
            }
            // Provider outages use the same grace period, retaining the last good position.
            LocationUpdate.Unavailable -> refreshFreshness()
        }
    }

    private fun receiveFix(fix: LocationUpdate.Fix) {
        val now = nowEpochMillis()
        val measuredAt = fix.timestampEpochMillis ?: now
        if (now - measuredAt !in 0L..TRIP_LOCATION_MAX_AGE_MILLIS ||
            lastAcceptedTimestamp?.let { measuredAt <= it } == true) {
            refreshFreshness()
            return
        }
        lastAcceptedTimestamp = measuredAt
        lastReadingAt = measuredAt
        val decision = jumpFilter.evaluate(fix, now) ?: return
        if (!decision.accepted) {
            holdPosition(fix, decision.trustedFix?.point)
            return
        }
        if (decision.relocated) clearMotion()
        if (fix.accuracyMeters > TripLocationMaximumAccuracyMeters) {
            // Both maps use the shared trusted measurement; weak GPS never pulls the marker onto a route.
            holdPosition(fix, decision.trustedFix?.point)
            return
        }
        lastFixAt = measuredAt
        val matched = trip.routePosition(fix.point, previousProgress)
        val onRoute = matched != null && matched.distanceFromRouteMeters <= TripRouteToleranceMeters
        mutableState.value = withUncertainty(CurrentTripLocationState(fix, TripLocationAvailability.Available, !onRoute))
        if (!onRoute) {
            clearMotion()
            if (decision.relocated) hasObservedRoute = false
            return
        }
        if (decision.relocated) {
            // Recover the visible position, never skipped travel or an arrival.
            progressRecovery = ProgressRecovery(fix.point, matched.progress, measuredAt)
            hasObservedRoute = false
            clearMotion()
            return
        }
        val recovery = progressRecovery
        if (recovery != null && (fix.point.distanceMetersTo(recovery.point) <= maxOf(5.0, fix.accuracyMeters) ||
                fix.speedMetersPerSecond?.let { it < 0.3 } == true)) return
        val referenceProgress = recovery?.progress ?: previousProgress
        val progressAge = (recovery?.time ?: lastProgressAt)?.let {
            (measuredAt - it).coerceIn(0L, 30_000L) / 1_000.0
        }
        val travelBudget = 10.0 + minOf(fix.accuracyMeters, 25.0) +
            trackingMaximumSpeed() * (progressAge ?: 0.0)
        if (progressAge != null && abs(matched.progress - referenceProgress) * routeLength > travelBudget) {
            // Crossing/parallel geometry must not jump confirmed progress to a distant branch.
            clearMotion()
            return
        }
        progressRecovery = null
        recordMotion(fix, matched.progress, measuredAt)
        var progress = matched.progress
        val atDestination = fix.point.distanceMetersTo(trip.destination.point) <= TripArrivalToleranceMeters
        if (progress >= 0.98f && atDestination && hasObservedRoute) progress = 1f
        if (progress >= 0.999f && (!hasObservedRoute || !atDestination)) progress = 0.999f
        previousProgress = progress
        lastProgressAt = measuredAt
        jumpFilter.useMaximumSpeed(this, trackingMaximumSpeed())
        if (progress < 0.98f) hasObservedRoute = true
        tripsStore.updateRouteProgress(trip.id, progress)
    }

    private fun holdPosition(fix: LocationUpdate.Fix, trustedPosition: GeoPoint? = null) {
        val current = mutableState.value
        clearMotion()
        mutableState.value = withUncertainty(CurrentTripLocationState(
            fix = fix,
            availability = if (fix.accuracyMeters > TripLocationMaximumAccuracyMeters)
                TripLocationAvailability.LowAccuracy else TripLocationAvailability.LastKnown,
            offRoute = current.offRoute,
            retainedPosition = trustedPosition ?: jumpFilter.acceptedFix?.point ?: current.displayedPosition,
            retainedProgress = previousProgress,
        ))
    }

    private fun maximumSpeed(legIndex: Int): Double {
        val leg = trip.legs[legIndex]
        val label = trip.selectedTransports.firstOrNull { it.id == leg.transportId }?.label.orEmpty().lowercase()
        return when {
            "walk" in label || "walk" in leg.transportId.value.lowercase() -> 3.5
            "bik" in label || "cycl" in label -> 15.0
            else -> 45.0
        }
    }

    private fun trackingMaximumSpeed(): Double {
        val legIndex = routeGeometry.legIndexAt(previousProgress)
        val remaining = legLengths.take(legIndex + 1).sum() - previousProgress * routeLength
        val nextLeg = (legIndex + 1).takeIf { it < trip.legs.size && remaining <= 30.0 }
        return maxOf(maximumSpeed(legIndex), nextLeg?.let(::maximumSpeed) ?: 0.0)
    }

    private fun withUncertainty(state: CurrentTripLocationState): CurrentTripLocationState {
        val fix = state.fix ?: return state
        val position = state.displayedPosition ?: return state
        val ageSeconds = ((nowEpochMillis() - (lastReadingAt ?: fix.timestampEpochMillis ?: nowEpochMillis())).coerceAtLeast(0L) / 1_000.0 - 4.0).coerceAtLeast(0.0)
        val retained = state.availability in setOf(TripLocationAvailability.Stale, TripLocationAvailability.LastKnown,
            TripLocationAvailability.PermissionDenied, TripLocationAvailability.Unsupported)
        val minimum = if (retained || fix.accuracyMeters > TripLocationMaximumAccuracyMeters) 75.0 else 10.0
        val growth = (ageSeconds * maximumSpeed(routeGeometry.legIndexAt(previousProgress))).coerceAtMost(2_000.0)
        return state.copy(uncertaintyRadiusMeters = maxOf(minimum, fix.accuracyMeters + fix.point.distanceMetersTo(position) + growth))
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
        val legIndex = routeGeometry.legIndexAt(progress)
        if (motionLegIndex != legIndex) clearMotion()
        motionLegIndex = legIndex
        anchorProgress = progress
        val leg = trip.legs[legIndex]
        val maximumSpeed = maximumSpeed(legIndex)
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

    /** Accuracy controls progress; only 30 seconds without fresh readings triggers the GPS warning. */
    fun refreshFreshness() {
        val current = mutableState.value
        if (current.availability == TripLocationAvailability.PermissionDenied ||
            current.availability == TripLocationAvailability.Unsupported) return
        val measuredAt = lastReadingAt ?: trackingStartedAt ?: return
        val now = nowEpochMillis()
        val readingAge = (now - measuredAt).coerceAtLeast(0L)
        val motionAge = lastFixAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        val anchor = anchorProgress
        val legIndex = motionLegIndex
        var estimatedProgress: Float? = null
        if (motionAge > PredictionDelayMillis && anchor != null && legIndex != null && velocityMetersPerSecond != 0.0) {
            val start = legLengths.take(legIndex).sum()
            val end = start + legLengths[legIndex]
            val anchorDistance = anchor * routeLength
            val distance = anchorDistance + velocityMetersPerSecond * motionAge / 1_000.0
            // Do not estimate a transfer, boarding, or arrival without a confirming measurement.
            val bounded = if (velocityMetersPerSecond > 0.0) distance.coerceIn(anchorDistance, maxOf(anchorDistance, end - 0.5))
                else distance.coerceIn(minOf(anchorDistance, start + 0.5), anchorDistance)
            estimatedProgress = (bounded / routeLength).toFloat().coerceIn(0f, 0.999f)
        }
        mutableState.value = withUncertainty(current.copy(
            availability = when {
                readingAge > TRIP_LOCATION_GRACE_MILLIS -> TripLocationAvailability.Stale
                estimatedProgress != null -> TripLocationAvailability.Estimated
                current.availability == TripLocationAvailability.LastKnown -> TripLocationAvailability.LastKnown
                current.fix?.let { it.accuracyMeters > TripLocationMaximumAccuracyMeters } == true -> TripLocationAvailability.LowAccuracy
                current.fix != null -> TripLocationAvailability.Available
                else -> TripLocationAvailability.Waiting
            },
            estimatedProgress = estimatedProgress ?: current.estimatedProgress,
            estimatedPosition = estimatedProgress?.let(routeGeometry::pointAt) ?: current.estimatedPosition,
        ))
    }

    /** Stops prediction while retaining the last visible position across screen/lifecycle changes. */
    fun stop() {
        val current = mutableState.value
        mutableState.value = withUncertainty(current.copy(availability = when {
            current.availability == TripLocationAvailability.PermissionDenied -> TripLocationAvailability.PermissionDenied
            current.fix != null -> TripLocationAvailability.LastKnown
            else -> TripLocationAvailability.Waiting
        }))
        trackingStartedAt = null
        clearMotion()
        jumpFilter.releaseMaximumSpeed(this)
    }

    /** This platform has no location adapter; the accepted route stays usable. */
    fun unsupported() {
        stop()
        mutableState.value = withUncertainty(mutableState.value.copy(availability = TripLocationAvailability.Unsupported))
    }
}
