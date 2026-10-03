package com.m57.hermescontrol.ui.mcp

import com.m57.hermescontrol.data.remote.NetworkError
import com.m57.hermescontrol.util.SafeExternalUrl

internal enum class OAuthFlowState {
    PENDING,
    SUCCEEDED,
    FAILED,
}

/** Closed policy for the dashboard-hosted MCP OAuth flow. */
internal object McpOAuthPolicy {
    const val POLL_INTERVAL_MS = 2_000L
    const val FLOW_TIMEOUT_MS = 5 * 60 * 1_000L
    const val MAX_CONSECUTIVE_POLL_FAILURES = 3
    const val MAX_POLL_ATTEMPTS = 150

    fun classify(status: String): OAuthFlowState =
        when (status) {
            "authorization_required" -> OAuthFlowState.PENDING
            "approved", "completed" -> OAuthFlowState.SUCCEEDED
            else -> OAuthFlowState.FAILED
        }

    fun isTerminalPollError(error: NetworkError): Boolean =
        error is NetworkError.AuthExpired ||
            error is NetworkError.Http && error.code in setOf(403, 404)

    fun remainingFlowTimeMs(
        deadlineMs: Long,
        nowMs: Long,
    ): Long = (deadlineMs - nowMs).coerceAtLeast(0L)

    /**
     * Accept only ordinary HTTPS authorization URLs. The URL comes from a
     * remote dashboard response and is handed to another app through an
     * Android intent, so the shared external URL policy fails closed on
     * malformed URLs, credentials in the authority, custom schemes, and
     * unbounded input.
     */
    fun authorizationUrlOrNull(raw: String?): String? = SafeExternalUrl.sanitizeOrNull(raw)
}
