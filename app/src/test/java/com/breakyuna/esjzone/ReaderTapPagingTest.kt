package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.readerTapPageDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTapPagingTest {
    @Test
    fun leftAndRightActionsFollowTheSelectedDirection() {
        assertEquals(false, readerTapPageDirection(0.1f, true, false))
        assertEquals(true, readerTapPageDirection(0.9f, true, false))
        assertEquals(true, readerTapPageDirection(0.1f, true, true))
        assertEquals(false, readerTapPageDirection(0.9f, true, true))
        assertNull(readerTapPageDirection(0.5f, true, true))
        assertNull(readerTapPageDirection(0.1f, false, false))
    }
}
