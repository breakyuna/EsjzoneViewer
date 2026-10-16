package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.bookshelfAnchorRow
import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfLayoutTest {
    private val books = (0..11).map { "book-$it" }

    @Test
    fun switchingToGridKeepsTheVisibleBookInTheFirstRow() {
        assertEquals(3, bookshelfAnchorRow(books, "book-7", columns = 3, headerVisible = false))
    }

    @Test
    fun switchingBackToListLocatesTheBookRatherThanTheOldRowNumber() {
        assertEquals(7, bookshelfAnchorRow(books, "book-6", columns = 1, headerVisible = false))
    }

    @Test
    fun reorderedBooksUseTheirCurrentIdentityPosition() {
        assertEquals(2, bookshelfAnchorRow(books.reversed(), "book-7", columns = 3, headerVisible = false))
    }

    @Test
    fun visibleHeaderAndRemovedAnchorReturnToTheHeader() {
        assertEquals(0, bookshelfAnchorRow(books, "book-7", columns = 3, headerVisible = true))
        assertEquals(0, bookshelfAnchorRow(books, "removed", columns = 3, headerVisible = false))
    }
}
