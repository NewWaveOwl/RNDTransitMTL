package com.example.rnd_transit_mtl.navigation

import com.example.rnd_transit_mtl.model.ids.TripId
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.example.rnd_transit_mtl.model.transport.TransportRoute
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.ui.global_ui_ellements.transitions.GO_TRIP_TRANSITION_MILLIS
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import com.example.rnd_transit_mtl.App
import com.example.rnd_transit_mtl.View.about.AboutScreen
import com.example.rnd_transit_mtl.View.current_trip.CurrentTripScreen
import com.example.rnd_transit_mtl.View.history.HistoryScreen
import com.example.rnd_transit_mtl.View.main.MainScreen
import com.example.rnd_transit_mtl.View.profile.ProfileScreen
import com.example.rnd_transit_mtl.View.settings.SettingsScreen
import com.example.rnd_transit_mtl.View.trip_details.TripDetailsScreen
import com.example.rnd_transit_mtl.View.trip_review.TripReviewScreen

/**
 * Literal sealed-class route hierarchy required by the assignment.
 */
@Serializable
@SerialName("com.example.rnd_transit_mtl.ScreenKey")
sealed class ScreenKey : NavKey {
    abstract val screenTitle: String
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.MainScreenKey")
data object MainScreenKey : ScreenKey() {
    override val screenTitle = "Home"
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.ProfileScreenKey")
data object ProfileScreenKey : ScreenKey() {
    override val screenTitle = "user"
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.AboutScreenKey")
data object AboutScreenKey : ScreenKey() {
    override val screenTitle = "about"
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.SettingsScreenKey")
data object SettingsScreenKey : ScreenKey() {
    override val screenTitle = "settings"
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.HistoryScreenKey")
data object HistoryScreenKey : ScreenKey() {
    override val screenTitle = "history"
}

/**
 * Demonstrates passing the generated item to the second content screen.
 *
 * The screen uses trip.id to resolve authoritative runtime state.
 */
@Serializable
@SerialName("com.example.rnd_transit_mtl.CurrentTripScreenKey")
data class CurrentTripScreenKey(
    val trip: Trip
) : ScreenKey() {
    override val screenTitle = "Current trip"
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.TripDetailsScreenKey")
data class TripDetailsScreenKey(
    val tripId: TripId
) : ScreenKey() {
    override val screenTitle = "Trip details"
}

/**
 * Restricted, serializable return destinations for review editing.
 *
 * Saving the origin avoids relying on a transient callback after rotation.
 */
@Serializable
@SerialName("com.example.rnd_transit_mtl.ReviewOrigin")
sealed class ReviewOrigin {
    abstract fun destination(): ScreenKey

    @Serializable
    @SerialName("com.example.rnd_transit_mtl.ReviewOrigin.History")
    data object History : ReviewOrigin() {
        override fun destination(): ScreenKey = HistoryScreenKey
    }

    @Serializable
    @SerialName("com.example.rnd_transit_mtl.ReviewOrigin.Details")
    data class Details(
        val tripId: TripId
    ) : ReviewOrigin() {
        override fun destination(): ScreenKey =
            TripDetailsScreenKey(tripId)
    }
}

@Serializable
@SerialName("com.example.rnd_transit_mtl.TripReviewScreenKey")
data class TripReviewScreenKey(
    val tripId: TripId,
    val mode: TripReviewMode,
    val origin: ReviewOrigin = ReviewOrigin.History
) : ScreenKey() {
    override val screenTitle = "Rate your trip"
}

/**
 * Every concrete NavKey remains explicitly registered.
 * ReviewOrigin uses its generated sealed serializer inside the review key.
 */
val backStackConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(MainScreenKey::class, MainScreenKey.serializer())
            subclass(ProfileScreenKey::class, ProfileScreenKey.serializer())
            subclass(AboutScreenKey::class, AboutScreenKey.serializer())
            subclass(SettingsScreenKey::class, SettingsScreenKey.serializer())
            subclass(HistoryScreenKey::class, HistoryScreenKey.serializer())
            subclass(
                CurrentTripScreenKey::class,
                CurrentTripScreenKey.serializer()
            )
            subclass(
                TripReviewScreenKey::class,
                TripReviewScreenKey.serializer()
            )
            subclass(
                TripDetailsScreenKey::class,
                TripDetailsScreenKey.serializer()
            )
        }
    }
}

val LocalNavigator = compositionLocalOf<Navigator> {
    error("Navigator must be provided by App.")
}

/**
 * Renders destinations beneath the already-hoisted MainLayout.
 *
 * All outgoing-screen callbacks check the live stack top.
 */
@Composable
fun Router(
    backStack: NavBackStack<NavKey>,
    transportTypes: List<TransportType>?,
    transportRoutes: List<TransportRoute>?,
    loadingError: Boolean
) {
    val navigator = LocalNavigator.current
    val navigation = LocalTripNavigation.current
    // Saved completion waits for the user's 100% action, including after restoration.

    NavDisplay(
        modifier = Modifier.fillMaxSize(),
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        onBack = {
            navigation.back()
        },
        entryProvider = entryProvider {
            entry<MainScreenKey>(clazzContentKey = { PLANNER_MAP_CONTENT_KEY }) {
                MainScreen(
                    transportTypes = transportTypes,
                    transportRoutes = transportRoutes,
                    loadingError = loadingError,
                    isDestinationActive =
                        navigator.current == MainScreenKey,
                    onOpenCurrentTrip = { trip ->
                        val current = navigator.current
                        val alreadyOpened =
                            current is CurrentTripScreenKey &&
                                    current.trip.id == trip.id

                        if (
                            current == MainScreenKey ||
                            alreadyOpened
                        ) {
                            navigation.openCurrentTrip(trip)
                        } else {
                            false
                        }
                    },
                    onOpenCompletedTrip = { tripId ->
                        if (navigator.current == MainScreenKey) {
                            navigation.openCompletedTrip(tripId)
                        } else {
                            false
                        }
                    }
                )
            }

            entry<CurrentTripScreenKey>(
                clazzContentKey = { key -> TRIP_MAP_CONTENT_KEY_PREFIX + key.trip.id }
            ) { key ->
                CurrentTripScreen(
                    trip = key.trip,
                    isDestinationActive = navigator.current == key,
                    onReviewRequested = { tripId ->
                        val current = navigator.current
                        val alreadyOpened =
                            current is TripReviewScreenKey &&
                                    current.tripId == tripId &&
                                    current.mode == TripReviewMode.INITIAL

                        if (
                            tripId == key.trip.id &&
                            (current == key || alreadyOpened)
                        ) {
                            navigation.openInitialReview(tripId)
                        } else {
                            false
                        }
                    },
                    onLeave = {
                        if (navigator.current == key) {
                            navigation.returnToPlanner()
                        }
                    },
                    onReturnToPlanner = {
                        if (navigator.current == key) {
                            navigation.returnToPlanner()
                        }
                    }
                )
            }

            entry<HistoryScreenKey> {
                HistoryScreen(
                    isDestinationActive =
                        navigator.current == HistoryScreenKey,
                    onOpenDetails = { tripId ->
                        if (navigator.current == HistoryScreenKey) {
                            navigation.openDetails(tripId)
                        }
                    },
                    onReview = { tripId ->
                        if (navigator.current == HistoryScreenKey) {
                            navigation.openReview(
                                tripId = tripId,
                                origin = ReviewOrigin.History
                            )
                        }
                    }
                )
            }

            entry<TripDetailsScreenKey> { key ->
                TripDetailsScreen(
                    tripId = key.tripId,
                    isDestinationActive = navigator.current == key,
                    onReturnToHistory = {
                        if (navigator.current == key) {
                            navigation.openSection(HistoryScreenKey)
                        }
                    },
                    onReview = { tripId ->
                        if (
                            navigator.current == key &&
                            tripId == key.tripId
                        ) {
                            navigation.openReview(
                                tripId = tripId,
                                origin = ReviewOrigin.Details(key.tripId)
                            )
                        }
                    }
                )
            }

            entry<TripReviewScreenKey> { key ->
                val registerBack: RegisterReviewBackHandler =
                    remember(navigation, key) {
                        { onBack ->
                            navigation.registerReviewBackHandler(
                                key = key,
                                onBack = onBack
                            )
                        }
                    }

                TripReviewScreen(
                    tripId = key.tripId,
                    mode = key.mode,
                    isDestinationActive = navigator.current == key,
                    onOpenHistory = {
                        navigation.finishReview(key)
                    },
                    onReturnToOrigin = {
                        navigation.finishReview(key)
                    },
                    registerBackHandler = registerBack
                )
            }

            entry<ProfileScreenKey> {
                ProfileScreen()
            }

            entry<AboutScreenKey> {
                AboutScreen()
            }

            entry<SettingsScreenKey> {
                SettingsScreen()
            }
        },
        transitionSpec = {
            if (isPlannerToTripTransition(
                    initialState.entries.lastOrNull()?.contentKey,
                    targetState.entries.lastOrNull()?.contentKey
                )
            ) {
                // Planner controls have already slid out before changing the stack.
                EnterTransition.None togetherWith ExitTransition.None
            } else if (isPlannerMapTransition(
                    initialState.entries.lastOrNull()?.contentKey,
                    targetState.entries.lastOrNull()?.contentKey
                )
            ) {
                // Returning to the planner crossfades over the stationary map.
                (EnterTransition.None togetherWith fadeOut(tween(GO_TRIP_TRANSITION_MILLIS))).apply {
                    targetContentZIndex = -1f
                }
            } else {
                slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
            }
        },
        popTransitionSpec = {
            if (isPlannerToTripTransition(
                    initialState.entries.lastOrNull()?.contentKey,
                    targetState.entries.lastOrNull()?.contentKey
                )
            ) {
                // Planner controls have already slid out before changing the stack.
                EnterTransition.None togetherWith ExitTransition.None
            } else if (isPlannerMapTransition(
                    initialState.entries.lastOrNull()?.contentKey,
                    targetState.entries.lastOrNull()?.contentKey
                )
            ) {
                (EnterTransition.None togetherWith fadeOut(tween(GO_TRIP_TRANSITION_MILLIS))).apply {
                    targetContentZIndex = -1f
                }
            } else {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        },
        predictivePopTransitionSpec = {
            if (isPlannerToTripTransition(
                    initialState.entries.lastOrNull()?.contentKey,
                    targetState.entries.lastOrNull()?.contentKey
                )
            ) {
                // Planner controls have already slid out before changing the stack.
                EnterTransition.None togetherWith ExitTransition.None
            } else if (isPlannerMapTransition(
                    initialState.entries.lastOrNull()?.contentKey,
                    targetState.entries.lastOrNull()?.contentKey
                )
            ) {
                (EnterTransition.None togetherWith fadeOut(tween(GO_TRIP_TRANSITION_MILLIS))).apply {
                    targetContentZIndex = -1f
                }
            } else {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        }
    )
}

private const val PLANNER_MAP_CONTENT_KEY = "planner-map"
private const val TRIP_MAP_CONTENT_KEY_PREFIX = "current-trip-map:"

private fun isPlannerMapTransition(initialKey: Any?, targetKey: Any?): Boolean =
    (initialKey == PLANNER_MAP_CONTENT_KEY &&
        targetKey is String && targetKey.startsWith(TRIP_MAP_CONTENT_KEY_PREFIX)) ||
        (targetKey == PLANNER_MAP_CONTENT_KEY &&
            initialKey is String && initialKey.startsWith(TRIP_MAP_CONTENT_KEY_PREFIX))

private fun isPlannerToTripTransition(initialKey: Any?, targetKey: Any?): Boolean =
    initialKey == PLANNER_MAP_CONTENT_KEY &&
        targetKey is String && targetKey.startsWith(TRIP_MAP_CONTENT_KEY_PREFIX)
