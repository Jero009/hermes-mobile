package com.m57.hermescontrol.data.bots

import com.m57.hermescontrol.data.model.SessionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the invisible managed-title marker used to own gateway sessions
 * on behalf of roster bots. The marker must be untypable so a user-authored
 * session can never collide with a managed one, and matching must be exact —
 * a bot owns its session only when the title is exactly marker + bot name.
 */
class ManagedBotSessionTest {
    @Test
    fun `managed title embeds the bot name after an untypable marker`() {
        val title = ManagedBotSession.managedTitle("researcher")

        assertTrue(title.endsWith("researcher"))
        assertTrue(title.startsWith(ManagedBotSession.TITLE_MARKER))
        assertTrue(ManagedBotSession.TITLE_MARKER.first().category == CharCategory.FORMAT)
    }

    @Test
    fun `managed titles are invisible in plain text`() {
        val title = ManagedBotSession.managedTitle("researcher")

        assertEquals("researcher", title.replace(ManagedBotSession.TITLE_MARKER, ""))
        assertFalse(title.trim() == "researcher")
    }

    @Test
    fun `exact matching accepts only marker plus full bot name`() {
        assertTrue(ManagedBotSession.isTitleFor(ManagedBotSession.managedTitle("researcher"), "researcher"))
        assertFalse(ManagedBotSession.isTitleFor("researcher", "researcher"))
        assertFalse(ManagedBotSession.isTitleFor(ManagedBotSession.managedTitle("researcher"), "research"))
        assertFalse(ManagedBotSession.isTitleFor(ManagedBotSession.managedTitle("researcher"), "researcher2"))
        assertFalse(ManagedBotSession.isTitleFor(null, "researcher"))
        assertFalse(ManagedBotSession.isTitleFor("", "researcher"))
    }

    @Test
    fun `title detection and bot name round trip`() {
        assertTrue(ManagedBotSession.isManagedTitle(ManagedBotSession.managedTitle("researcher")))
        assertFalse(ManagedBotSession.isManagedTitle("plain title"))
        assertFalse(ManagedBotSession.isManagedTitle(null))
        assertEquals("researcher", ManagedBotSession.botName(ManagedBotSession.managedTitle("researcher")))
        assertNull(ManagedBotSession.botName("plain title"))
        assertNull(ManagedBotSession.botName(null))
    }

    @Test
    fun `blank bot names never produce managed titles`() {
        assertFalse(ManagedBotSession.isManagedTitle(ManagedBotSession.managedTitle(" ")))
        assertNull(ManagedBotSession.managedTitle("").let(ManagedBotSession::botName))
        assertFalse(ManagedBotSession.isTitleFor(ManagedBotSession.managedTitle("  "), "  "))
    }

    @Test
    fun `session classification and list filtering drop only managed rows`() {
        val managed = SessionInfo(id = "s1", title = ManagedBotSession.managedTitle("researcher"))
        val plain = SessionInfo(id = "s2", title = "researcher")
        val untitled = SessionInfo(id = "s3", title = null)

        assertTrue(managed.isManagedBotSession())
        assertFalse(plain.isManagedBotSession())
        assertFalse(untitled.isManagedBotSession())

        val filtered = filterManagedBotSessions(listOf(managed, plain, untitled))
        assertEquals(listOf("s2", "s3"), filtered.map { it.id })
    }
}
