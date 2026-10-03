package com.m57.hermescontrol.data.bots

import com.m57.hermescontrol.data.ws.WsEvent
import java.security.MessageDigest

/**
 * One merged line in a local group chat transcript. [sender] is `"user"` for
 * locally dispatched messages or the owning bot's profile name for replies
 * merged from that bot's member session.
 */
data class GroupTranscriptEntry(
    val id: String,
    val sender: String,
    val fromUser: Boolean,
    val content: String,
    val timestamp: Long,
    /** The member runtime session a merged reply arrived on. */
    val sourceSessionId: String? = null,
)

/**
 * Pure transcript merge rules for local group chat v1. There is no backend
 * group API: the room is a local projection over per-bot sessions, so merges
 * are keyed by session provenance and stable content identity.
 */
object GroupTranscript {
    const val USER_SENDER: String = "user"

    /**
     * Deterministic user entry: dispatching the same text twice within the
     * same millisecond cannot double-persist, while distinct sends keep
     * distinct ids.
     */
    fun userEntry(
        roomId: String,
        text: String,
        timestamp: Long,
    ): GroupTranscriptEntry =
        GroupTranscriptEntry(
            id = "grp-user:${stableHash(roomId, text, timestamp.toString())}",
            sender = USER_SENDER,
            fromUser = true,
            content = text,
            timestamp = timestamp,
        )

    /** Stable id for a merged reply so redelivery replaces rather than duplicates. */
    fun assistantEntryId(
        roomId: String,
        sourceSessionId: String,
        text: String,
    ): String = "grp-bot:${stableHash(roomId, sourceSessionId, text)}"

    /**
     * Merge a completed gateway reply into the room transcript, but only when
     * the session that produced it is a member session of this room. Sessions
     * outside the membership (stray turns, other rooms, 1:1 chats) never merge.
     */
    fun entryForEvent(
        complete: WsEvent.MessageComplete,
        membersBySessionId: Map<String, String>,
        timestamp: Long,
        roomId: String,
    ): GroupTranscriptEntry? {
        val sessionId = complete.sessionId?.takeIf(String::isNotBlank) ?: return null
        val botName = membersBySessionId[sessionId] ?: return null
        return GroupTranscriptEntry(
            id = assistantEntryId(roomId, sessionId, complete.text),
            sender = botName,
            fromUser = false,
            content = complete.text,
            timestamp = timestamp,
            sourceSessionId = sessionId,
        )
    }

    private fun stableHash(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}
