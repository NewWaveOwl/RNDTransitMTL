package com.example.rnd_transit_mtl

import web.navigator.navigator

/**
 * Browser platform information inferred from the user-agent string.
 */
class JsPlatform: Platform {
    private val userAgent = navigator.userAgent
    private val browserList = listOf("Chrome", "Firefox", "Safari", "Edge")

    override val name: String = userAgent.findAnyOf(browserList, ignoreCase = true)
            ?.let { (startIndex) -> userAgent.substring(startIndex).substringBefore(" ") }
            ?: "Unknown"
}

/**
 * Creates the platform descriptor for this target.
 *
 * @return A new JsPlatform instance.
 */
actual fun getPlatform(): Platform = JsPlatform()