package com.m57.hermescontrol.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the shared closed policy for URLs this app hands to external
 * handlers (browser, custom tabs). Sources are remote dashboards, OAuth
 * providers, and model-generated chat content — all untrusted.
 */
class SafeExternalUrlTest {
    @Test
    fun validHttpsUrlPassesUnchanged() {
        assertEquals(
            "https://idp.example/authorize?state=opaque",
            SafeExternalUrl.sanitizeOrNull("https://idp.example/authorize?state=opaque"),
        )
        assertEquals(
            "https://portal.example.com:8443/subscription",
            SafeExternalUrl.sanitizeOrNull("https://portal.example.com:8443/subscription"),
        )
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(
            "https://example.com/x",
            SafeExternalUrl.sanitizeOrNull("  https://example.com/x  "),
        )
    }

    @Test
    fun rejectsCleartextAndCustomSchemes() {
        assertNull(SafeExternalUrl.sanitizeOrNull("http://example.com/authorize"))
        assertNull(SafeExternalUrl.sanitizeOrNull("javascript:alert(1)"))
        assertNull(SafeExternalUrl.sanitizeOrNull("intent://example.com/#Intent;end"))
        assertNull(SafeExternalUrl.sanitizeOrNull("file:///etc/passwd"))
        assertNull(SafeExternalUrl.sanitizeOrNull("ftp://example.com/file"))
        assertNull(SafeExternalUrl.sanitizeOrNull("example.com/no-scheme"))
    }

    @Test
    fun rejectsUrlsWithEmbeddedCredentials() {
        assertNull(SafeExternalUrl.sanitizeOrNull("https://user:secret@example.com/authorize"))
        assertNull(SafeExternalUrl.sanitizeOrNull("https://user@example.com/authorize"))
    }

    @Test
    fun rejectsMalformedAndHostlessUrls() {
        assertNull(SafeExternalUrl.sanitizeOrNull(null))
        assertNull(SafeExternalUrl.sanitizeOrNull(""))
        assertNull(SafeExternalUrl.sanitizeOrNull("   "))
        assertNull(SafeExternalUrl.sanitizeOrNull("https:///path-only"))
        assertNull(SafeExternalUrl.sanitizeOrNull("https://"))
        assertNull(SafeExternalUrl.sanitizeOrNull("not a url"))
    }

    @Test
    fun rejectsUnboundedInput() {
        val maxUrl = "https://example.com/" + "a".repeat(8_192 - "https://example.com/".length)
        assertEquals(maxUrl, SafeExternalUrl.sanitizeOrNull(maxUrl))
        assertNull(SafeExternalUrl.sanitizeOrNull(maxUrl + "a"))
    }
}
