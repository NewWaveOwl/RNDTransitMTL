package com.example.rnd_transit_mtl

/**
 * Desktop platform information using the Java runtime version.
 */
class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

/**
 * Creates the platform descriptor for this target.
 *
 * @return A new JVMPlatform instance.
 */
actual fun getPlatform(): Platform = JVMPlatform()