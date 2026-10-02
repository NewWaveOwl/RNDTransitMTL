package com.example.rnd_transit_mtl

import android.os.Build

/**
 * Android platform information using the device API level.
 */
class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

/**
 * Creates the platform descriptor for this target.
 *
 * @return A new AndroidPlatform instance.
 */
actual fun getPlatform(): Platform = AndroidPlatform()