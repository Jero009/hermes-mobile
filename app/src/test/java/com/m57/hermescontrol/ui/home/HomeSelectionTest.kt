package com.m57.hermescontrol.ui.home

import com.m57.hermescontrol.data.model.CanonicalSessionInfo
import com.m57.hermescontrol.data.model.ProfileInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HomeSelectionTest {
    @Test
    fun `select active profile prefers server active then default then first`() {
        val profiles =
            listOf(
                ProfileInfo(name = "a", is_default = true),
                ProfileInfo(name = "b"),
            )
        val result = selectActiveProfile(profiles, "b")
        assertEquals("b", result)
    }

    @Test
    fun `select active profile prefers default when no selected`() {
        val profiles =
            listOf(
                ProfileInfo(name = "a", is_default = true),
                ProfileInfo(name = "b"),
            )
        assertEquals("a", selectActiveProfile(profiles, null))
    }

    @Test
    fun `select active profile falls back to first when no default`() {
        val profiles = listOf(ProfileInfo(name = "x"))
        assertEquals("x", selectActiveProfile(profiles, null))
    }

    @Test
    fun `visible profiles can open canonical session`() {
        val bot =
            ProfileInfo(
                name = "bot",
                canonical_session = CanonicalSessionInfo(id = "s1"),
            )
        assertNotNull(bot.canonicalSessionId)
        assertEquals("s1", bot.canonicalSessionId)
    }
}
