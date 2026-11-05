package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.readerGlowTileWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderGlowTilePolicyTest {
    @Test fun keepsVisibleAndNearbyTilesDuringSmallScrolls() {
        val first = readerGlowTileWindow(textHeight = 10_000, viewportHeight = 800, textTop = -600f)
        val moved = readerGlowTileWindow(textHeight = 10_000, viewportHeight = 800, textTop = -650f)
        assertEquals(1..2, first.visible)
        assertEquals(0..3, first.buffered)
        assertEquals(first, moved)
        assertEquals(listOf(1, 2, 0, 3), first.inPriorityOrder())
    }

    @Test fun longParagraphUsesBoundedTilesAndDropsDistantContent() {
        val window = readerGlowTileWindow(textHeight = 1_000_000, viewportHeight = 1_000, textTop = -500_000f)
        assertTrue(window.visible.count() <= 3)
        assertTrue(window.buffered.count() <= 5)
        assertTrue(window.buffered.first > 0)
        assertTrue(readerGlowTileWindow(1_000_000, 1_000, 3_000f).buffered.isEmpty())
    }

    @Test fun handlesEmptyAndPartiallyVisibleText() {
        assertTrue(readerGlowTileWindow(0, 500, 0f).buffered.isEmpty())
        assertTrue(readerGlowTileWindow(1000, 0, 0f).visible.isEmpty())
        val entering = readerGlowTileWindow(2_000, 800, 700f)
        assertEquals(0..0, entering.visible)
        assertEquals(0..0, entering.buffered)
    }
}
