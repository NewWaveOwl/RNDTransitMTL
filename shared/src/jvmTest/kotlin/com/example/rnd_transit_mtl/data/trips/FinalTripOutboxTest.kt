package com.example.rnd_transit_mtl.data.trips

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.example.rnd_transit_mtl.View.history.previews.unreviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.View.trip_review.TripReviewScreen
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.review.TripReview
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.state.review.ReviewDraft
import com.example.rnd_transit_mtl.state.review.ReviewStep
import com.example.rnd_transit_mtl.state.trip.TRIP_SIMULATION_DURATION_MILLIS
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.state.trip.TripsStore
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import java.nio.file.Files
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every restart loads fresh Settings and store objects from disk, without Compose saved state. */
class FinalTripOutboxTest {
    private val owner = "recovery-owner"
    private val trip = unreviewedHistoryPreviewTrip.copy(ownerId = UserId(owner))
    private val draft = ReviewDraft(
        overall = 5, quality = 4, interesting = 3, `fun` = 2,
        comment = "Keep my feedback. ".repeat(600),
        imageUrl = "https://example.com/review.jpg", step = ReviewStep.FUN,
    )

    @Test
    fun lostTripAndReviewResponsesCanBeRetriedAfterDiskRestartsWithoutDuplicates() = runTest {
        RecoveryDisk().use { disk ->
            val repository = RetryRepository().apply { loseTripResponse = true }
            val original = disk.restore(owner)
            val generated = trip.copy(status = TripStatus.Generated, completedAt = null)
            assertEquals(TripActionResult.Applied, original.start(generated))
            assertEquals(TripActionResult.Applied,
                original.updateElapsed(trip.id, TRIP_SIMULATION_DURATION_MILLIS))
            original.beginReview(trip.id)
            original.updateReviewDraft(trip.id, draft)
            val completed = assertNotNull(original.findCompleted(trip.id))
            assertFailsWith<IllegalStateException> { CloudTrips(repository, original).flushFinalTrips() }

            val restored = disk.restore(owner)
            assertEquals(completed, restored.pendingFinalTrips().single())
            assertEquals(draft, restored.reviewDrafts[trip.id])
            val cloud = CloudTrips(repository, restored)
            cloud.flushFinalTrip(trip.id)
            cloud.flushFinalTrips()
            assertEquals(1, repository.records.size)
            assertEquals(completed, repository.records[trip.id])

            // Acknowledging the trip must retain its draft and parent snapshot.
            val reviewStore = disk.restore(owner)
            assertTrue(reviewStore.pendingFinalTrips().isEmpty())
            assertEquals(completed, reviewStore.findCompleted(trip.id))
            assertEquals(draft, reviewStore.reviewDrafts[trip.id])
            repository.loseReviewResponse = true
            assertFailsWith<IllegalStateException> { saveReview(CloudTrips(repository, reviewStore), reviewStore) }

            val retryStore = disk.restore(owner)
            assertEquals(draft, retryStore.reviewDrafts[trip.id])
            saveReview(CloudTrips(repository, retryStore), retryStore)
            assertEquals(1, repository.records.size)
            assertEquals(1, repository.reviews.size)
            assertEquals(listOf(trip.id, trip.id), repository.tripAttempts)
            assertEquals(listOf(trip.id, trip.id), repository.reviewAttempts)
            assertEquals(draft.comment.trim(), repository.reviews[trip.id]?.comment)
            assertEquals(completed.legs, repository.records[trip.id]?.legs)
            assertEquals(completed.createdAt, repository.records[trip.id]?.createdAt)
            assertEquals(completed.completedAt, repository.records[trip.id]?.completedAt)
            assertTrue(disk.settings().keys.isEmpty())
            assertTrue(disk.restore(owner).reviewDrafts.isEmpty())
        }
    }

