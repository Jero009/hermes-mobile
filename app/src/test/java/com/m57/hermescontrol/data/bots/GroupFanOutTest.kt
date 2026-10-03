package com.m57.hermescontrol.data.bots

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fan-out is executed member-by-member in roster order: profile currency is
 * re-checked immediately before every dispatch, so a connection-profile change
 * mid fan-out fences every remaining member rather than sending under new
 * credentials. Unresolved members are skipped with a reason — never guessed.
 */
class GroupFanOutTest {
    private class Recorder {
        val dispatched = mutableListOf<Pair<String, String>>()

        fun dispatch(
            botName: String,
            sessionId: String,
            text: String,
        ) {
            dispatched.add(botName to sessionId)
        }
    }

    @Test
    fun `resolved members dispatch in order and unresolved members are skipped`() {
        val recorder = Recorder()

        val report =
            GroupFanOut.execute(
                members =
                    listOf(
                        GroupMemberTarget("alpha", "session-alpha"),
                        GroupMemberTarget("beta", null),
                        GroupMemberTarget("gamma", "session-gamma"),
                    ),
                isProfileCurrent = { true },
                text = "hello",
                dispatch = recorder::dispatch,
            )

        assertEquals(
            listOf("alpha" to "session-alpha", "gamma" to "session-gamma"),
            recorder.dispatched,
        )
        assertEquals(listOf("alpha", "gamma"), report.sentBots)
        assertEquals(listOf("beta"), report.unresolvedBots)
        assertEquals(0, report.fencedCount)
    }

    @Test
    fun `a profile change mid fan-out fences every remaining member`() {
        val recorder = Recorder()
        var current = true

        val report =
            GroupFanOut.execute(
                members =
                    listOf(
                        GroupMemberTarget("alpha", "session-alpha"),
                        GroupMemberTarget("beta", "session-beta"),
                        GroupMemberTarget("gamma", "session-gamma"),
                    ),
                isProfileCurrent = { current },
                text = "hello",
                dispatch = { botName, sessionId, _ ->
                    recorder.dispatch(botName, sessionId, "hello")
                    // The first dispatch flips the active connection profile.
                    current = false
                },
            )

        assertEquals(listOf("alpha" to "session-alpha"), recorder.dispatched)
        assertEquals(listOf("alpha"), report.sentBots)
        assertEquals(emptyList<String>(), report.unresolvedBots)
        assertEquals(2, report.fencedCount)
    }

    @Test
    fun `a profile already stale before the first member fences everyone`() {
        val recorder = Recorder()

        val report =
            GroupFanOut.execute(
                members = listOf(GroupMemberTarget("alpha", "session-alpha")),
                isProfileCurrent = { false },
                text = "hello",
                dispatch = recorder::dispatch,
            )

        assertTrue(recorder.dispatched.isEmpty())
        assertEquals(1, report.fencedCount)
    }

    @Test
    fun `planner never dispatches blank session ids`() {
        val recorder = Recorder()

        val report =
            GroupFanOut.execute(
                members =
                    listOf(
                        GroupMemberTarget("alpha", " "),
                        GroupMemberTarget("beta", ""),
                    ),
                isProfileCurrent = { true },
                text = "hello",
                dispatch = recorder::dispatch,
            )

        assertTrue(recorder.dispatched.isEmpty())
        assertEquals(listOf("alpha", "beta"), report.unresolvedBots)
        assertEquals(emptyList<String>(), report.sentBots)
    }
}
