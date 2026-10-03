package com.m57.hermescontrol.notification

import com.m57.hermescontrol.data.ws.WsEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Group-member sessions (the bots' invisible managed-title sessions) must stay
 * out of the system shade: a completed reply or clarification that belongs to
 * a known member session is silently ignored.
 */
class BotSessionNotificationExclusionTest {
    @Test
    fun `a completed reply for an excluded session is ignored`() {
        val decision =
            notificationDecisionFor(
                WsEvent.MessageComplete("group answer", "member-session"),
                storedSessionId = null,
                excludedSessionIds = setOf("member-session"),
            )

        assertEquals(ChatNotificationDecision.Ignore, decision)
    }

    @Test
    fun `a completed reply whose stored session is excluded is ignored`() {
        val decision =
            notificationDecisionFor(
                WsEvent.MessageComplete("group answer", "runtime-member"),
                storedSessionId = "stored-member",
                excludedSessionIds = setOf("stored-member"),
            )

        assertEquals(ChatNotificationDecision.Ignore, decision)
    }

    @Test
    fun `a completed reply for an unrelated session is still notified`() {
        val decision =
            notificationDecisionFor(
                WsEvent.MessageComplete("normal reply", "normal-session"),
                storedSessionId = null,
                excludedSessionIds = setOf("member-session"),
            ) as ChatNotificationDecision.Reply

        assertEquals("normal reply", decision.preview)
        assertEquals("normal-session", decision.sessionId)
    }

    @Test
    fun `a clarify request for an excluded session is silent`() {
        val decision =
            notificationDecisionFor(
                WsEvent.ClarifyRequest("Which file?", listOf("a"), "c1", "member-session"),
                storedSessionId = "member-session",
                excludedSessionIds = setOf("member-session"),
            )

        assertEquals(ChatNotificationDecision.Ignore, decision)
    }

    @Test
    fun `exclusion never leaks excluded identities into decisions`() {
        val decision =
            notificationDecisionFor(
                WsEvent.MessageComplete("hello", "member-session"),
                storedSessionId = null,
                excludedSessionIds = setOf("member-session"),
            )

        assertTrue(decision.toString().let { it == "Ignore" })
        assertNull((decision as? ChatNotificationDecision.Reply)?.sessionId)
    }

    @Test
    fun `default exclusion keeps the historic behavior`() {
        val decision =
            notificationDecisionFor(
                WsEvent.MessageComplete("normal reply", "s1"),
                storedSessionId = null,
            ) as ChatNotificationDecision.Reply

        assertEquals("s1", decision.sessionId)
    }
}
