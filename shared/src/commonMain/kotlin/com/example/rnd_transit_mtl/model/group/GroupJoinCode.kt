package com.example.rnd_transit_mtl.model.group

import kotlin.jvm.JvmInline

/**
 * The short code people type to join a group. It is not the group's ID.
 *
 * Codes are six characters from [ALPHABET], which leaves out look-alikes (O/0, I/1).
 * The server generates them with the same alphabet; see
 * `supabase/migrations/20261127200000_create_groups.sql`.
 *
 * @property value Normalized code: six upper-case characters from [ALPHABET].
 */
@JvmInline
value class GroupJoinCode private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        /** Characters a code may contain. Must match the server's `create_group` alphabet. */
        const val ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** Number of characters in every code. */
        const val LENGTH: Int = 6

        /**
         * Reads a code typed by a person, ignoring case and surrounding spaces.
         *
         * @param text Raw input, such as " k7mq2x ".
         * @return The normalized code, or null when the text cannot be a valid code.
         */
        fun parseOrNull(text: String): GroupJoinCode? {
            val normalized = text.trim().uppercase()
            val valid = normalized.length == LENGTH && normalized.all { it in ALPHABET }
            return if (valid) GroupJoinCode(normalized) else null
        }

        /**
         * Reads a code that must already be valid, such as one returned by the server.
         *
         * @param text Code text.
         * @throws IllegalArgumentException When [text] is not a valid code.
         */
        fun parse(text: String): GroupJoinCode =
            requireNotNull(parseOrNull(text)) { "Invalid group join code." }
    }
}
