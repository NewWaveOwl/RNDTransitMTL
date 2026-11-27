package com.example.rnd_transit_mtl.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.rnd_transit_mtl.View.about.AboutScreen
import com.example.rnd_transit_mtl.View.authentication.AuthScreen
import com.example.rnd_transit_mtl.View.compass.CompassScreen
import com.example.rnd_transit_mtl.View.current_trip.CurrentTripScreen
import com.example.rnd_transit_mtl.View.history.HistoryScreen
import com.example.rnd_transit_mtl.View.main.MainScreen
import com.example.rnd_transit_mtl.View.profile.ProfileScreen
import com.example.rnd_transit_mtl.View.settings.SettingsScreen
import com.example.rnd_transit_mtl.View.trip_details.TripDetailsScreen
import com.example.rnd_transit_mtl.View.trip_review.TripReviewScreen
import com.example.rnd_transit_mtl.ViewModels.Accounts.ProfileViewModel
import com.example.rnd_transit_mtl.ViewModels.Settings.SettingsViewModel
import com.example.rnd_transit_mtl.ViewModels.authentication.AuthViewModel
import com.example.rnd_transit_mtl.data.accounts.LocalAccountRepository
import com.example.rnd_transit_mtl.data.authentication.AccountSession
import com.example.rnd_transit_mtl.data.authentication.LocalAccountSession
import com.example.rnd_transit_mtl.data.authentication.LocalAuthRepository
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.model.transport.TransportRoute
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalSharedTransitMapHost
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.transitions.GO_TRIP_TRANSITION_MILLIS

// Animation settings — milliseconds for lateral slides between the planner map and secondary tabs.
// Planner/trip handoff timing is defined in ui/global_ui_ellements/transitions/TripTransition.kt.
private const val PLANNER_TAB_SLIDE_DURATION_MILLIS = 480
private const val PLANNER_MAP_CONTENT_KEY = "planner-map"
private const val TRIP_MAP_CONTENT_KEY_PREFIX = "current-trip-map:"

val LocalNavigator = compositionLocalOf<Navigator> {
    error("Navigator must be provided by App.")
}

/**
 * Renders destinations beneath the already-hoisted [MainLayout] and coordinates the shared map layer.
 * All outgoing-screen callbacks check the live stack top.
 *
 * @param backStack Observable Navigation 3 destination back stack.
 * @param transportTypes Loaded transport modes, or null while loading.
 * @param transportRoutes Loaded transport routes, or null while loading.
 * @param loadingError Whether loading the bundled transport catalogue failed.
 */
@Composable
fun Router(
    backStack: NavBackStack<NavKey>,
    transportTypes: List<TransportType>?,
    transportRoutes: List<TransportRoute>?,
    loadingError: Boolean,
) {
    val navigator = LocalNavigator.current
    val mapHost = LocalSharedTransitMapHost.current
    val destination = navigator.current

    Box(Modifier.fillMaxSize()) {
        mapHost?.invoke(
            Modifier.matchParentSize(),
            destination == MainScreenKey || destination is CurrentTripScreenKey,
        )
        DestinationDisplay(backStack, transportTypes, transportRoutes, loadingError)
    }
}

