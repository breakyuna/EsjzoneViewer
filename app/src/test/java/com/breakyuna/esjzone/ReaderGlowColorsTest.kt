package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.*
import org.junit.Assert.*
import org.junit.Test

class ReaderGlowColorsTest {
    @Test fun gamutMappingRetainsHueAndReducesChroma() {
        val requested = ReaderOklch(0.65, 0.8, 1.2)
        val mapped = readerOklch(readerGamutMap(requested))
        assertTrue(mapped.c < requested.c)
        assertEquals(requested.l, mapped.l, 0.005)
        assertEquals(requested.h, mapped.h, 0.03)
    }

    @Test fun defaultColorsPreserveContrastAcrossSaturatedAndMidtoneText() {
        for (text in listOf(0xFFFFFFFFL, 0xFF111111L, 0xFF808080L, 0xFFFF0000L,
            0xFF00FF00L, 0xFF0000FFL, 0xFFFF00FFL, 0xFF94A22FL)) {
            val resolved = readerGlowColors(ReaderImageBackground(textColor = text))
            assertTrue(readerContrast(readerPixelLuminance(resolved.text.toInt()),
                readerPixelLuminance(resolved.glow.toInt())) >= 4.5)
            val tone = readerOklch(resolved.text)
            val glow = readerOklch(resolved.glow)
            assertEquals(if (tone.l >= 0.55) 0.18 else 0.96, glow.l, 0.006)
        }
    }

    @Test fun customGlowAndClampSwitchAreRespected() {
        val settings = ReaderImageBackground(textColor = 0xFF808080L,
            glowMode = ReaderGlowMode.CUSTOM, customGlowColor = 0xFF808080L)
        val disabled = readerGlowColors(settings.copy(autoClampLightness = false))
        assertEquals(settings.textColor, disabled.text)
        assertEquals(settings.customGlowColor!!, disabled.glow)
        val enabled = readerGlowColors(settings)
        assertEquals(settings.customGlowColor!!, enabled.glow)
        assertTrue(readerContrast(readerPixelLuminance(enabled.text.toInt()),
            readerPixelLuminance(enabled.glow.toInt())) >= 4.5)
        assertEquals(settings.textColor, readerGlowColors(settings.copy(glowStrength = 0f)).text)
        assertEquals(settings.textColor, readerGlowColors(settings.copy(glowEnabled = false)).text)
    }

    @Test fun advancedSettingsSanitizeInvalidPreferences() {
        val settings = ReaderImageBackground(lightTextGlowL = Float.NaN, darkTextGlowL = 10f,
            glowChromaRetention = 120f, outerGlowAlpha = 0.2f).sanitized()
        assertEquals(18f, settings.lightTextGlowL, 0f)
        assertEquals(80f, settings.darkTextGlowL, 0f)
        assertEquals(100f, settings.glowChromaRetention, 0f)
        assertEquals(0.9f, settings.outerGlowAlpha, 0f)
    }
}
