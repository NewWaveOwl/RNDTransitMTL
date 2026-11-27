package com.example.rnd_transit_mtl.ViewModels.together

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rnd_transit_mtl.data.group.GroupFailure
import com.example.rnd_transit_mtl.data.group.GroupRepository
import com.example.rnd_transit_mtl.data.group.GroupSync
import com.example.rnd_transit_mtl.model.group.Group
import com.example.rnd_transit_mtl.model.group.GroupJoinCode
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripJourneyKind
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether live group updates are currently arriving. */
internal enum class GroupConnection { Live, Reconnecting }

/** The trip action the Together screen offers, given the group and the user's active trip. */
internal enum class TogetherTripAction {
    /** Nothing to offer: no group, or the host hasn't planned a trip yet. */
    None,

    /** The host has an active solo trip they can share with the group. */
    ShareActiveTrip,

    /** The host shared a trip and this member hasn't started it yet. */
    StartGroupTrip,

    /** The user is already on the group's current trip. */
    OnGroupTrip,

    /** The host shared a trip, but this member is on another trip and must finish it first. */
    FinishCurrentTripFirst,

    /** No trip shared yet; members wait for the host. */
    WaitForHost,
}

/**
 * Together screen state.
 *
 * @property loading True until the first check for an existing group finishes.
 * @property group The open group the user is in, or null.
 * @property joinFormOpen Whether the join-by-code field is shown.
 * @property codeInput Code typed so far, normalized to upper case without spaces.
 * @property busy True while a create, join, leave, share or start request runs.
 * @property message Error or notice to show once, such as an expired code or a group that ended.
 * @property connection Whether live updates are arriving.
 * @property openTrip A group trip that just started; the screen opens it, then calls
 * [TogetherViewModel.onTripOpened].
 */
internal data class TogetherUiState(
    val loading: Boolean = true,
    val group: Group? = null,
    val joinFormOpen: Boolean = false,
    val codeInput: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val connection: GroupConnection = GroupConnection.Live,
    val openTrip: Trip? = null,
) {
    /** True when [codeInput] is a complete, valid code. */
    val codeValid: Boolean get() = GroupJoinCode.parseOrNull(codeInput) != null
}

/**
 * Decides which trip action to offer. Kept outside the ViewModel because the active trip
 * is owned by [TripsStore], the single source of truth, and read by the screen.
 *
 * @param state Current Together state.
 * @param userId The signed-in user.
 * @param activeTrip The user's active trip, or null.
 */
internal fun togetherTripAction(state: TogetherUiState, userId: UserId, activeTrip: Trip?): TogetherTripAction {
    val group = state.group ?: return TogetherTripAction.None
    val sharedId = group.currentTripId
    if (sharedId != null && activeTrip?.groupTripId == sharedId) return TogetherTripAction.OnGroupTrip
    return if (group.isHost(userId)) {
        val shareable = activeTrip != null && activeTrip.groupTripId == null &&
            activeTrip.journey?.kind != TripJourneyKind.Return
        if (shareable) TogetherTripAction.ShareActiveTrip else TogetherTripAction.None
    } else when {
        sharedId == null -> TogetherTripAction.WaitForHost
        activeTrip != null -> TogetherTripAction.FinishCurrentTripFirst
        else -> TogetherTripAction.StartGroupTrip
    }
}

/**
 * Owns the Together screen: finding, creating, joining and leaving a group, following it
 * live, and sharing or starting the group trip.
 *
 * @param repository Server access for groups.
 * @param tripsStore The app's trip store; group trips start and link here.
 */
