package com.example.rnd_transit_mtl.View.together

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rnd_transit_mtl.ViewModels.together.TogetherTripAction
import com.example.rnd_transit_mtl.ViewModels.together.TogetherViewModel
import com.example.rnd_transit_mtl.ViewModels.together.togetherTripAction
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore

/**
 * Together tab destination. Connects [TogetherViewModel] and the app's active trip to
 * [TogetherContent]; navigation stays with the caller.
 *
 * @param viewModel Group state owner, or null for a guest (who sees the sign-in invitation).
 * @param currentUserId The signed-in user, or null for a guest.
 * @param onGo Returns to the planner.
 * @param onSignIn Opens sign-in.
 * @param onOpenTrip Opens a trip on the current-trip screen.
 */
@Composable
internal fun TogetherScreen(
    viewModel: TogetherViewModel?,
    currentUserId: UserId?,
    onGo: () -> Unit,
    onSignIn: () -> Unit,
    onOpenTrip: (Trip) -> Unit,
) {
    val trips by LocalTripsStore.current.state
    val activeTrip = trips.activeTrip?.trip
    val state = viewModel?.state?.collectAsStateWithLifecycle()?.value

    LaunchedEffect(state?.openTrip) {
        val started = state?.openTrip ?: return@LaunchedEffect
        onOpenTrip(activeTrip?.takeIf { it.id == started.id } ?: started)
        viewModel.onTripOpened()
    }

    val tripAction = if (state != null && currentUserId != null) {
        togetherTripAction(state, currentUserId, activeTrip)
    } else {
        TogetherTripAction.None
    }

    TogetherContent(
        state = state,
        currentUserId = currentUserId,
        tripAction = tripAction,
        callbacks = TogetherCallbacks(
            onGo = onGo,
            onSignIn = onSignIn,
            onCreate = { viewModel?.create() },
            onToggleJoin = { viewModel?.toggleJoinForm() },
            onCodeChange = { viewModel?.onCodeChange(it) },
            onJoin = { viewModel?.join() },
            onLeave = { viewModel?.leave() },
            onShareTrip = { activeTrip?.let { viewModel?.shareActiveTrip(it) } },
            onStartTrip = { viewModel?.startGroupTrip() },
            onOpenActiveTrip = { activeTrip?.let(onOpenTrip) },
            onDismissMessage = { viewModel?.dismissMessage() },
            onRetry = { viewModel?.reload() },
        ),
    )
}
