package com.example.rnd_transit_mtl.model

import com.example.rnd_transit_mtl.model.ids.GroupId
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Android rejects a lazy-list key or saved value that a Bundle cannot store, but only at runtime. */
class IdsBundleTest {
    // Typed as Any so each ID is boxed, exactly as it is when used as a lazy-list key.
    private val ids: List<Any> = listOf(
        TripId("trip-1"),
        UserId("user-1"),
        GroupId("group-1"),
        TransportId("bus"),
        RouteId("bus:401"),
        ZoneId("A")
    )

    @Test
    fun everyIdIsAcceptedByTheBundle() {
        ids.forEach { id -> assertTrue(id is Serializable, "$id must be Serializable") }
    }

    @Test
    fun everyIdSurvivesBeingSavedAndRestoredInsideAMap() {
        // Compose saves list-row state in a map keyed by the list key.
        val saved = HashMap(ids.associateWith { it.toString() })
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(saved) }
        }.toByteArray()

        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }

        assertEquals<Any>(saved, restored)
    }
}
