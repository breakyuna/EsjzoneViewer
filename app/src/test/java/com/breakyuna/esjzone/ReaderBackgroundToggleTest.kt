package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.ReaderBackground
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.reader.toggleDarkBackground
import com.breakyuna.esjzone.ui.reader.effectiveBackground
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderBackgroundToggleTest {
    @Test fun palettesAndPhotosSurviveThemeChangesFromAnyEntry() {
        ReaderBackground.entries.forEach { background ->
            val dark = ReaderSettings(background = background).toggleDarkBackground(true)
            assertEquals(background, dark.background)
            assertEquals(ReaderBackground.SYSTEM, dark.effectiveBackground(isDark = true))
            // Application settings or the system can switch theme without the reader button.
            assertEquals(background, dark.effectiveBackground(isDark = false))
            val nextDark = dark.toggleDarkBackground(true)
            assertEquals(background, nextDark.toggleDarkBackground(false).effectiveBackground(isDark = false))
        }
    }

    @Test fun choosingABackgroundWhileDarkCanDisableTheTemporaryOverride() {
        val dark = ReaderSettings(background = ReaderBackground.PAPER).toggleDarkBackground(true)
        val selected = dark.copy(background = ReaderBackground.CUSTOM_IMAGE, useThemeBackgroundInDarkMode = false)
        assertEquals(ReaderBackground.CUSTOM_IMAGE, selected.effectiveBackground(isDark = true))
        assertEquals(ReaderBackground.CUSTOM_IMAGE, selected.effectiveBackground(isDark = false))
    }
}
