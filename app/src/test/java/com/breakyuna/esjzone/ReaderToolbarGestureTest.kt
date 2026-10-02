package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.ReaderTool
import com.breakyuna.esjzone.ui.reader.readerSwipeDirection
import org.junit.Assert.*
import org.junit.Test

class ReaderToolbarGestureTest {
    @Test fun toolbarRestoresOrderAndAllowsEmptySelection() {
        assertEquals(ReaderTool.defaults, ReaderTool.decode(null))
        assertEquals(emptyList<ReaderTool>(), ReaderTool.decode(""))
        assertEquals(listOf(ReaderTool.BRIGHTNESS, ReaderTool.CONTENTS),
            ReaderTool.decode("BRIGHTNESS,CONTENTS,BRIGHTNESS,UNKNOWN"))
        assertEquals(6, ReaderTool.decode(ReaderTool.entries.joinToString(",") { it.name }).size)
    }

    @Test fun sideGesturesRequireDistanceAndClearDirection() {
        assertNull(readerSwipeDirection(71f, 0f, true, 72f))
        assertNull(readerSwipeDirection(80f, 70f, true, 72f))
        assertEquals(false, readerSwipeDirection(90f, 10f, true, 72f))
        assertEquals(true, readerSwipeDirection(-90f, 10f, true, 72f))
        assertNull(readerSwipeDirection(10f, 180f, true, 72f))
    }

    @Test fun bookmarkGesturesRequireLongVerticalMovement() {
        assertNull(readerSwipeDirection(0f, 95f, false, 96f))
        assertNull(readerSwipeDirection(90f, 100f, false, 96f))
        assertEquals(false, readerSwipeDirection(10f, 130f, false, 96f))
        assertEquals(true, readerSwipeDirection(10f, -130f, false, 96f))
        assertNull(readerSwipeDirection(180f, 10f, false, 96f))
    }
}
