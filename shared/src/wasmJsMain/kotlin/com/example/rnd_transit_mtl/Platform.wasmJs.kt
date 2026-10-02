package com.example.rnd_transit_mtl

/**
 * Platform information for the Kotlin/Wasm browser target.
 */
class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

/**
 * Creates the platform descriptor for this target.
 *
 * @return A new WasmPlatform instance.
 */
actual fun getPlatform(): Platform = WasmPlatform()