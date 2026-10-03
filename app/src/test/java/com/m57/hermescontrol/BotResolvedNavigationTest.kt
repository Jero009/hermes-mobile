package com.m57.hermescontrol

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.m57.hermescontrol.data.model.ProfileInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bots whose canonical identity only exists locally (async resolution) open
 * through the resolved session id; the server canonical id always wins.
 */
class BotResolvedNavigationTest {
    @After
    fun tearDown() {
        NavigationController.backStack = null
        NavigationController.pendingSessionTarget = null
    }

    @Test
    fun `a locally resolved session id queues the pending target`() {
        val stack = NavBackStack<NavKey>(BotsScreen)
        NavigationController.backStack = stack

        NavigationController.openBot(ProfileInfo("bot"), "connection-a", resolvedSessionId = "resolved-1")

        assertEquals(PendingSessionTarget("resolved-1", "connection-a"), NavigationController.pendingSessionTarget)
        assertEquals(ChatScreen, stack.lastOrNull())
    }

    @Test
    fun `server canonical id wins over a locally resolved id`() {
        val stack = NavBackStack<NavKey>(BotsScreen)
        val bot =
            com.m57.hermescontrol.data.model.ProfileInfo(
                "bot",
                canonical_session = com.m57.hermescontrol.data.model.CanonicalSessionInfo("canonical"),
            )

        NavigationController.openBot(bot, "connection-a", resolvedSessionId = "resolved-1")

        assertEquals("canonical", NavigationController.pendingSessionTarget?.sessionId)
    }

    @Test
    fun `a blank resolved id without canonical identity does nothing`() {
        val stack = NavBackStack<NavKey>(BotsScreen)

        NavigationController.openBot(ProfileInfo("bot"), "connection-a", resolvedSessionId = " ")

        assertNull(NavigationController.pendingSessionTarget)
        assertEquals(listOf(BotsScreen), stack.toList())
    }
}
