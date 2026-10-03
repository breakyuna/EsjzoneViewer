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

    @Test fun sideGesturesRequireDistanceAndClearHorizontalIntent() {
        assertNull(readerSwipeDirection(71f, 0f, true, 72f))
        assertEquals(false, readerSwipeDirection(72f, 40f, true, 72f))
        assertEquals(true, readerSwipeDirection(-90f, 45f, true, 72f))
        assertNull(readerSwipeDirection(72f, 48f, true, 72f))
        assertNull(readerSwipeDirection(90f, 80f, true, 72f))
        assertNull(readerSwipeDirection(90f, 0f, true, 120f))
    }

    @Test fun bookmarkGesturesRequireDistanceAndClearVerticalIntent() {
        assertNull(readerSwipeDirection(0f, 95f, false, 96f))
        assertEquals(false, readerSwipeDirection(40f, 96f, false, 96f))
        assertEquals(true, readerSwipeDirection(45f, -120f, false, 96f))
        assertNull(readerSwipeDirection(64f, 96f, false, 96f))
        assertNull(readerSwipeDirection(100f, 120f, false, 96f))
    }
}
