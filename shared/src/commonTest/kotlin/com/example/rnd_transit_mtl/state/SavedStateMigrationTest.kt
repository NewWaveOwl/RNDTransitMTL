package com.example.rnd_transit_mtl.state

import com.example.rnd_transit_mtl.data.assets.toSampleMapPlace
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.review.TripReview
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.state.trip.TripsState
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Snapshots written by the schema 1 build must restore with their trips.
 *
 * The JSON is written out by hand in schema 1's shape, as that build's
 * Saver produced it, so these tests break if the legacy format drifts.
 */
class SavedStateMigrationTest {
    private val start = TripPoint("Start", 0.2f, 0.3f)
    private val destination = TripPoint("Destination", 0.7f, 0.6f)

    private fun tripJson(
        id: String,
        completedAt: Long? = null,
        review: String = "null"
    ): String =
        """{"id":"$id","title":"Old trip","description":"Saved before issue 27.","imageUrl":"",""" +
            """"start":{"label":"Start","x":0.2,"y":0.3},""" +
            """"destination":{"label":"Destination","x":0.7,"y":0.6},""" +
            """"plannedMinutes":30,""" +
            """"selectedTransports":[{"id":"walk","label":"Walk","usesRoutes":false}],""" +
            """"selectedRoutes":[],"attractionIntensity":50.0,"distanceKm":3.5,""" +
            """"createdAtEpochMillis":1000,"completedAtEpochMillis":$completedAt,""" +
            """"review":$review,"selectedZoneIds":[],"selectedZonesByTransport":{}}"""

    @Test
    fun activeTripSurvivesWithItsProgress() {
        val saved = """{"schemaVersion":1,"activeTrip":{"trip":${tripJson("old-active")},""" +
            """"elapsedMillis":4000},"completedTrips":[],"pendingReviewTripId":null,"reviewDrafts":{}}"""

        val store = assertNotNull(TripsStore.fromSavedStateJson(saved))

        val active = assertNotNull(store.activeTrip)
        assertEquals(TripId("old-active"), active.id)
        assertEquals(TripStatus.Active, active.status)
        assertEquals(4_000L, store.elapsedMillis)
        assertEquals(1_000L, active.createdAt)
        assertNull(active.ownerId)
        assertEquals(start.toSampleMapPlace(), active.origin)
        assertEquals(destination.toSampleMapPlace(), active.destination)
    }

    @Test
    fun completedHistoryReviewsAndPendingReviewSurvive() {
        val review = """{"overall":4,"quality":5,"interesting":null,"fun":null,"comment":"Nice","imageUrl":""}"""
        val draft = """{"overall":2,"quality":null,"interesting":null,"fun":null,"comment":"Half done",""" +
            """"step":"OVERALL","imageUrl":""}"""
        val saved = """{"schemaVersion":1,"activeTrip":null,"completedTrips":[""" +
            tripJson("old-reviewed", completedAt = 9_000L, review = review) + "," +
            tripJson("old-pending", completedAt = 12_000L) +
            """],"pendingReviewTripId":"old-pending","reviewDrafts":{"old-reviewed":$draft}}"""

        val store = assertNotNull(TripsStore.fromSavedStateJson(saved))

        assertEquals(listOf(TripId("old-reviewed"), TripId("old-pending")), store.completedTrips.map { it.id })
        assertEquals(listOf(TripStatus.Completed, TripStatus.Completed), store.completedTrips.map { it.status })
        assertEquals(listOf(9_000L, 12_000L), store.completedTrips.map { it.completedAt })
        assertEquals(
            TripReview(overall = 4, quality = 5, comment = "Nice"),
            store.findCompleted(TripId("old-reviewed"))?.review
        )
        assertEquals(TripId("old-pending"), store.pendingReviewTripId)
        assertEquals("Half done", store.reviewDrafts.getValue(TripId("old-reviewed")).comment)
    }

    @Test
    fun mapPointsBecomeOneUnroutedLegWithTheFirstMode() {
        val saved = """{"schemaVersion":1,"activeTrip":null,"completedTrips":[""" +
            tripJson("old-trip", completedAt = 2_000L) +
            """],"pendingReviewTripId":null,"reviewDrafts":{}}"""

        val trip = assertNotNull(TripsStore.fromSavedStateJson(saved)).completedTrips.single()

        val leg = trip.legs.single()
        assertEquals(TransportId("walk"), leg.transportId)
        assertFalse(leg.isRouted)
        assertEquals(listOf(trip.origin.point, trip.destination.point), leg.geometry)
        assertEquals(30 * 60L, leg.durationSeconds)
        // The synthetic 3.5 km is replaced by the real straight-line distance.
        assertEquals(trip.origin.point.distanceMetersTo(trip.destination.point) / 1000.0, trip.distanceKm)
    }

    @Test
    fun migratedStateSavesAsTheCurrentSchema() {
        val saved = """{"schemaVersion":1,"activeTrip":{"trip":${tripJson("old-active")},""" +
            """"elapsedMillis":0},"completedTrips":[],"pendingReviewTripId":null,"reviewDrafts":{}}"""

        val resaved = assertNotNull(TripsStore.fromSavedStateJson(saved)).toSavedStateJson()
        val restored = assertNotNull(TripsStore.fromSavedStateJson(resaved))

        assertEquals(TripsState.CURRENT_SCHEMA_VERSION, restored.snapshot().schemaVersion)
        assertEquals(TripId("old-active"), restored.activeTrip?.id)
    }

    @Test
    fun emptyVersionOneSnapshotRestoresAnEmptyStore() {
        val saved = """{"schemaVersion":1,"activeTrip":null,"completedTrips":[],""" +
            """"pendingReviewTripId":null,"reviewDrafts":{}}"""

        val store = assertNotNull(TripsStore.fromSavedStateJson(saved))

        assertNull(store.activeTrip)
        assertEquals(emptyList(), store.completedTrips)
    }

    @Test
    fun brokenOrUnknownSnapshotsStillStartFresh() {
        val noTransport = tripJson("old-trip", completedAt = 2_000L)
            .replace("""[{"id":"walk","label":"Walk","usesRoutes":false}]""", "[]")
        val noTransportSnapshot = """{"schemaVersion":1,"activeTrip":null,"completedTrips":[$noTransport],""" +
            """"pendingReviewTripId":null,"reviewDrafts":{}}"""
        val futureSnapshot = """{"schemaVersion":99,"activeTrip":null,"completedTrips":[],""" +
            """"cancelledTrips":[],"pendingReviewTripId":null,"reviewDrafts":{}}"""

        assertNull(TripsStore.fromSavedStateJson(noTransportSnapshot))
        assertNull(TripsStore.fromSavedStateJson(futureSnapshot))
        assertNull(TripsStore.fromSavedStateJson("[1]"))
        assertNull(TripsStore.fromSavedStateJson("not json"))
    }
}
