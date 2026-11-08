package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.data.trips.CloudTrips
import com.example.rnd_transit_mtl.data.trips.FinalTripOutbox
import com.example.rnd_transit_mtl.data.trips.LocalCloudTrips
import com.example.rnd_transit_mtl.data.trips.SupabaseTripRepository
import com.example.rnd_transit_mtl.data.trips.TripRepository
import com.example.rnd_transit_mtl.model.ids.UserId
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.rnd_transit_mtl.data.trips.tryCloudAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.rnd_transit_mtl.data.routing.DebugTripLoader
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.rnd_transit_mtl.data.fake.FakeTransportRouteRepository
import com.example.rnd_transit_mtl.data.fake.FakeTransportTypeRepository
import com.example.rnd_transit_mtl.layout.MainLayout
import com.example.rnd_transit_mtl.model.transport.TransportRoute
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.user.LocalNicknameSession
import com.example.rnd_transit_mtl.state.user.appNicknameSession
import com.example.rnd_transit_mtl.state.trip.SessionTripsStores
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.CancellationException
import com.example.rnd_transit_mtl.View.authentication.AuthScreen
import com.example.rnd_transit_mtl.ViewModels.authentication.AuthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rnd_transit_mtl.navigation.LocalNavigator
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.Navigator
import com.example.rnd_transit_mtl.navigation.Router
import com.example.rnd_transit_mtl.navigation.TripNavigation
import com.example.rnd_transit_mtl.navigation.backStackConfig
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rnd_transit_mtl.data.authentication.AccountSession
import com.example.rnd_transit_mtl.data.authentication.AuthRepository
import com.example.rnd_transit_mtl.data.authentication.LocalAccountSession
import com.example.rnd_transit_mtl.data.authentication.LocalAuthRepository
import com.example.rnd_transit_mtl.data.authentication.SupabaseAuthRepository

private data class TransportData(
    val types: List<TransportType>,
    val routes: List<TransportRoute>
)

/**
 * Restores the account session and scopes navigation and trip data to the active identity.
 *
 * TripNavigation is recreated from those restored objects. Its temporary
 * callbacks are not part of saved state.
 *
 * MainLayout remains outside NavDisplay's destination transitions.
 */
@Composable
fun App(
    loadDebugStmTrip: DebugTripLoader? = null,
    openDebugStmPreview: Boolean = false,
    loadDebugExoTrip: DebugTripLoader? = null,
    openDebugExoPreview: Boolean = false
) {
    App(
        authRepository = remember { SupabaseAuthRepository() },
        tripRepository = { SupabaseTripRepository(it) },
        loadDebugStmTrip = loadDebugStmTrip,
        openDebugStmPreview = openDebugStmPreview,
        loadDebugExoTrip = loadDebugExoTrip,
        openDebugExoPreview = openDebugExoPreview,
    )
}

