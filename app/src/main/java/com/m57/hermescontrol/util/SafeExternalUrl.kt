package com.m57.hermescontrol.util

import java.net.URI

/**
 * Shared closed policy for URLs this app hands to external handlers
 * (browser, custom tabs). Sources are remote dashboards, OAuth providers,
 * and model-generated chat content — all untrusted — so fail closed on
 * malformed input, non-HTTPS schemes, credentials in the authority, missing
 * hosts, and unbounded lengths. Valid HTTPS URLs pass through unchanged so
 * user-initiated opens keep working.
 */
object SafeExternalUrl {
    const val MAX_LENGTH = 8_192

    /** Returns [raw] only if it is a bounded, host-bearing HTTPS URL; otherwise null. */
    fun sanitizeOrNull(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.length in 1..MAX_LENGTH } ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return value
    }
}
