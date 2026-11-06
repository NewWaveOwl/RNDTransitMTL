package com.example.rnd_transit_mtl.data.assets

import com.example.rnd_transit_mtl.model.geo.GeoBounds
import com.example.rnd_transit_mtl.model.geo.MapPosition
import com.example.rnd_transit_mtl.model.geo.MapProjection
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.TripPoint

/**
 * Real-world area shown by drawable/map_sample.png (1510 × 746 px, central Montreal).
 *
 * Fitted on 2026-11-02 by least squares, assuming Web Mercator with square
 * pixels, from three landmarks: Berri-UQAM (45.51510, -73.56113), CHU
 * Sainte-Justine (45.502880, -73.624792) and the middle of Lac aux Castors
 * (45.498688, -73.597562). Each landmark lands within about 35 m (6 px) of
 * where it appears in the image. Recalibrate if the image is replaced.
 */
val SampleMapBounds = GeoBounds(
    north = 45.53351,
    south = 45.49671,
    west = -73.64249,
    east = -73.53617
)

/** Converts between real coordinates and positions on map_sample.png. */
val SampleMapProjection = MapProjection(SampleMapBounds)

/**
 * Finds the real place shown at a position picked on map_sample.png.
 *
 * @receiver Normalized position on the image, such as the planner's chosen origin.
 * @return A place with the same label at the matching coordinates.
 */
fun TripPoint.toSampleMapPlace(): Place =
    Place(label, SampleMapProjection.toGeoPoint(MapPosition(x.toDouble(), y.toDouble())))

/**
 * Finds where a place appears on map_sample.png, for the PNG map composables.
 *
 * Places outside the image are clamped to its nearest edge, because TripPoint
 * only describes positions inside the image.
 *
 * @receiver Real place to draw.
 * @return Normalized image position with the place's label.
 */
fun Place.toSampleMapPoint(): TripPoint {
    val position = SampleMapProjection.toMapPosition(point)
    return TripPoint(
        label = label,
        x = position.x.toFloat().coerceIn(0f, 1f),
        y = position.y.toFloat().coerceIn(0f, 1f)
    )
}
