package com.breakyuna.esjzone.ui.page

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderLayoutTest {
    @Test
    fun scrollBodyStaysAboveTheChapterStatusOverlay() {
        val endPadding = ReaderLayout.contentBottomPadding

        val (noOverlayInset, noOverlayPadding) = readerScrollBottomSpace(endPadding, 0.dp)
        assertEquals(0.dp, noOverlayInset)
        assertEquals(endPadding, noOverlayPadding)

        val (clearedInset, clearedPadding) = readerScrollBottomSpace(endPadding, 48.dp)
        assertEquals(48.dp, clearedInset)
        assertEquals(endPadding - 48.dp, clearedPadding)
        assertEquals(endPadding, clearedInset + clearedPadding)

        val tallerThanEndGap = endPadding + 40.dp
        val (tallInset, tallPadding) = readerScrollBottomSpace(endPadding, tallerThanEndGap)
        assertEquals(tallerThanEndGap, tallInset)
        assertEquals(0.dp, tallPadding)
    }
}