    @Test
    fun serverUnavailableKeepsAnAcknowledgedTripsReviewInputForRestartAndRetry() = runTest {
        RecoveryDisk().use { disk ->
            val repository = RetryRepository().apply {
                records[trip.id] = trip
                failReview = true
            }
            val store = disk.restore(owner).apply {
                acceptStoredTrip(trip)
                beginReview(trip.id)
                updateReviewDraft(trip.id, draft)
            }
            assertFailsWith<IllegalStateException> { saveReview(CloudTrips(repository, store), store) }

            val restored = disk.restore(owner)
            assertEquals(trip, restored.findCompleted(trip.id))
            assertEquals(draft, restored.reviewDrafts[trip.id])
            assertTrue(restored.pendingFinalTrips().isEmpty())
            repository.failReview = false
            saveReview(CloudTrips(repository, restored), restored)
            assertTrue(repository.tripAttempts.isEmpty())
            assertEquals(1, repository.reviews.size)
            assertTrue(disk.settings().keys.isEmpty())
        }
    }

    @Test
    fun skippedDraftDoesNotDiscardAnUnsyncedCompletedOrCancelledTrip() = runTest {
        RecoveryDisk().use { disk ->
            val store = disk.restore(owner)
            store.restorePendingFinalTrips(listOf(trip))
            store.beginReview(trip.id)
            store.updateReviewDraft(trip.id, draft)
            val cancelledId = TripId("cancelled-recovery-trip")
            store.acknowledgeReviewNavigation(trip.id)
            store.start(trip.copy(id = cancelledId, status = TripStatus.Generated, completedAt = null))
            store.cancel(cancelledId, keepInHistory = true)
            val cancelled = assertNotNull(store.findInHistory(cancelledId))
            store.skipReview(trip.id)

            val restored = disk.restore(owner)
            assertTrue(restored.reviewDrafts.isEmpty())
            assertEquals(setOf(trip.id, cancelledId), restored.state.value.pendingFinalTripIds)
            assertEquals(cancelled, restored.findInHistory(cancelledId))
            val repository = RetryRepository()
            CloudTrips(repository, restored).flushFinalTrips()
            assertEquals(TripStatus.Cancelled, repository.records[cancelledId]?.status)
            assertEquals(cancelled.cancelledAt, repository.records[cancelledId]?.cancelledAt)
            assertTrue(disk.settings().keys.isEmpty())
        }
    }

    @Test
    fun skipAndDeletionRemoveDurableDraftsWithoutResurrectingTrips() {
        for (delete in listOf(false, true)) {
            RecoveryDisk().use { disk ->
                val store = disk.restore(owner).apply {
                    acceptStoredTrip(trip)
                    beginReview(trip.id)
                    updateReviewDraft(trip.id, draft)
                }
                if (delete) store.removeFromHistory(trip.id) else store.skipReview(trip.id)
                assertTrue(disk.settings().keys.isEmpty())
                val restored = disk.restore(owner)
                assertTrue(restored.reviewDrafts.isEmpty())
                assertNull(restored.findInHistory(trip.id))
            }
        }
    }

    @Test
    fun pendingTripsAndReviewDraftsRemainIsolatedAcrossAccounts() {
        RecoveryDisk().use { disk ->
            val first = disk.restore(owner).apply {
                restorePendingFinalTrips(listOf(trip))
                beginReview(trip.id)
                updateReviewDraft(trip.id, draft)
            }
            val otherOwner = "other-owner"
            val other = disk.restore(otherOwner)
            assertNull(other.findInHistory(trip.id))
            assertTrue(other.reviewDrafts.isEmpty())
            val otherTrip = trip.copy(id = TripId("other-trip"), ownerId = UserId(otherOwner))
            other.acceptStoredTrip(otherTrip)
            other.beginReview(otherTrip.id)
            other.updateReviewDraft(otherTrip.id, ReviewDraft(overall = 2))

            val restored = disk.restore(owner)
            assertEquals(first.pendingFinalTrips(), restored.pendingFinalTrips())
            assertEquals(draft, restored.reviewDrafts[trip.id])
            assertNull(restored.findInHistory(otherTrip.id))
            restored.skipReview(trip.id)
            restored.removeFromHistory(trip.id)
            assertEquals(2, disk.restore(otherOwner).reviewDrafts[otherTrip.id]?.overall)
        }
    }

