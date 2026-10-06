package com.m57.hermescontrol.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.theme.presets.NothingOs5DarkColorScheme
import com.m57.hermescontrol.theme.presets.NothingOs5LightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NothingOs5SchemeTest {
    @Test
    fun `nothing os 5 is a selectable preset`() {
        assertTrue(ThemePreset.entries.contains(ThemePreset.NOTHING_OS_5))
    }

    @Test
    fun `nothing os 5 uses black white and red signal palette`() {
        assertEquals(Color(0xFF000000), NothingOs5DarkColorScheme.background)
        assertEquals(Color(0xFFD71921), NothingOs5DarkColorScheme.primary)
        assertEquals(Color(0xFFF4F4F2), NothingOs5LightColorScheme.background)
        assertEquals(Color(0xFFC81018), NothingOs5LightColorScheme.primary)
    }

    @Test
    fun `nothing os 5 uses spacious rounded surfaces`() {
        assertEquals(RoundedCornerShape(20.dp), NothingOs5Shapes.medium)
        assertEquals(RoundedCornerShape(28.dp), NothingOs5Shapes.large)
    }
}
