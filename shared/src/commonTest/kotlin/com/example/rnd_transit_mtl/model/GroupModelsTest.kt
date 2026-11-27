package com.example.rnd_transit_mtl.model

import com.example.rnd_transit_mtl.model.group.Group
import com.example.rnd_transit_mtl.model.group.GroupJoinCode
import com.example.rnd_transit_mtl.model.group.GroupMember
import com.example.rnd_transit_mtl.model.ids.GroupId
import com.example.rnd_transit_mtl.model.ids.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupModelsTest {
    private val host = UserId("host")
    private val guest = UserId("guest")

    @Test
    fun joinCodeIgnoresCaseAndSurroundingSpaces() {
        assertEquals("K7MQ2X", GroupJoinCode.parseOrNull("  k7mq2x ")?.value)
    }

    @Test
    fun joinCodeRejectsWrongLengthAndLookAlikeCharacters() {
        for (text in listOf("", "K7MQ2", "K7MQ2XY", "K7MQ0X", "K7MQ1X", "K7MOQX", "K7MIQX", "K7 Q2X")) {
            assertNull(GroupJoinCode.parseOrNull(text), "\"$text\" must be rejected")
        }
        assertFailsWith<IllegalArgumentException> { GroupJoinCode.parse("000000") }
    }

    @Test
    fun joinCodeAlphabetMatchesTheServerCheck() {
        // supabase/migrations/20261127200000_create_groups.sql: join_code ~ '^[A-HJ-NP-Z2-9]{6}$'
        val serverAlphabet = (('A'..'H') + ('J'..'N') + ('P'..'Z') + ('2'..'9')).joinToString("")
        assertEquals(serverAlphabet, GroupJoinCode.ALPHABET)
        assertEquals(32, GroupJoinCode.ALPHABET.length)
    }

    @Test
    fun groupKnowsItsHostAndPartySize() {
        val group = Group(
            id = GroupId("group"),
            joinCode = GroupJoinCode.parse("K7MQ2X"),
            hostId = host,
            members = listOf(GroupMember(host, "Ana", 1L), GroupMember(guest, "Ben", 2L)),
        )

        assertEquals(2, group.partySize)
        assertEquals("Ana", group.host?.displayName)
        assertTrue(group.isHost(host))
        assertFalse(group.isHost(guest))
    }

    @Test
    fun memberNameMustNotBeBlank() {
        assertFailsWith<IllegalArgumentException> { GroupMember(guest, " ", 0L) }
    }
}