    @Test
    fun existingTripOnlyOutboxesMigrateWithoutLosingCancelledHistory() {
        for (legacy in listOf(false, true)) {
            RecoveryDisk().use { disk ->
                val cancelled = trip.copy(status = TripStatus.Cancelled,
                    cancelledAt = trip.completedAt, completedAt = null)
                val key = if (legacy) "transit_pending_final_trips" else "transit_pending_final_trips_$owner"
                val encoded = buildJsonObject {
                    put("owner", owner)
                    put("trips", Json.encodeToJsonElement(listOf(cancelled)))
                }.toString()
                disk.settings().apply {
                    putString("${key}_0_0", encoded)
                    putInt("${key}_0_count", 1)
                    putInt(key, 0)
                }
                val restored = disk.restore(owner)
                assertEquals(cancelled, restored.pendingFinalTrips().single())
                assertEquals(cancelled, disk.restore(owner).findInHistory(cancelled.id))
                if (legacy) assertTrue(!disk.settings().hasKey(key))
            }
        }
    }

    @Test
    fun newerComposeSavedStateDraftWinsOverDiskRecovery() {
        RecoveryDisk().use { disk ->
            val store = disk.restore(owner).apply {
                acceptStoredTrip(trip)
                beginReview(trip.id)
                updateReviewDraft(trip.id, draft)
            }
            // Simulate a newer platform snapshot alongside the older on-disk draft.
            val savedState = assertNotNull(TripsStore.fromSavedStateJson(store.toSavedStateJson()))
            val newer = draft.copy(comment = "Newer saved-state input", imageUrl = "https://incomplete")
            savedState.updateReviewDraft(trip.id, newer)
            FinalTripOutbox(disk.settings()).bind(owner, savedState)
            assertEquals(newer, savedState.reviewDrafts[trip.id])
            assertEquals(newer, disk.restore(owner).reviewDrafts[trip.id])
        }
    }

