package com.m57.hermescontrol.data.bots

import com.m57.hermescontrol.data.model.SessionInfo

/**
 * Invisible-title ownership marker for gateway sessions that back roster bots.
 *
 * A managed session's title is exactly [TITLE_MARKER] (zero-width space,
 * untypable on a normal keyboard) followed by the bot's profile name. The
 * marker makes the session invisible in ordinary title rendering while still
 * giving exact-match semantics: a user-authored session can never collide
 * with a managed one, and matching never degrades to substring or recency
 * heuristics.
 */
object ManagedBotSession {
    const val TITLE_MARKER: String = "\u200B"

    /** The invisible canonical title owning [botName]'s gateway session. */
    fun managedTitle(botName: String): String = "$TITLE_MARKER$botName"

    /** True when [title] is a well-formed managed title carrying a bot name. */
    fun isManagedTitle(title: String?): Boolean = botName(title) != null

    /** Exact ownership test: [title] must be exactly the managed title of [botName]. */
    fun isTitleFor(
        title: String?,
        botName: String,
    ): Boolean = botName.isNotBlank() && title == managedTitle(botName)

    /** The bot name embedded in a managed [title], or null for any other text. */
    fun botName(title: String?): String? =
        title
            ?.takeIf { it.length > TITLE_MARKER.length && it.startsWith(TITLE_MARKER) }
            ?.substring(TITLE_MARKER.length)
            ?.takeIf(String::isNotBlank)
}

/** True when this session is owned by a bot through an invisible managed title. */
fun SessionInfo.isManagedBotSession(): Boolean = ManagedBotSession.isManagedTitle(title)

/** Drops bot-owned managed sessions from a page of history rows. */
fun filterManagedBotSessions(sessions: List<SessionInfo>): List<SessionInfo> =
    sessions.filterNot { it.isManagedBotSession() }
