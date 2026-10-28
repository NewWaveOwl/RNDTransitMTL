package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.View.main.plannerSheetGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies Figma resting positions and safe positioning in smaller windows. */
class PlannerSheetGeometryTest {
    @Test
    fun referenceFrameMatchesAllThreeFigmaSheetAndGoPositions() {
        val geometry = plannerSheetGeometry(800f, 1f)
        assertEquals(400f, geometry.main, 0.01f)
        assertEquals(256f, geometry.routes, 0.01f)
        assertEquals(100f, geometry.preferences, 0.01f)
        assertEquals(300f, geometry.main - 76f - geometry.goGap(geometry.main), 0.01f)
        assertEquals(173f, geometry.routes - 76f - geometry.goGap(geometry.routes), 0.01f)
        assertEquals(12f, geometry.preferences - 76f - geometry.goGap(geometry.preferences), 0.01f)
    }

    @Test
    fun shortAndWideWindowsKeepOrderedDistinctStopsInsideViewport() {
        listOf(240f to 0.7f, 420f to 1f, 800f to 1.5f).forEach { (height, scale) ->
            val geometry = plannerSheetGeometry(height, scale)
            assertTrue(geometry.preferences >= 0f)
            assertTrue(geometry.preferences < geometry.routes)
            assertTrue(geometry.routes < geometry.main)
            assertTrue(geometry.main < height)
        }
    }
}
