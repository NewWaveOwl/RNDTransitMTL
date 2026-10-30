package com.example.rnd_transit_mtl.model.ids

/**
 * Marks a value that Android can store in a Bundle.
 *
 * Android saves rememberSaveable values and lazy-list keys in a Bundle, and a boxed
 * value class is only accepted there if it is java.io.Serializable. That type does
 * not exist in shared code, so each platform supplies its own definition.
 */
expect interface PlatformSerializable
