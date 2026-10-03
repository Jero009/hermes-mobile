package com.m57.hermescontrol.data.bots

import com.m57.hermescontrol.data.ws.WsEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Group transcripts are a local merge over per-bot sessions: the user entry is
 * deterministic (a double dispatch cannot double-persist), assistant entries
 * only exist for events provenanced to the room's profile and one of the
 * room's member sessions, and the sender is the owning bot.
 */
class GroupTranscriptTest {
    @Test
    fun `user entries are deterministic per room and text`() {
        val first = GroupTranscript.userEntry("room-1", "hello", 100L)
        val again = GroupTranscript.userEntry("room-1", "hello", 100L)
        val otherRoom = GroupTranscript.userEntry("room-2", "hello", 100L)
        val otherText = GroupTranscript.userEntry("room-1", "world", 100L)

        assertEquals(first, again)
        assertEquals("user", first.sender)
        assertTrue(first.fromUser)
        assertEquals("hello", first.content)
        assertNotEquals(first.id, otherRoom.id)
        assertNotEquals(first.id, otherText.id)
    }

    @Test
    fun `a message complete for a member session merges as that bot`() {
        val event = WsEvent.MessageComplete("group answer", "session-alpha")
        val entry =
            GroupTranscript.entryForEvent(
                event,
                membersBySessionId = mapOf("session-alpha" to "alpha"),
                timestamp = 200L,
                roomId = "room-1",
            )

        assertEquals("alpha", entry?.sender)
        assertEquals("group answer", entry?.content)
        assertEquals("session-alpha", entry?.sourceSessionId)
        assertTrue(!entry!!.fromUser)
    }

    @Test
    fun `events outside the membership never merge`() {
        val event = WsEvent.MessageComplete("stray", "unknown-session")

        assertNull(GroupTranscript.entryForEvent(event, mapOf("session-alpha" to "alpha"), 200L, "room-1"))
        assertNull(
            GroupTranscript.entryForEvent(
                event,
                emptyMap(),
                200L,
                "room-1",
            ),
        )
    }

    @Test
    fun `assistant entry ids are stable per source session and content`() {
        val one = GroupTranscript.assistantEntryId("room-1", "session-alpha", "same")
        val two = GroupTranscript.assistantEntryId("room-1", "session-alpha", "same")
        val differentText = GroupTranscript.assistantEntryId("room-1", "session-alpha", "other")
        val differentSession = GroupTranscript.assistantEntryId("room-1", "session-beta", "same")

        assertEquals(one, two)
        assertNotEquals(one, differentText)
        assertNotEquals(one, differentSession)
    }

    @Test
    fun `a merged entry for a session answering twice stays distinct`() {
        val first = GroupTranscript.assistantEntryId("room-1", "session-alpha", "Done.")
        val second = GroupTranscript.assistantEntryId("room-1", "session-alpha", "Done again.")

        assertNotEquals(first, second)
    }
}
