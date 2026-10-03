package com.m57.hermescontrol.ui.sessions

import com.m57.hermescontrol.data.bots.ManagedBotSession
import com.m57.hermescontrol.data.bots.filterManagedBotSessions
import com.m57.hermescontrol.data.model.SessionInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** Managed bot sessions must stay out of the Sessions (history) UI. */
class SessionsManagedFilterTest {
    private fun session(
        id: String,
        title: String?,
    ) = SessionInfo(id = id, title = title)

    @Test
    fun `managed rows are dropped while ordinary rows survive`() {
        val rows =
            listOf(
                session("a", "Weekly sync"),
                session("b", ManagedBotSession.managedTitle("researcher")),
                session("c", null),
                session("d", ""),
            )

        assertEquals(listOf("a", "c", "d"), filterManagedBotSessions(rows).map { it.id })
    }

    @Test
    fun `filter is idempotent and preserves order`() {
        val rows =
            listOf(
                session("a", "one"),
                session("b", ManagedBotSession.managedTitle("bot")),
            )
        val once = filterManagedBotSessions(rows)

        assertEquals(once, filterManagedBotSessions(once))
        assertEquals(listOf("a"), once.map { it.id })
    }
}