internal class TogetherViewModel(
    private val repository: GroupRepository,
    private val tripsStore: TripsStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TogetherUiState())
    val state: StateFlow<TogetherUiState> = mutableState.asStateFlow()
    private var following: Job? = null

    init {
        reload()
    }

    /** Looks for the user's open group again, for example after a failed first load. */
    fun reload() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true) }
            val group = attempt { repository.currentGroup() }
            mutableState.update { it.copy(loading = false) }
            group?.let(::follow)
        }
    }

    /** Creates a group hosted by the user. */
    fun create() = runAction { follow(repository.createGroup()) }

    /** Shows or hides the join-by-code field. */
    fun toggleJoinForm() = mutableState.update { it.copy(joinFormOpen = !it.joinFormOpen, codeInput = "") }

    /**
     * @param text Code as typed; spaces are dropped, letters upper-cased, and it is capped at
     * [GroupJoinCode.LENGTH] characters.
     */
    fun onCodeChange(text: String) = mutableState.update {
        it.copy(codeInput = text.filterNot(Char::isWhitespace).uppercase().take(GroupJoinCode.LENGTH))
    }

    /** Joins the group whose code was typed. Does nothing until the code is valid. */
    fun join() {
        val code = GroupJoinCode.parseOrNull(mutableState.value.codeInput) ?: return
        runAction {
            val group = repository.joinGroup(code)
            mutableState.update { it.copy(joinFormOpen = false, codeInput = "") }
            follow(group)
        }
    }

    /** Leaves the group. When the host leaves, the group ends for everyone. */
    fun leave() {
        val group = mutableState.value.group ?: return
        runAction {
            // Stop first, so the host's own leave isn't reported back as "the host left".
            stopFollowing()
            try {
                repository.leaveGroup(group.id)
            } catch (failure: Exception) {
                if (failure !is CancellationException) follow(group)
                throw failure
            }
            mutableState.update { it.copy(group = null, connection = GroupConnection.Live) }
        }
    }

    /**
     * Shares the host's active trip with the group and links it to the shared trip.
     *
     * @param trip The host's active trip.
     */
    fun shareActiveTrip(trip: Trip) {
        val group = mutableState.value.group ?: return
        runAction {
            val sharedId = repository.shareTrip(group.id, trip)
            val linked = tripsStore.attachGroupTrip(trip.id, sharedId)
            if (linked != TripActionResult.Applied) {
                mutableState.update { it.copy(message = "Shared, but this trip ended before it could be linked.") }
            }
            mutableState.update { state ->
                state.copy(group = state.group?.copy(currentTripId = sharedId))
            }
        }
    }

    /** Starts the user's own copy of the group's current trip. */
    fun startGroupTrip() {
        val sharedId = mutableState.value.group?.currentTripId ?: return
        runAction {
            val trip = repository.startGroupTrip(sharedId)
            when (val result = tripsStore.start(trip)) {
                TripActionResult.Applied -> mutableState.update { it.copy(openTrip = trip) }
                is TripActionResult.InvalidInput -> mutableState.update { it.copy(message = result.message) }
                else -> mutableState.update { it.copy(message = "Finish your current trip before starting the group trip.") }
            }
        }
    }

    /** Call after the screen has opened [TogetherUiState.openTrip]. */
    fun onTripOpened() = mutableState.update { it.copy(openTrip = null) }

    /** Call after [TogetherUiState.message] has been shown. */
    fun dismissMessage() = mutableState.update { it.copy(message = null) }

    private fun follow(group: Group) {
        stopFollowing()
        mutableState.update { it.copy(group = group, connection = GroupConnection.Live) }
        following = viewModelScope.launch {
            repository.observeGroup(group.id).collect { sync ->
                when (sync) {
                    is GroupSync.Live -> mutableState.update {
                        it.copy(group = sync.group, connection = GroupConnection.Live)
                    }
                    GroupSync.Reconnecting -> mutableState.update { it.copy(connection = GroupConnection.Reconnecting) }
                    GroupSync.Closed -> {
                        // Trips already started keep going; only the group is gone.
                        mutableState.update {
                            it.copy(group = null, connection = GroupConnection.Live, message = GROUP_ENDED_MESSAGE)
                        }
                        following?.cancel()
                    }
                }
            }
        }
    }

    private fun stopFollowing() {
        following?.cancel()
        following = null
    }

    /** Runs one user action at a time; duplicate taps while busy are ignored. */
    private fun runAction(action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                attempt { action() }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    /** Runs a server call, showing its failure message instead of throwing. */
    private suspend fun <T> attempt(call: suspend () -> T): T? = try {
        call()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: GroupFailure) {
        mutableState.update { it.copy(message = failure.message) }
        null
    } catch (_: Exception) {
        mutableState.update { it.copy(message = GroupFailure.Unexpected().message) }
        null
    }

    internal companion object {
        const val GROUP_ENDED_MESSAGE = "The host left, so the group has ended. Your trip continues."
    }
}
