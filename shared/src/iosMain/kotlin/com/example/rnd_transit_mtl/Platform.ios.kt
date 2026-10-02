package com.example.rnd_transit_mtl

import platform.UIKit.UIDevice

/**
 * iOS platform information using the current device system name and version.
 */
class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

/**
 * Creates the platform descriptor for this target.
 *
 * @return A new IOSPlatform instance.
 */
actual fun getPlatform(): Platform = IOSPlatform()