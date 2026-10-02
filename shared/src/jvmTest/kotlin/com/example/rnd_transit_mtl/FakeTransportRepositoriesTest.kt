package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.data.FakeTransportRouteRepository
import com.example.rnd_transit_mtl.data.FakeTransportTypeRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies that the migrated repositories load the packaged shared JSON resources. */
class FakeTransportRepositoriesTest {
    @Test
    fun repositoriesLoadSharedResources() = runBlocking {
        val types = FakeTransportTypeRepository().getTransportTypes()
        val routes = FakeTransportRouteRepository().getTransportRoutes()

        assertEquals(listOf("train", "rem", "walk", "bus", "metro", "bike"), types.map { it.id })
        assertEquals(18, routes.size)
        assertEquals(routes.size, routes.map { it.id }.distinct().size)
        val routeBasedIds = types.filter { it.usesRoutes }.map { it.id }.toSet()
        assertTrue(routes.all { it.transportTypeId in routeBasedIds })
        assertEquals(listOf("55", "80", "165", "401", "747"),
            routes.filter { it.transportTypeId == "bus" }.map { it.label })
    }
}
