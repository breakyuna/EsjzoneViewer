package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.restoredCommentPageIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentPageRestorationTest {
    @Test
    fun returningKeepsTheChosenPageEvenAfterAnEarlierReply() {
        assertEquals(19, restoredCommentPageIndex(19, 40, 594, false))
        assertEquals(39, restoredCommentPageIndex(39, 40, -1, false))
    }

    @Test
    fun onlyANewReplySelectsItsPage() {
        assertEquals(39, restoredCommentPageIndex(19, 40, 594, true))
        assertEquals(19, restoredCommentPageIndex(19, 40, -1, true))
    }

    @Test
    fun freshThreadStartsAtTopAndShortenedDataClampsSelection() {
        assertEquals(0, restoredCommentPageIndex(0, 40, -1, false))
        assertEquals(1, restoredCommentPageIndex(39, 2, -1, false))
        assertEquals(0, restoredCommentPageIndex(39, 0, -1, false))
    }
}
