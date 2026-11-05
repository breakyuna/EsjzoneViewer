package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.*
import org.junit.Assert.*
import org.junit.Test

class ReaderImageBackgroundTest {
    @Test fun defaultsAndCustomColorsPreserveOpaqueRgb() {
        val defaults = ReaderImageBackground()
        assertEquals(0xFFFFFFFFL, defaults.textColor)
        assertTrue(defaults.glowEnabled)
        assertEquals(70f, defaults.glowStrength, 0f)
        val legacy = ReaderImageBackground("../../outside.jpg", 0x00123456L, glowStrength = Float.NaN).sanitized()
        assertEquals("", legacy.fileName)
        assertEquals(0xFF123456L, legacy.textColor)
        assertEquals(70f, legacy.glowStrength, 0f)
        assertEquals(100f, defaults.copy(glowStrength = 150f).sanitized().glowStrength, 0f)
        assertEquals(0f, defaults.copy(glowStrength = -5f).sanitized().glowStrength, 0f)
    }

    @Test fun photoAdjustmentsAreBoundedAndRejectNonFiniteValues() {
        val bounded = ReaderImageBackground(blur = 90f, transparency = -20f).sanitized()
        assertEquals(50f, bounded.blur, 0f)
        assertEquals(0f, bounded.transparency, 0f)
        val invalid = ReaderImageBackground(blur = Float.NaN, transparency = Float.POSITIVE_INFINITY).sanitized()
        assertEquals(0f, invalid.blur, 0f)
        assertEquals(0f, invalid.transparency, 0f)
    }

    @Test fun contrastAndGlowParameters() {
        assertEquals(21.0, readerContrast(readerPixelLuminance(0xFFFFFFFF.toInt()), readerPixelLuminance(0xFF000000.toInt())), 0.0001)
        assertEquals(listOf(0.07f, 0.17f, 0.30f, 0.40f), readerGlowRadiiEm)
        assertEquals(0.9f, READER_GLOW_ALPHA, 0f)
    }

    @Test fun degradationRequiresSustainedSlowFrames() {
        val slow = ReaderGlowFrameTracker()
        var reduced = false
        for (index in 0..125) reduced = reduced || slow.frame(1L + index * 33_333_334L)
        assertTrue(reduced)
        val fast = ReaderGlowFrameTracker()
        assertFalse((0..300).any { fast.frame(1L + it * 16_666_667L) })
    }
}