@Composable
internal fun App(
    authRepository: AuthRepository,
    tripRepository: ((UserId) -> TripRepository)? = null,
    loadDebugStmTrip: DebugTripLoader? = null,
    openDebugStmPreview: Boolean = false,
    loadDebugExoTrip: DebugTripLoader? = null,
    openDebugExoPreview: Boolean = false,
) {
    var pendingDebugPreview by rememberSaveable {
        mutableStateOf(when {
            openDebugExoPreview && loadDebugExoTrip != null -> DebugTripSource.Exo.name
            openDebugStmPreview && loadDebugStmTrip != null -> DebugTripSource.Stm.name
            else -> null
        })
    }
    val consumeDebugPreview = remember { { pendingDebugPreview = null } }
    var transportData by remember {
        mutableStateOf<TransportData?>(null)
    }
    var loadingError by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        try {
            transportData = TransportData(
                types = FakeTransportTypeRepository()
                    .getTransportTypes(),
                routes = FakeTransportRouteRepository()
                    .getTransportRoutes()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadingError = true
        }
    }

    val accountSession = authRepository.session.collectAsStateWithLifecycle(
        initialValue = AccountSession.Restoring,
    )
    var continueAsGuest by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pendingDebugPreview) {
        if (pendingDebugPreview != null) continueAsGuest = true
    }
    val outbox = remember(tripRepository != null) { tripRepository?.let { FinalTripOutbox() } }
    val sessionStores = rememberSaveable(saver = SessionTripsStores.Saver) { SessionTripsStores() }
    var lastSignedIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    val signedIdentity = (accountSession.value as? AccountSession.SignedIn)?.let { "account:${it.userId}" }
    if (signedIdentity != null) lastSignedIdentity = signedIdentity
    LaunchedEffect(accountSession.value) {
        when (accountSession.value) {
            is AccountSession.SignedIn -> {
                sessionStores.clearAccountStores(except = signedIdentity)
                continueAsGuest = false
            }
            AccountSession.Guest -> {
                sessionStores.clearAccountStores()
                outbox?.clear()
                lastSignedIdentity = null
            }
            else -> Unit
        }
    }
    val identity = when {
        accountSession.value is AccountSession.SignedIn -> signedIdentity!!
        continueAsGuest -> "guest"
        accountSession.value == AccountSession.Guest -> "guest"
        else -> lastSignedIdentity ?: "guest"
    }
    var navigationOwner by rememberSaveable { mutableStateOf(identity) }
    RNDTransitTheme {
      key(identity) {
        val backStack = rememberNavBackStack(backStackConfig, MainScreenKey)
        val navigator = remember(backStack) { Navigator(backStack) }
        val tripsStore = remember(sessionStores, identity) { sessionStores.forIdentity(identity) }
        val owner = (accountSession.value as? AccountSession.SignedIn)?.userId
        val cloudTrips = remember(owner, tripsStore) {
            if (owner == null || tripRepository == null) null else {
                tripsStore.restorePendingFinalTrips(outbox?.read(owner).orEmpty())
                tripsStore.onPendingFinalTripsChanged = { pending -> outbox?.write(owner, pending) }
                CloudTrips(tripRepository(UserId(owner)), tripsStore)
            }
        }
        val tripNavigation = remember(navigator, tripsStore) { TripNavigation(navigator, tripsStore) }
        LaunchedEffect(identity, backStack) {
            if (navigationOwner != identity) {
                backStack.clear()
                backStack.add(MainScreenKey)
                navigationOwner = identity
            }
        }
        CompositionLocalProvider(
            LocalDebugStmTripLoader provides loadDebugStmTrip,
            LocalDebugExoTripLoader provides loadDebugExoTrip,
            LocalDebugTripLaunch provides pendingDebugPreview?.let {
                DebugTripLaunch(DebugTripSource.valueOf(it), consumeDebugPreview)
            },
            LocalNavigator provides navigator,
            LocalTripsStore provides tripsStore,
            LocalCloudTrips provides cloudTrips,
            LocalTripNavigation provides tripNavigation,
            LocalNicknameSession provides appNicknameSession,
            LocalAuthRepository provides authRepository,
            LocalAccountSession provides accountSession.value
        ) {
            when {
                accountSession.value == AccountSession.Restoring -> androidx.compose.material3.CircularProgressIndicator()
                accountSession.value !is AccountSession.SignedIn && !continueAsGuest -> {
                    val authViewModel = viewModel { AuthViewModel(authRepository) }
                    MainLayout {
                        AuthScreen(authViewModel, onContinueAsGuest = {
                            continueAsGuest = true
                            appNicknameSession.signIn("Guest")
                        })
                    }
                }
                navigationOwner != identity -> Unit
                else -> {
                MainLayout {
                    Column {
                        val pending = tripsStore.state.value.pendingFinalTripIds
                        var retry by remember { mutableStateOf(0) }
                        var saveFailed by remember { mutableStateOf(false) }
                        LaunchedEffect(cloudTrips, pending, retry) {
                            if (cloudTrips != null && pending.isNotEmpty()) {
                                saveFailed = false
                                saveFailed = !tryCloudAction { cloudTrips.flushFinalTrips() }
                            }
                        }
                        if (cloudTrips != null && pending.isNotEmpty()) {
                            if (saveFailed) {
                                TextButton(onClick = { retry++ }) {
                                    Text("Trip could not be saved. Retry")
                                }
                            } else {
                                Text("Saving trip…")
                            }
                        }
                        Router(
                        backStack = backStack,
                        transportTypes = transportData?.types,
                        transportRoutes = transportData?.routes,
                        loadingError = loadingError
                        )
                    }
                }
            }
        }
      }
    }
}
}

internal val LocalDebugStmTripLoader = staticCompositionLocalOf<DebugTripLoader?> { null }
internal val LocalDebugExoTripLoader = staticCompositionLocalOf<DebugTripLoader?> { null }
internal enum class DebugTripSource { Stm, Exo }
internal data class DebugTripLaunch(val source: DebugTripSource, val consume: () -> Unit)
internal val LocalDebugTripLaunch = staticCompositionLocalOf<DebugTripLaunch?> { null }
