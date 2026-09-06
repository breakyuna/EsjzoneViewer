package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsFormattingTest {
    @Test
    fun formatBytesUsesReadableUnitsAndClampsNegativeValues() {
        assertEquals("0 B", formatBytes(-1))
        assertEquals("1.0 KiB", formatBytes(1024))
        assertEquals("1.0 MiB", formatBytes(1024 * 1024))
    }
}
