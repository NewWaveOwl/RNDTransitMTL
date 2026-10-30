package com.example.rnd_transit_mtl.model.ids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/*
 * Each identifier has its own type, so passing one kind where another belongs
 * fails at compile time. All of them serialize as a plain JSON string, which
 * keeps saved state and navigation keys in the same format as a String ID.
 * toString() returns the raw value so messages and string keys read the same.
 * PlatformSerializable lets Android store them as lazy-list keys and saved state.
 */

/**
 * Identifies one generated trip across screens, history, and group sessions.
 *
 * @property value Nonblank identifier text.
 */
@Serializable
@JvmInline
value class TripId(val value: String) : PlatformSerializable {
    init {
        require(value.isNotBlank()) { "Trip ID must not be blank." }
    }

    override fun toString(): String = value
}

/**
 * Identifies a user, including as the owner of a record or a group member.
 *
 * @property value Nonblank identifier text.
 */
@Serializable
@JvmInline
value class UserId(val value: String) : PlatformSerializable {
    init {
        require(value.isNotBlank()) { "User ID must not be blank." }
    }

    override fun toString(): String = value
}

/**
 * Identifies one group session. This is not the short code people type to join.
 *
 * @property value Nonblank identifier text.
 */
@Serializable
@JvmInline
value class GroupId(val value: String) : PlatformSerializable {
    init {
        require(value.isNotBlank()) { "Group ID must not be blank." }
    }

    override fun toString(): String = value
}

/**
 * Identifies a transport mode from the transport catalogue.
 *
 * @property value Nonblank catalogue identifier, such as walk or metro.
 */
@Serializable
@JvmInline
value class TransportId(val value: String) : PlatformSerializable {
    init {
        require(value.isNotBlank()) { "Transport ID must not be blank." }
    }

    override fun toString(): String = value
}

/**
 * Identifies a route belonging to a route-based transport mode.
 *
 * @property value Nonblank catalogue identifier.
 */
@Serializable
@JvmInline
value class RouteId(val value: String) : PlatformSerializable {
    init {
        require(value.isNotBlank()) { "Route ID must not be blank." }
    }

    override fun toString(): String = value
}

/**
 * Identifies a transit zone.
 *
 * @property value Nonblank zone identifier, such as A.
 */
@Serializable
@JvmInline
value class ZoneId(val value: String) : PlatformSerializable {
    init {
        require(value.isNotBlank()) { "Zone ID must not be blank." }
    }

    override fun toString(): String = value
}
