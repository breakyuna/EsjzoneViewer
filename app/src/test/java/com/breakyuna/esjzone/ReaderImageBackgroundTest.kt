package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.parseReaderTextColor
import com.breakyuna.esjzone.ui.reader.ReaderImageBackground
import org.junit.Assert.*
import org.junit.Test

class ReaderImageBackgroundTest {
    @Test fun invalidStoredPhotoPathsAndBlurValuesAreSanitized() {
        val settings = ReaderImageBackground("../../outside.jpg", Float.NaN, 0x00123456L).sanitized()
        assertEquals("", settings.fileName)
        assertEquals(20f, settings.blur)
        assertEquals(0xFF123456L, settings.textColor)
        assertEquals(49f, ReaderImageBackground(blur = 49f).sanitized().blur, 0f)
        assertEquals(50f, ReaderImageBackground(blur = 150f).sanitized().blur, 0f)
    }

    @Test fun customTextColorsRequireSixHexDigitsAndRemainOpaque() {
        assertEquals(0xFFABCDEF, parseReaderTextColor(" #aBcDeF "))
        assertEquals(0xFF000000, parseReaderTextColor("000000"))
        assertNull(parseReaderTextColor("#fff"))
        assertNull(parseReaderTextColor("#00112233"))
        assertNull(parseReaderTextColor("#zzzzzz"))
    }

    @Test fun backgroundTransparencyIsBoundedAndMapsToImageOpacity() {
        assertEquals(1f, ReaderImageBackground().imageAlpha, 0f)
        assertEquals(0.5f, ReaderImageBackground(transparency = 50f).imageAlpha, 0f)
        assertEquals(0f, ReaderImageBackground(transparency = 100f).imageAlpha, 0f)
        assertEquals(0f, ReaderImageBackground(transparency = Float.NaN).sanitized().transparency, 0f)
        assertEquals(0f, ReaderImageBackground(transparency = -10f).sanitized().transparency, 0f)
        assertEquals(100f, ReaderImageBackground(transparency = 150f).sanitized().transparency, 0f)
    }
}
