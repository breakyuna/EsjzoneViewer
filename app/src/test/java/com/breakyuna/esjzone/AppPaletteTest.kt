package com.breakyuna.esjzone

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPaletteTest {
    @Test
    fun missingOrInvalidPaletteUsesClassic() {
        assertEquals(AppPalette.MONOCHROME, AppPalette.fromName(null))
        assertEquals(AppPalette.MONOCHROME, AppPalette.fromName("unknown"))
    }

    @Test
    fun savedPaletteChoicesArePreserved() {
        AppPalette.entries.forEach { palette ->
            assertEquals(palette, AppPalette.fromName(palette.name))
        }
    }
}
