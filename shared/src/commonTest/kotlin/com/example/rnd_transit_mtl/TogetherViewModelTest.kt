package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.ViewModels.together.GroupConnection
import com.example.rnd_transit_mtl.ViewModels.together.TogetherTripAction
import com.example.rnd_transit_mtl.ViewModels.together.TogetherUiState
import com.example.rnd_transit_mtl.ViewModels.together.TogetherViewModel
import com.example.rnd_transit_mtl.ViewModels.together.togetherTripAction
import com.example.rnd_transit_mtl.data.group.GroupFailure
import com.example.rnd_transit_mtl.data.group.GroupRepository
import com.example.rnd_transit_mtl.data.group.GroupSync
import com.example.rnd_transit_mtl.model.group.Group
import com.example.rnd_transit_mtl.model.group.GroupJoinCode
import com.example.rnd_transit_mtl.model.group.GroupMember
import com.example.rnd_transit_mtl.model.ids.GroupId
import com.example.rnd_transit_mtl.model.ids.GroupTripId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripJourney
import com.example.rnd_transit_mtl.model.trip.TripJourneyKind
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TogetherViewModelTest {
    private val host = UserId("host")
    private val member = UserId("member")
    private val shared = GroupTripId("shared-route")
    private val code = GroupJoinCode.parse("K7MQ2X")
    private val group = Group(
        id = GroupId("group-1"), joinCode = code, hostId = host,
        members = listOf(GroupMember(host, "Ana", 1L)),
    )
    private val trip = Trip(
        id = TripId("trip-1"), status = TripStatus.Generated, title = "Old Port walk",
        description = "Test fixture", imageUrl = "", origin = testOrigin, destination = testDestination,
        legs = testLegs(TransportId("walk")), plannedMinutes = 30,
        selectedTransports = listOf(TripTransportSnapshot(TransportId("walk"), "Walk", false)),
        selectedRoutes = emptyList(), attractionIntensity = 50f, createdAt = 1_000L,
    )

    private class FakeGroupRepository(var existing: Group? = null) : GroupRepository {
        val live = MutableSharedFlow<GroupSync>(replay = 8)
        var created = 0
        var left = 0
        var joinedWith: GroupJoinCode? = null
        var joinFailure: GroupFailure? = null
        var createGate: CompletableDeferred<Unit>? = null
        lateinit var groupToReturn: Group
        var memberTrip: Trip? = null

        override suspend fun currentGroup(): Group? = existing
        override suspend fun createGroup(): Group {
            created++
            createGate?.await()
            return groupToReturn
        }
        override suspend fun joinGroup(code: GroupJoinCode): Group {
            joinFailure?.let { throw it }
            joinedWith = code
            return groupToReturn
        }
        override suspend fun leaveGroup(id: GroupId) { left++ }
        override suspend fun shareTrip(id: GroupId, trip: Trip): GroupTripId = GroupTripId("shared-route")
        override suspend fun startGroupTrip(id: GroupTripId): Trip = checkNotNull(memberTrip)
        override fun observeGroup(id: GroupId): Flow<GroupSync> = live
    }

    private fun withViewModel(
        repository: FakeGroupRepository,
        store: TripsStore = TripsStore(nowEpochMillis = { 2_000L }),
        test: suspend TestScope.(TogetherViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = TogetherViewModel(repository, store)
            advanceUntilIdle()
            test(viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun anExistingGroupIsRestoredOnOpen() = withViewModel(FakeGroupRepository(existing = group)) { viewModel ->
        assertFalse(viewModel.state.value.loading)
        assertEquals(group, viewModel.state.value.group)
    }

    @Test
    fun creatingShowsTheGroupWithItsCode() {
        val repository = FakeGroupRepository().apply { groupToReturn = group }
        withViewModel(repository) { viewModel ->
            viewModel.create()
            advanceUntilIdle()

            assertEquals("K7MQ2X", viewModel.state.value.group?.joinCode?.value)
            assertFalse(viewModel.state.value.busy)
        }
    }

    @Test
    fun aDoubleTapCreatesOnlyOneGroup() {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeGroupRepository().apply { groupToReturn = group; createGate = gate }
        withViewModel(repository) { viewModel ->
            viewModel.create()
            advanceUntilIdle()
            viewModel.create()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, repository.created)
        }
    }

    @Test
    fun theCodeIsNormalizedAndJoinWaitsForAValidCode() {
        val repository = FakeGroupRepository().apply { groupToReturn = group }
        withViewModel(repository) { viewModel ->
            viewModel.toggleJoinForm()
            viewModel.onCodeChange("k7 mq")
            viewModel.join()
            advanceUntilIdle()
            assertNull(repository.joinedWith)
            assertFalse(viewModel.state.value.codeValid)

            viewModel.onCodeChange("k7 mq2x9")
            assertEquals("K7MQ2X", viewModel.state.value.codeInput)
            viewModel.join()
            advanceUntilIdle()

            assertEquals(code, repository.joinedWith)
            assertEquals(group, viewModel.state.value.group)
            assertFalse(viewModel.state.value.joinFormOpen)
        }
    }

    @Test
    fun anExpiredCodeShowsTheServersMessage() {
        val repository = FakeGroupRepository().apply { joinFailure = GroupFailure.InvalidCode() }
        withViewModel(repository) { viewModel ->
            viewModel.onCodeChange("K7MQ2X")
            viewModel.join()
            advanceUntilIdle()

            assertEquals(GroupFailure.InvalidCode().message, viewModel.state.value.message)
            assertNull(viewModel.state.value.group)
        }
    }

    @Test
    fun liveUpdatesReplaceTheMembersAndShowReconnecting() {
        val repository = FakeGroupRepository(existing = group)
        val withBen = group.copy(members = group.members + GroupMember(member, "Ben", 2L))
        withViewModel(repository) { viewModel ->
            repository.live.emit(GroupSync.Reconnecting)
            advanceUntilIdle()
            assertEquals(GroupConnection.Reconnecting, viewModel.state.value.connection)

            repository.live.emit(GroupSync.Live(withBen))
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.group?.partySize)
            assertEquals(GroupConnection.Live, viewModel.state.value.connection)
        }
    }

    @Test
    fun aClosedGroupIsClearedWhileTheActiveTripContinues() {
        val repository = FakeGroupRepository(existing = group.copy(hostId = host))
        val store = TripsStore(nowEpochMillis = { 2_000L }).apply { start(trip) }
        withViewModel(repository, store) { viewModel ->
            repository.live.emit(GroupSync.Closed)
            advanceUntilIdle()

            assertNull(viewModel.state.value.group)
            assertEquals(TogetherViewModel.GROUP_ENDED_MESSAGE, viewModel.state.value.message)
            assertEquals(trip.id, store.snapshot().activeTrip?.trip?.id)
        }
    }

    @Test
    fun theHostSharesTheActiveTripAndItIsLinked() {
        val repository = FakeGroupRepository(existing = group)
        val store = TripsStore(nowEpochMillis = { 2_000L }).apply { start(trip) }
        withViewModel(repository, store) { viewModel ->
            viewModel.shareActiveTrip(assertNotNull(store.snapshot().activeTrip).trip)
            advanceUntilIdle()

            assertEquals(shared, store.snapshot().activeTrip?.trip?.groupTripId)
            assertEquals(shared, viewModel.state.value.group?.currentTripId)
        }
    }

    @Test
    fun aMemberStartsTheirOwnCopyAndTheScreenOpensIt() {
        val copy = trip.copy(id = TripId("my-copy"), ownerId = member, groupTripId = shared)
        val repository = FakeGroupRepository(existing = group.copy(currentTripId = shared)).apply { memberTrip = copy }
        val store = TripsStore(nowEpochMillis = { 2_000L })
        withViewModel(repository, store) { viewModel ->
            viewModel.startGroupTrip()
            advanceUntilIdle()

            assertEquals(shared, store.snapshot().activeTrip?.trip?.groupTripId)
            assertEquals(copy.id, viewModel.state.value.openTrip?.id)
            viewModel.onTripOpened()
            assertNull(viewModel.state.value.openTrip)
        }
    }

    @Test
    fun theHostLeavingIsNotReportedAsTheGroupEnding() {
        val repository = FakeGroupRepository(existing = group)
        withViewModel(repository) { viewModel ->
            viewModel.leave()
            repository.live.emit(GroupSync.Closed)
            advanceUntilIdle()

            assertEquals(1, repository.left)
            assertNull(viewModel.state.value.group)
            assertNull(viewModel.state.value.message)
        }
    }

    @Test
    fun theOfferedTripActionFollowsRoleAndActiveTrip() {
        val inGroup = TogetherUiState(group = group)
        val sharedGroup = TogetherUiState(group = group.copy(currentTripId = shared))
        val onGroupTrip = trip.copy(groupTripId = shared)
        val returnTrip = trip.copy(journey = TripJourney(TripJourneyKind.Return, TripId("outbound")))

        assertEquals(TogetherTripAction.None, togetherTripAction(TogetherUiState(), host, trip))
        assertEquals(TogetherTripAction.None, togetherTripAction(inGroup, host, null))
        assertEquals(TogetherTripAction.ShareActiveTrip, togetherTripAction(inGroup, host, trip))
        assertEquals(TogetherTripAction.None, togetherTripAction(inGroup, host, returnTrip))
        assertEquals(TogetherTripAction.OnGroupTrip, togetherTripAction(sharedGroup, host, onGroupTrip))
        assertEquals(TogetherTripAction.WaitForHost, togetherTripAction(inGroup, member, null))
        assertEquals(TogetherTripAction.StartGroupTrip, togetherTripAction(sharedGroup, member, null))
        assertEquals(TogetherTripAction.FinishCurrentTripFirst, togetherTripAction(sharedGroup, member, trip))
        assertEquals(TogetherTripAction.OnGroupTrip, togetherTripAction(sharedGroup, member, onGroupTrip))
        assertTrue(TogetherUiState(codeInput = "K7MQ2X").codeValid)
    }
}
