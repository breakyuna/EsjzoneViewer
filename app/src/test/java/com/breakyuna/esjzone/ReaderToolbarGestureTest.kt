package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.ReaderDragAxis
import com.breakyuna.esjzone.ui.reader.ReaderTool
import com.breakyuna.esjzone.ui.reader.readerDragAxis
import com.breakyuna.esjzone.ui.reader.readerSidePanelShouldOpen
import com.breakyuna.esjzone.ui.reader.readerSwipeDirection
import org.junit.Assert.*
import org.junit.Test

class ReaderToolbarGestureTest {
    @Test fun directionLockAndBookmarkGesturesRequireClearDirectionalIntent() {
        assertNull(readerDragAxis(6f, 4f, 8f))
        assertEquals(ReaderDragAxis.HORIZONTAL, readerDragAxis(18f, 4f, 8f))
        assertEquals(ReaderDragAxis.HORIZONTAL, readerDragAxis(-18f, 4f, 8f))
        assertEquals(ReaderDragAxis.VERTICAL, readerDragAxis(20f, 15f, 8f))
        assertEquals(ReaderDragAxis.VERTICAL, readerDragAxis(4f, -18f, 8f))

        assertNull(readerSwipeDirection(0f, 50f, false, 96f))
        assertEquals(false, readerSwipeDirection(40f, 96f, false, 96f))
        assertEquals(true, readerSwipeDirection(45f, -120f, false, 96f))
        assertNull(readerSwipeDirection(100f, 120f, false, 96f))
    }

    @Test fun sidePanelReleaseRequiresDistanceOrADeliberateFling() {
        assertFalse(readerSidePanelShouldOpen(0.2f, false, 24f, 1800f))
        assertTrue(readerSidePanelShouldOpen(0.5f, false, 100f, 0f))
        assertTrue(readerSidePanelShouldOpen(0.2f, false, 48f, 1000f))
        assertFalse(readerSidePanelShouldOpen(0.7f, true, -48f, -1000f))
        assertTrue(readerSidePanelShouldOpen(0.7f, true, -48f, 0f))
        assertFalse(readerSidePanelShouldOpen(0f, false, -48f, 1000f))
    }

    @Test fun toolbarRestoresOrderIgnoresRemovedToolsAndAllowsEmptySelection() {
        assertEquals(ReaderTool.defaults, ReaderTool.decode(null))
        assertEquals(emptyList<ReaderTool>(), ReaderTool.decode(""))
        assertEquals(listOf(ReaderTool.BRIGHTNESS, ReaderTool.CONTENTS),
            ReaderTool.decode("SCRIPT,BRIGHTNESS,LAYOUT,CONTENTS,BRIGHTNESS,UNKNOWN"))
        assertEquals(ReaderTool.entries.take(ReaderTool.MAX_VISIBLE),
            ReaderTool.decode(ReaderTool.entries.joinToString(",") { it.name }))
    }

    @Test fun fullToolbarOrderRestoresHiddenPositionsAndMigratesExistingSelection() {
        val selected = listOf(ReaderTool.DARK_MODE, ReaderTool.CONTENTS)
        val migrated = ReaderTool.decodeOrder(null, selected)
        assertEquals(selected, migrated.take(2))
        assertEquals(ReaderTool.entries.toSet(), migrated.toSet())
        val restored = ReaderTool.decodeOrder("BRIGHTNESS,SCRIPT,CONTENTS,BRIGHTNESS,UNKNOWN")
        assertEquals(listOf(ReaderTool.BRIGHTNESS, ReaderTool.CONTENTS), restored.take(2))
        assertEquals(ReaderTool.entries.size, restored.size)
    }
}
