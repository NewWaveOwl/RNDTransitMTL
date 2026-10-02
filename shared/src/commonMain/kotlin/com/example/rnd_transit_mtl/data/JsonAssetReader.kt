package com.example.rnd_transit_mtl.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import rnd_transit_mtl.shared.generated.resources.Res

/** Reads JSON arrays from the shared resources bundled for every platform. */
internal object JsonAssetReader {
    /** Loads a path relative to composeResources and parses its root JSON array. */
    suspend fun readArray(assetPath: String): JsonArray = Json
        .parseToJsonElement(Res.readBytes(assetPath).decodeToString())
        .jsonArray
}
