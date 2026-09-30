package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.ReaderBoundaryTurn
import com.breakyuna.esjzone.ui.reader.resolveReaderBoundaryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderBoundaryTurnTest {
    @Test fun prependTurnsToPreviousChaptersLastPage() {
        assertEquals(1, resolveReaderBoundaryPage(
            ReaderBoundaryTurn(false, "5:0", "4"),
            listOf("4:0", "4:1", "5:0", "5:1"), listOf("4", "4", "5", "5")
        ))
    }

    @Test fun fullWindowCanTurnAfterOppositeEndIsTrimmed() {
        val pages = (2..10).map { "$it:0" }
        assertEquals(8, resolveReaderBoundaryPage(
            ReaderBoundaryTurn(true, "9:0", "10"), pages, (2..10).map(Int::toString)
        ))
        assertEquals(0, resolveReaderBoundaryPage(
            ReaderBoundaryTurn(false, "2:0", "1"), (1..9).map { "$it:0" }, (1..9).map(Int::toString)
        ))
    }

    @Test fun unrelatedLoadOrChapterJumpCannotFulfillOldTurn() {
        assertNull(resolveReaderBoundaryPage(
            ReaderBoundaryTurn(true, "5:1", "6"), listOf("5:1", "7:0"), listOf("5", "7")
        ))
        assertNull(resolveReaderBoundaryPage(
            ReaderBoundaryTurn(false, "5:0", "4"), listOf("1:0", "2:0"), listOf("1", "2")
        ))
    }
}
