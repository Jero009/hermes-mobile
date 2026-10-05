package com.m57.hermescontrol.ui.chat.components

import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolActivityGroupingTest {
    @Test
    fun `adjacent tool updates render from one aggregate entry`() {
        val messages =
            listOf(
                message("user", MessageRole.USER),
                message("terminal", MessageRole.TOOL),
                message("read-1", MessageRole.TOOL),
                message("read-2", MessageRole.TOOL),
                message("assistant", MessageRole.ASSISTANT),
            )

        val groups = toolActivityGroups(messages)

        assertEquals(listOf("terminal", "read-1", "read-2"), groups.getValue(1).map(ChatMessage::content))
        assertNull(groups[2])
        assertNull(groups[3])
    }

    @Test
    fun `user and assistant messages split tool activity runs`() {
        val messages =
            listOf(
                message("first", MessageRole.TOOL),
                message("reply", MessageRole.ASSISTANT),
                message("second", MessageRole.TOOL),
            )

        val groups = toolActivityGroups(messages)

        assertEquals(listOf("first"), groups.getValue(0).map(ChatMessage::content))
        assertEquals(listOf("second"), groups.getValue(2).map(ChatMessage::content))
    }

    @Test
    fun `timeline notices stay outside aggregates`() {
        val notice = message("notice", MessageRole.TOOL).copy(displayKind = "notice")
        val messages = listOf(message("first", MessageRole.TOOL), notice, message("second", MessageRole.TOOL))

        val groups = toolActivityGroups(messages)

        assertEquals(listOf("first"), groups.getValue(0).map(ChatMessage::content))
        assertNull(groups[1])
        assertEquals(listOf("second"), groups.getValue(2).map(ChatMessage::content))
    }

    private fun message(
        content: String,
        role: MessageRole,
    ) = ChatMessage(role = role, content = content)
}
