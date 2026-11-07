package com.example.rnd_transit_mtl.data.generation

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val origin = Place("Notre-Dame Basilica", GeoPoint(45.50455, -73.55610))
private val stop = Place("Place Jacques-Cartier", GeoPoint(45.50746, -73.55334))
private val destination = Place("Bonsecours Market", GeoPoint(45.50905, -73.55118))
private val walk = TransportId("walk")

@OptIn(ExperimentalUuidApi::class)
internal fun oldMontrealSampleTrip(): Trip = Trip(
    id = TripId(Uuid.random().toString()),
    status = TripStatus.Generated,
    title = "Old Montréal Walk (sample)",
    description = "A fixed walking demo from Notre-Dame Basilica via Place Jacques-Cartier to Bonsecours Market.",
    imageUrl = "",
    origin = origin,
    destination = destination,
    legs = listOf(
        RouteLeg.straightLine(walk, origin.point, stop.point, 300),
        RouteLeg.straightLine(walk, stop.point, destination.point, 180)
    ),
    plannedMinutes = 10,
    selectedTransports = listOf(TripTransportSnapshot(walk, "Walk", false)),
    selectedRoutes = emptyList(),
    attractionIntensity = 0f,
    createdAt = Clock.System.now().toEpochMilliseconds()
)