/** Hosts [NavDisplay] with destination-scoped ViewModel decorators and coordinated transitions. */
@Composable
private fun DestinationDisplay(
    backStack: NavBackStack<NavKey>,
    transportTypes: List<TransportType>?,
    transportRoutes: List<TransportRoute>?,
    loadingError: Boolean,
) {
    val navigator = LocalNavigator.current
    val navigation = LocalTripNavigation.current
    val sharedLiveMap = LocalTransitMap.current != null
    val hostedMap = LocalSharedTransitMapHost.current != null

    NavDisplay(
        modifier = Modifier.fillMaxSize(),
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        onBack = { navigation.back() },
        entryProvider = entryProvider {
            entry<MainScreenKey>(clazzContentKey = { PLANNER_MAP_CONTENT_KEY }) {
                // Only the foreground destination can place the shared map during the handoff.
                if (sharedLiveMap && navigator.current is CurrentTripScreenKey) return@entry
                MainScreen(
                    transportTypes = transportTypes,
                    transportRoutes = transportRoutes,
                    loadingError = loadingError,
                    isDestinationActive = navigator.current == MainScreenKey,
                    onOpenCurrentTrip = { trip ->
                        val current = navigator.current
                        val alreadyOpened = current is CurrentTripScreenKey && current.trip.id == trip.id
                        if (current == MainScreenKey || alreadyOpened) navigation.openCurrentTrip(trip) else false
                    },
                    onOpenCompletedTrip = { tripId ->
                        if (navigator.current == MainScreenKey) navigation.openCompletedTrip(tripId) else false
                    },
                )
            }

            entry<CurrentTripScreenKey>(
                clazzContentKey = { key -> TRIP_MAP_CONTENT_KEY_PREFIX + key.trip.id },
            ) { key ->
                if (sharedLiveMap && navigator.current == MainScreenKey) return@entry
                CurrentTripScreen(
                    trip = key.trip,
                    isDestinationActive = navigator.current == key,
                    onReviewRequested = { tripId ->
                        val current = navigator.current
                        val alreadyOpened =
                            current is TripReviewScreenKey &&
                                current.tripId == tripId &&
                                current.mode == TripReviewMode.INITIAL
                        if (tripId == key.trip.id && (current == key || alreadyOpened)) {
                            navigation.openInitialReview(tripId)
                        } else {
                            false
                        }
                    },
                    onLeave = { if (navigator.current == key) navigation.returnToPlanner() },
                    onReturnToPlanner = { if (navigator.current == key) navigation.returnToPlanner() },
                )
            }

            entry<HistoryScreenKey> {
                HistoryScreen(
                    isDestinationActive = navigator.current == HistoryScreenKey,
                    onOpenDetails = { tripId ->
                        if (navigator.current == HistoryScreenKey) navigation.openDetails(tripId)
                    },
                    onReview = { tripId ->
                        if (navigator.current == HistoryScreenKey) {
                            navigation.openReview(tripId = tripId, origin = ReviewOrigin.History)
                        }
                    },
                )
            }

            entry<TripDetailsScreenKey> { key ->
                TripDetailsScreen(
                    tripId = key.tripId,
                    isDestinationActive = navigator.current == key,
                    onReturnToHistory = {
                        if (navigator.current == key) navigation.openSection(HistoryScreenKey)
                    },
                    onReview = { tripId ->
                        if (navigator.current == key && tripId == key.tripId) {
                            navigation.openReview(tripId = tripId, origin = ReviewOrigin.Details(key.tripId))
                        }
                    },
                )
            }

            entry<TripReviewScreenKey> { key ->
                val registerBack: RegisterReviewBackHandler = remember(navigation, key) {
                    { onBack -> navigation.registerReviewBackHandler(key = key, onBack = onBack) }
                }
                TripReviewScreen(
                    tripId = key.tripId,
                    mode = key.mode,
                    isDestinationActive = navigator.current == key,
                    onOpenHistory = { navigation.finishReview(key) },
                    onReturnToOrigin = { navigation.finishReview(key) },
                    registerBackHandler = registerBack,
                )
            }

            entry<CompassScreenKey> {
                val trips by LocalTripsStore.current.state
                CompassScreen(
                    activeTrip = trips.activeTrip?.trip,
                    onOpenMap = {
                        val active = trips.activeTrip?.trip
                        if (active != null) navigation.openCurrentTrip(active)
                        else navigation.returnToPlanner()
                    },
                )
            }

            entry<ProfileScreenKey> {
                val accountRepository = LocalAccountRepository.current
                val authRepository = LocalAuthRepository.current
                // Guests have no account repository, so they get no ViewModel.
                val profileViewModel = accountRepository?.let {
                    viewModel { ProfileViewModel(it, authRepository) }
                }
                ProfileScreen(profileViewModel)
            }

            entry<AuthScreenKey> { key ->
                val repository = LocalAuthRepository.current
                val session = LocalAccountSession.current
                val isCurrent = navigator.current == key
                LaunchedEffect(session, isCurrent) {
                    if (session is AccountSession.SignedIn && navigator.current == key) {
                        navigator.pop()
                    }
                }
                val authViewModel = viewModel { AuthViewModel(repository) }
                AuthScreen(authViewModel, initiallyRegistering = key.register)
            }

            entry<AboutScreenKey> {
                AboutScreen()
            }

            entry<SettingsScreenKey> {
                val accountRepository = LocalAccountRepository.current
                val authRepository = LocalAuthRepository.current
                // Guests have no account repository, so they get no ViewModel.
                val settingsViewModel = accountRepository?.let {
                    viewModel { SettingsViewModel(it, authRepository) }
                }
                SettingsScreen(settingsViewModel)
            }
        },
        transitionSpec = {
            routerTransition(
                from = initialState.entries.lastOrNull()?.contentKey,
                to = targetState.entries.lastOrNull()?.contentKey,
                sharedLiveMap = sharedLiveMap,
                hostedMap = hostedMap,
                isPop = false,
            )
        },
        popTransitionSpec = {
            routerTransition(
                from = initialState.entries.lastOrNull()?.contentKey,
                to = targetState.entries.lastOrNull()?.contentKey,
                sharedLiveMap = sharedLiveMap,
                hostedMap = hostedMap,
                isPop = true,
            )
        },
        predictivePopTransitionSpec = {
            routerTransition(
                from = initialState.entries.lastOrNull()?.contentKey,
                to = targetState.entries.lastOrNull()?.contentKey,
                sharedLiveMap = sharedLiveMap,
                hostedMap = hostedMap,
                isPop = true,
            )
        },
    )
}

