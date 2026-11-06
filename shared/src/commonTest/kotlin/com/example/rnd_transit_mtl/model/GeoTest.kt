package com.example.rnd_transit_mtl.model

import com.example.rnd_transit_mtl.data.assets.SampleMapBounds
import com.example.rnd_transit_mtl.data.assets.SampleMapProjection
import com.example.rnd_transit_mtl.model.geo.GeoBounds
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.geo.MapPosition
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.formatGeoPoint
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoTest {
    private val imageWidthPx = 1510.0
    private val imageHeightPx = 746.0

    /** Approximate ground size of one image pixel, from the calibration. */
    private val metersPerPixel = 5.5

    @Test
    fun invalidCoordinatesAreRejected() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(90.1, 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(-90.1, 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(0.0, 180.1) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(0.0, -180.1) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(Double.NaN, 0.0) }
        GeoPoint(90.0, -180.0)
    }

    @Test
    fun invalidBoundsAreRejected() {
        assertFailsWith<IllegalArgumentException> { GeoBounds(north = 1.0, south = 2.0, west = 0.0, east = 1.0) }
        assertFailsWith<IllegalArgumentException> { GeoBounds(north = 2.0, south = 1.0, west = 1.0, east = 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoBounds(north = 89.0, south = 1.0, west = 0.0, east = 1.0) }
    }

    @Test
    fun calibrationLandmarksLandWhereTheImageShowsThem() {
        // Pixel positions read from the PNG: pin tips, and the middle of the lake.
        assertNearPixel(GeoPoint(45.51510, -73.56113), expectedX = 1158.0, expectedY = 379.0) // Berri-UQAM
        assertNearPixel(GeoPoint(45.502880, -73.624792), expectedX = 251.0, expectedY = 616.0) // CHU Sainte-Justine
        assertNearPixel(GeoPoint(45.498688, -73.597562), expectedX = 636.0, expectedY = 705.0) // Lac aux Castors
    }

    @Test
    fun cornersMapToImageCorners() {
        val topLeft = SampleMapProjection.toMapPosition(GeoPoint(SampleMapBounds.north, SampleMapBounds.west))
        val bottomRight = SampleMapProjection.toMapPosition(GeoPoint(SampleMapBounds.south, SampleMapBounds.east))

        assertClose(0.0, topLeft.x); assertClose(0.0, topLeft.y)
        assertClose(1.0, bottomRight.x); assertClose(1.0, bottomRight.y)
    }

    @Test
    fun convertingBackReturnsTheSamePoint() {
        val original = GeoPoint(45.5151, -73.56113)
        val roundTrip = SampleMapProjection.toGeoPoint(SampleMapProjection.toMapPosition(original))

        assertClose(original.latitude, roundTrip.latitude)
        assertClose(original.longitude, roundTrip.longitude)
    }

    @Test
    fun pointsOutsideTheImageFallOutsideZeroToOne() {
        val lachineLighthouse = GeoPoint(45.4316, -73.6745)

        assertFalse(lachineLighthouse in SampleMapBounds)
        assertTrue(GeoPoint(45.51510, -73.56113) in SampleMapBounds)
        val position = SampleMapProjection.toMapPosition(lachineLighthouse)
        assertTrue(position.x < 0.0 && position.y > 1.0)
    }

    @Test
    fun verticalPositionFollowsMercatorNotLatitude() {
        // Mercator stretches the north more, so the top half of the image spans
        // fewer degrees and its middle row lies north of the latitude midpoint.
        val middle = SampleMapProjection.toGeoPoint(MapPosition(0.5, 0.5))
        val latitudeMidpoint = (SampleMapBounds.north + SampleMapBounds.south) / 2

        assertTrue(middle.latitude - latitudeMidpoint > 1e-6)
    }

    @Test
    fun straightLineDistanceMatchesKnownValues() {
        val berriUqam = GeoPoint(45.51510, -73.56113)
        val sainteJustine = GeoPoint(45.502880, -73.624792)

        assertEquals(0.0, berriUqam.distanceMetersTo(berriUqam))
        // One degree of latitude is about 111.2 km everywhere.
        assertEquals(111_195.0, GeoPoint(45.0, -73.0).distanceMetersTo(GeoPoint(46.0, -73.0)), absoluteTolerance = 5.0)
        // About 5.2 km between Berri-UQAM and Sainte-Justine.
        assertEquals(5_160.0, berriUqam.distanceMetersTo(sainteJustine), absoluteTolerance = 50.0)
        assertEquals(berriUqam.distanceMetersTo(sainteJustine), sainteJustine.distanceMetersTo(berriUqam))
    }

    @Test
    fun coordinatesAreShownWithFiveDecimals() {
        assertEquals("45.51510, -73.56113", formatGeoPoint(GeoPoint(45.5151, -73.56113)))
        assertEquals("0.00000, -0.50000", formatGeoPoint(GeoPoint(-0.000001, -0.5)))
    }

    private fun assertNearPixel(point: GeoPoint, expectedX: Double, expectedY: Double) {
        val position = SampleMapProjection.toMapPosition(point)
        val errorPx = hypot(position.x * imageWidthPx - expectedX, position.y * imageHeightPx - expectedY)
        assertTrue(errorPx * metersPerPixel < 40.0, "$point is ${errorPx * metersPerPixel} m from its image position.")
    }

    private fun assertClose(expected: Double, actual: Double) =
        assertEquals(expected, actual, absoluteTolerance = 1e-9)
}
