package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.ReaderTool
import com.breakyuna.esjzone.ui.reader.readerSwipeDirection
import com.breakyuna.esjzone.ui.reader.ReaderDragAxis
import com.breakyuna.esjzone.ui.reader.readerDragAxis
import com.breakyuna.esjzone.ui.reader.readerSidePanelShouldOpen
import org.junit.Assert.*
import org.junit.Test

class ReaderToolbarGestureTest {
    @Test fun directionLockReservesAmbiguousDirectionsForVerticalGestures() {
        assertNull(readerDragAxis(6f, 4f, 8f))
        assertEquals(ReaderDragAxis.HORIZONTAL, readerDragAxis(18f, 4f, 8f))
        assertEquals(ReaderDragAxis.HORIZONTAL, readerDragAxis(-18f, 4f, 8f))
        assertEquals(ReaderDragAxis.VERTICAL, readerDragAxis(20f, 15f, 8f))
        assertEquals(ReaderDragAxis.VERTICAL, readerDragAxis(4f, -18f, 8f))
        assertEquals(ReaderDragAxis.VERTICAL, readerDragAxis(18f, 9f, 8f))
    }

    @Test fun sidePanelReleaseRequiresDistanceOrADeliberateFling() {
        assertFalse(readerSidePanelShouldOpen(0.2f, false, 24f, 1800f))
        assertFalse(readerSidePanelShouldOpen(0.39f, false, 100f, 0f))
        assertTrue(readerSidePanelShouldOpen(0.4f, false, 100f, 0f))
        assertTrue(readerSidePanelShouldOpen(0.2f, false, 48f, 1000f))
        assertFalse(readerSidePanelShouldOpen(0.7f, true, -48f, -1000f))
        assertTrue(readerSidePanelShouldOpen(0.7f, true, -48f, 0f))
        assertFalse(readerSidePanelShouldOpen(0.59f, true, -100f, 0f))
        assertFalse(readerSidePanelShouldOpen(0f, false, -48f, 1000f))
    }

    @Test fun toolbarRestoresOrderAndAllowsEmptySelection() {
        assertEquals(ReaderTool.defaults, ReaderTool.decode(null))
        assertEquals(emptyList<ReaderTool>(), ReaderTool.decode(""))
        assertEquals(listOf(ReaderTool.BRIGHTNESS, ReaderTool.CONTENTS),
            ReaderTool.decode("BRIGHTNESS,CONTENTS,BRIGHTNESS,UNKNOWN"))
        assertEquals(6, ReaderTool.decode(ReaderTool.entries.joinToString(",") { it.name }).size)
    }

    @Test fun bookmarkGesturesRequireDistanceAndClearVerticalIntent() {
        assertNull(readerSwipeDirection(0f, 95f, false, 96f))
        assertEquals(false, readerSwipeDirection(40f, 96f, false, 96f))
        assertEquals(true, readerSwipeDirection(45f, -120f, false, 96f))
        assertNull(readerSwipeDirection(64f, 96f, false, 96f))
        assertNull(readerSwipeDirection(100f, 120f, false, 96f))
    }
}