/** Selects the coordinated map crossfade, tab slide, or standard horizontal slide transition. */
private fun routerTransition(
    from: Any?,
    to: Any?,
    sharedLiveMap: Boolean,
    hostedMap: Boolean,
    isPop: Boolean,
): ContentTransform = when {
    isPlannerToTripTransition(from, to) || (sharedLiveMap && isPlannerMapTransition(from, to)) ->
        EnterTransition.None togetherWith ExitTransition.None

    isPlannerMapTransition(from, to) ->
        // Returning to the planner crossfades over the stationary map.
        (EnterTransition.None togetherWith fadeOut(tween(GO_TRIP_TRANSITION_MILLIS))).apply {
            targetContentZIndex = -1f
        }

    from == PLANNER_MAP_CONTENT_KEY || to == PLANNER_MAP_CONTENT_KEY ->
        plannerTabSlide(enteringPlanner = to == PLANNER_MAP_CONTENT_KEY, hostedMap = hostedMap)

    isPop ->
        slideInHorizontally(initialOffsetX = { -it }) togetherWith slideOutHorizontally(targetOffsetX = { it })

    else ->
        slideInHorizontally(initialOffsetX = { it }) togetherWith slideOutHorizontally(targetOffsetX = { -it })
}

private fun isPlannerMapTransition(initialKey: Any?, targetKey: Any?): Boolean =
    (initialKey == PLANNER_MAP_CONTENT_KEY &&
        targetKey is String && targetKey.startsWith(TRIP_MAP_CONTENT_KEY_PREFIX)) ||
        (targetKey == PLANNER_MAP_CONTENT_KEY &&
            initialKey is String && initialKey.startsWith(TRIP_MAP_CONTENT_KEY_PREFIX))

private fun isPlannerToTripTransition(initialKey: Any?, targetKey: Any?): Boolean =
    initialKey == PLANNER_MAP_CONTENT_KEY &&
        targetKey is String && targetKey.startsWith(TRIP_MAP_CONTENT_KEY_PREFIX)

private fun plannerTabSlide(enteringPlanner: Boolean, hostedMap: Boolean): ContentTransform {
    val slide = tween<IntOffset>(durationMillis = PLANNER_TAB_SLIDE_DURATION_MILLIS, easing = FastOutSlowInEasing)
    return if (enteringPlanner) {
        (EnterTransition.None togetherWith slideOutHorizontally(slide) { it }).apply {
            targetContentZIndex = -1f
        }
    } else {
        val exit = if (hostedMap) ExitTransition.None else slideOutHorizontally(slide) { -it }
        slideInHorizontally(slide) { it } togetherWith exit
    }
}
