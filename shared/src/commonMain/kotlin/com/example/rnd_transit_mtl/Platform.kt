package com.example.rnd_transit_mtl

/**
 * Defines the platform information available to shared Kotlin code.
 *
 * Each target-specific implementation supplies its operating system or runtime name.
 */
interface Platform {
    /** Human-readable operating system, browser, or runtime description. */
    val name: String
}

/**
 * Obtains the implementation of Platform for the current compilation target.
 *
 * This expect declaration is implemented by an actual function in each platform source set.
 *
 * @return Platform information supplied by the target-specific implementation.
 */
expect fun getPlatform(): Platform