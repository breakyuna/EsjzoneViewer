package com.breakyuna.esjzone

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.breakyuna.esjzone.ui.reader.*
import org.junit.Assert.*
import org.junit.Test

class ReaderImageBackgroundTest {
    @Test fun sanitizedRejectsPathTraversalAndPreservesOpaqueRgb() {
        val legacy = ReaderImageBackground("../../outside.jpg", 0x00123456L, glowStrength = Float.NaN).sanitized()
        assertEquals("", legacy.fileName)
        assertEquals(0xFF123456L, legacy.textColor)
        assertEquals(70f, legacy.glowStrength, 0f)
    }

    @Test fun cropTracksDraggingOnlyWhereThePhotoOverflowsAndStopsAtTheEdges() {
        val centered = ReaderImageBackground()
        val viewport = IntSize(100, 200)
        val wide = IntSize(200, 200)
        val tall = IntSize(100, 300)
        val horizontal = centered.pan(Offset(25f, 80f), viewport, wide)
        assertEquals(0.25f, horizontal.cropX, 0f)
        assertEquals(0.5f, horizontal.cropY, 0f)
        val vertical = centered.pan(Offset(80f, -25f), viewport, tall)
        assertEquals(0.5f, vertical.cropX, 0f)
        assertEquals(0.75f, vertical.cropY, 0f)
        assertEquals(0f, horizontal.pan(Offset(200f, 0f), viewport, wide).cropX, 0f)
        assertEquals(1f, vertical.pan(Offset(0f, -200f), viewport, tall).cropY, 0f)
        assertEquals(centered, centered.pan(Offset(80f, 80f), viewport, viewport))
    }

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
}
