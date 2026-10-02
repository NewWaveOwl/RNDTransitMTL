package com.example.rnd_transit_mtl.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import rnd_transit_mtl.shared.generated.resources.Res

/**
 * Reads sample JSON arrays bundled in shared Compose resources.
 */
internal object JsonAssetReader {
    /**
     * Loads a bundled Compose resource and parses its root JSON array.
     *
     * @param assetPath Resource path relative to composeResources, including the files directory.
     * @return Parsed JSON array from the requested resource.
     */
    suspend fun readArray(assetPath: String): JsonArray = Json
        .parseToJsonElement(Res.readBytes(assetPath).decodeToString())
        .jsonArray
}
