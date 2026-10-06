package com.m57.hermescontrol.ui.settings.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceLicensesTest {
    @Test
    fun `license dialog exposes app license and attribution notice`() {
        val documents = openSourceLicenseDocuments()

        assertEquals(listOf("Apache License 2.0", "Open-source notices"), documents.map { it.title })
        assertTrue(documents.all { it.rawResourceId != 0 })
    }
}
