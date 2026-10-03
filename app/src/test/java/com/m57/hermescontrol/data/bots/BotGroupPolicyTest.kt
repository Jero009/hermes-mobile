package com.m57.hermescontrol.data.bots

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** A local phone group chat v1 carries 2–6 distinct roster bots. */
class BotGroupPolicyTest {
    @Test
    fun `roster bounds are two through six`() {
        assertEquals(2, BotGroupPolicy.MIN_MEMBERS)
        assertEquals(6, BotGroupPolicy.MAX_MEMBERS)
    }

    @Test
    fun `valid rosters are distinct non-blank bots within bounds`() {
        assertEquals(BotGroupRosterValidity.OK, BotGroupPolicy.validate(listOf("alpha", "beta")))
        assertEquals(
            BotGroupRosterValidity.OK,
            BotGroupPolicy.validate(listOf("a", "b", "c", "d", "e", "f")),
        )
    }

    @Test
    fun `too few members are rejected`() {
        assertEquals(BotGroupRosterValidity.TOO_FEW, BotGroupPolicy.validate(emptyList()))
        assertEquals(BotGroupRosterValidity.TOO_FEW, BotGroupPolicy.validate(listOf("alpha")))
    }

    @Test
    fun `too many members are rejected`() {
        assertEquals(
            BotGroupRosterValidity.TOO_MANY,
            BotGroupPolicy.validate(listOf("a", "b", "c", "d", "e", "f", "g")),
        )
    }

    @Test
    fun `duplicate members are rejected`() {
        assertEquals(BotGroupRosterValidity.DUPLICATE, BotGroupPolicy.validate(listOf("alpha", "alpha")))
    }

    @Test
    fun `blank member names are rejected`() {
        assertEquals(BotGroupRosterValidity.TOO_FEW, BotGroupPolicy.validate(listOf("alpha", " ")))
        assertFalse(BotGroupPolicy.validate(listOf("", "beta")) == BotGroupRosterValidity.OK)
    }
}
