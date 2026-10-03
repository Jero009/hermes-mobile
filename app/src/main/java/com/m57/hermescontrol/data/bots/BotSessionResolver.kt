package com.m57.hermescontrol.data.bots

import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.model.SessionListResponse
import com.m57.hermescontrol.data.remote.HermesApiService
import com.m57.hermescontrol.data.ws.WsMethods
import java.io.IOException

/** How a bot's gateway session identity was established. */
enum class BotResolutionSource {
    /** The server reported the profile's canonical session directly. */
    CANONICAL,

    /** Exactly one session carries the bot's invisible managed title. */
    MANAGED_TITLE,

    /** A hidden managed session was created in the bot's own profile. */
    CREATED,
}

/** Outcome of resolving the gateway session that backs a roster bot. */
sealed interface BotSessionResolution {
    data class Resolved(
        val sessionId: String,
        val via: BotResolutionSource,
    ) : BotSessionResolution

    /** More than one session carries the bot's managed title — fail closed rather than guess. */
    data class Ambiguous(
        val count: Int,
    ) : BotSessionResolution

    /** Transport or protocol failure during resolution. */
    data class Failed(
        val reason: String,
    ) : BotSessionResolution
}

/**
 * Resolves the gateway session backing a roster bot, in a fixed order:
 *
 * 1. The server's canonical session id for the profile (async roster load).
 * 2. A paged session-title search verified by exact unique invisible-title
 *    matching — one exact match resolves, several fail closed, and recency is
 *    never used to select anything.
 * 3. Create a hidden managed session in the bot's profile using the gateway's
 *    documented per-request `profile` scope.
 */
class BotSessionResolver(
    private val api: HermesApiService,
    private val gatewayRequest: suspend (method: String, params: Map<String, Any>) -> Result<Any?>,
    private val searchPages: Int = SEARCH_PAGE_LIMIT,
    private val pageSize: Int = SEARCH_PAGE_SIZE,
) {
    suspend fun resolve(bot: ProfileInfo): BotSessionResolution {
        bot.canonicalSessionId?.let { canonical ->
            return BotSessionResolution.Resolved(canonical, BotResolutionSource.CANONICAL)
        }

        return when (val titled = findByManagedTitle(bot.name)) {
            is TitleSearchResult.Unique ->
                BotSessionResolution.Resolved(
                    titled.sessionId,
                    BotResolutionSource.MANAGED_TITLE,
                )
            is TitleSearchResult.Ambiguous -> BotSessionResolution.Ambiguous(titled.count)
            is TitleSearchResult.Failed -> BotSessionResolution.Failed(titled.reason)
            TitleSearchResult.None -> provisionNewSession(bot)
        }
    }

    private sealed interface TitleSearchResult {
        data class Unique(
            val sessionId: String,
        ) : TitleSearchResult

        data class Ambiguous(
            val count: Int,
        ) : TitleSearchResult

        data object None : TitleSearchResult

        data class Failed(
            val reason: String,
        ) : TitleSearchResult
    }

    /**
     * Scan recent-ordered pages while matching only on the exact invisible
     * managed title. Ordering is a transport detail of pagination — selection
     * is purely exact-title identity, never recency.
     */
    private suspend fun findByManagedTitle(botName: String): TitleSearchResult {
        val managedTitle = ManagedBotSession.managedTitle(botName)
        var offset = 0
        var matches = 0
        var firstMatch: String? = null
        repeat(searchPages) {
            val response =
                try {
                    api.getSessions(
                        limit = pageSize,
                        offset = offset,
                        order = "recent",
                        source = null,
                        excludeSources = null,
                    )
                } catch (e: IOException) {
                    return TitleSearchResult.Failed(e.message ?: "title search failed")
                }
            val page: SessionListResponse =
                response.body() ?: return TitleSearchResult.Failed("empty title search response")
            page.sessions
                .filter { ManagedBotSession.isTitleFor(it.title, botName) }
                .forEach { match ->
                    if (firstMatch == null) firstMatch = match.id
                    matches++
                }
            // Server pagination counts rows the server would return per page
            // (`limit`), matching SessionsViewModel.nextOffset — never the
            // filtered row count.
            val advance = if (page.limit > 0) page.limit else page.sessions.size
            if (page.sessions.isEmpty() || advance <= 0 || (page.total > 0 && offset + advance >= page.total)) {
                return when {
                    matches == 1 -> TitleSearchResult.Unique(requireNotNull(firstMatch))
                    matches > 1 -> TitleSearchResult.Ambiguous(matches)
                    else -> TitleSearchResult.None
                }
            }
            offset += advance
        }
        return when {
            matches == 1 -> TitleSearchResult.Unique(requireNotNull(firstMatch))
            matches > 1 -> TitleSearchResult.Ambiguous(matches)
            else -> TitleSearchResult.None
        }
    }

    private suspend fun provisionNewSession(bot: ProfileInfo): BotSessionResolution {
        val created =
            gatewayRequest(
                WsMethods.SESSION_CREATE,
                mapOf(
                    "source" to "desktop",
                    "profile" to bot.name,
                    "title" to ManagedBotSession.managedTitle(bot.name),
                    "hidden" to true,
                ),
            ).getOrElse {
                return BotSessionResolution.Failed(it.message ?: "session.create failed")
            }
        val sessionId =
            (created as? Map<*, *>)?.get("session_id") as? String
        if (sessionId.isNullOrBlank()) {
            return BotSessionResolution.Failed("session.create returned no session id")
        }

        return BotSessionResolution.Resolved(sessionId, BotResolutionSource.CREATED)
    }

    private companion object {
        /** Bounded scan: 5 pages of 100 sessions. Recency is never a match rule. */
        const val SEARCH_PAGE_LIMIT = 5
        const val SEARCH_PAGE_SIZE = 100
    }
}
