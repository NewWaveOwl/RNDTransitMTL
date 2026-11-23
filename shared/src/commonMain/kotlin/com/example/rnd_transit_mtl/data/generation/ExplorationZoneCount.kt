package com.example.rnd_transit_mtl.data.generation

/** Each selected interest box requests one random waypoint; no selections still requests one. */
internal fun explorationZoneCount(selectedInterests: Int): Int = selectedInterests.coerceIn(1, 6)