    @Test
    fun acknowledgedReviewRecoverySupersedesAStaleActivePlatformSnapshot() {
        RecoveryDisk().use { disk ->
            val activeSnapshot = TripsStore().apply {
                start(trip.copy(status = TripStatus.Generated, completedAt = null))
            }.toSavedStateJson()
            disk.restore(owner).apply {
                acceptStoredTrip(trip)
                beginReview(trip.id)
                updateReviewDraft(trip.id, draft)
            }

            val restored = assertNotNull(TripsStore.fromSavedStateJson(activeSnapshot))
            FinalTripOutbox(disk.settings()).bind(owner, restored)
            assertNull(restored.activeTrip)
            assertEquals(trip, restored.findCompleted(trip.id))
            assertEquals(draft, restored.reviewDrafts[trip.id])
            assertTrue(restored.pendingFinalTrips().isEmpty())
            assertEquals(draft, disk.restore(owner).reviewDrafts[trip.id])
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun reviewScreenKeepsFailedInputAndSavesOnceAfterDiskRecovery() = runBlocking(Dispatchers.Main) {
        RecoveryDisk().use { disk ->
            val repository = RetryRepository().apply {
                records[trip.id] = trip
                failReview = true
            }
            val store = disk.restore(owner).apply {
                acceptStoredTrip(trip)
                beginReview(trip.id)
                updateReviewDraft(trip.id, draft)
            }
            val lifecycleOwner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                    currentState = Lifecycle.State.RESUMED
                }
            }
            var exits = 0
            fun sceneFor(currentStore: TripsStore): ImageComposeScene {
                val cloud = CloudTrips(repository, currentStore)
                return ImageComposeScene(412, 900) {
                    val dispatcher = rememberNavigationEventDispatcherOwner(parent = null)
                    CompositionLocalProvider(
                        LocalNavigationEventDispatcherOwner provides dispatcher,
                        LocalLifecycleOwner provides lifecycleOwner,
                        LocalTripsStore provides currentStore,
                        LocalCloudTrips provides cloud,
                    ) {
                        RNDTransitTheme {
                            TripReviewScreen(trip.id, TripReviewMode.EDIT, true,
                                onOpenHistory = { exits++; true }, onReturnToOrigin = { exits++; true })
                        }
                    }
                }
            }
            var time = 0L
            suspend fun render(scene: ImageComposeScene) {
                repeat(24) { yield(); time += 16_000_000; scene.render(time).close() }
            }
            fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
            fun findText(scene: ImageComposeScene, text: String): SemanticsNode =
                scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true
                }
            fun clickSave(scene: ImageComposeScene) {
                assertTrue(findText(scene, "Save review").config[SemanticsActions.OnClick].action!!.invoke())
            }

            val firstScene = sceneFor(store)
            try {
                render(firstScene)
                clickSave(firstScene)
                render(firstScene)
                findText(firstScene, "Could not save your review. Please retry.")
                assertEquals(draft, store.reviewDrafts[trip.id])
                assertEquals(0, exits)
            } finally { firstScene.close() }

            val restored = disk.restore(owner)
            assertEquals(draft, restored.reviewDrafts[trip.id])
            val recoveredScene = sceneFor(restored)
            try {
                render(recoveredScene)
                repository.failReview = false
                clickSave(recoveredScene)
                clickSave(recoveredScene)
                render(recoveredScene)
                assertEquals(1, exits)
                assertEquals(listOf(trip.id), repository.reviewAttempts)
                assertEquals(1, repository.reviews.size)
                assertNull(restored.reviewDrafts[trip.id])
                assertTrue(disk.settings().keys.isEmpty())
            } finally { recoveredScene.close() }
        }
    }

    private suspend fun saveReview(cloud: CloudTrips, store: TripsStore) {
        val review = assertNotNull(store.reviewDrafts[trip.id]).let {
            TripReview(assertNotNull(it.overall), it.quality, it.interesting, it.`fun`, it.comment.trim(), it.imageUrl)
        }
        cloud.flushFinalTrip(trip.id)
        cloud.mutate { store.saveReview(trip.id, saveReview(trip.id, review)) }
    }

    private class RecoveryDisk : AutoCloseable {
        private val file = Files.createTempFile("rnd-trip-recovery-", ".properties").toFile()

        fun settings(): Settings {
            val properties = Properties().apply { file.inputStream().use { load(it) } }
            return PropertiesSettings(properties) { latest ->
                file.outputStream().use { latest.store(it, null) }
            }
        }

        fun restore(owner: String): TripsStore = TripsStore().also {
            FinalTripOutbox(settings()).bind(owner, it)
        }

        override fun close() { file.delete() }
    }

    private class RetryRepository : TripRepository {
        val records = mutableMapOf<TripId, Trip>()
        val reviews = mutableMapOf<TripId, TripReview>()
        val tripAttempts = mutableListOf<TripId>()
        val reviewAttempts = mutableListOf<TripId>()
        var loseTripResponse = false
        var loseReviewResponse = false
        var failReview = false

        override suspend fun saveFinalTrip(trip: Trip): Trip {
            tripAttempts += trip.id
            val stored = records.getOrPut(trip.id) { trip }
            if (loseTripResponse) {
                loseTripResponse = false
                error("Response lost after trip insert")
            }
            return stored
        }

        override suspend fun saveReview(tripId: TripId, review: TripReview): TripReview {
            check(!failReview) { "Server unavailable" }
            checkNotNull(records[tripId]) { "Missing parent trip" }
            reviewAttempts += tripId
            reviews[tripId] = review
            if (loseReviewResponse) {
                loseReviewResponse = false
                error("Response lost after review upsert")
            }
            return review
        }

        override suspend fun listHistory() = records.values.toList()
        override suspend fun getTrip(id: TripId) = records[id]
        override suspend fun updateTrip(id: TripId, title: String, description: String) = error("Unexpected update")
        override suspend fun deleteTrip(id: TripId) { records.remove(id); reviews.remove(id) }
        override suspend fun deleteReview(tripId: TripId) { reviews.remove(tripId) }
    }
}
