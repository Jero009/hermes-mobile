package com.m57.hermescontrol.ui.chat.components

import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole

internal fun toolActivityGroups(messages: List<ChatMessage>): Map<Int, List<ChatMessage>> {
    val groups = mutableMapOf<Int, List<ChatMessage>>()
    var index = 0
    while (index < messages.size) {
        if (!messages[index].isAggregatableToolActivity()) {
            index++
            continue
        }
        val start = index
        while (index < messages.size && messages[index].isAggregatableToolActivity()) index++
        groups[start] = messages.subList(start, index)
    }
    return groups
}

internal fun ChatMessage.isAggregatableToolActivity(): Boolean =
    role == MessageRole.TOOL && approvalInfo == null && clarifyInfo == null && displayKind == null
