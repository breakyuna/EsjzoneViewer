package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.ui.designsystem.useWideLayout
import com.breakyuna.esjzone.ui.designsystem.wideListColumnCount
import com.breakyuna.esjzone.ui.page.bookshelfColumnCount
import com.breakyuna.esjzone.ui.tab.prepareHomeSectionRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WideLayoutPolicyTest {
    @Test
    fun wideLayoutAndColumnsScaleForLandscapeTabletsAndLargeText() {
        assertFalse(useWideLayout(390f, 844f, 1f))
        assertFalse(useWideLayout(1000f, 1400f, 1f))
        assertTrue(useWideLayout(1000f, 700f, 1f))
        assertFalse(useWideLayout(1000f, 700f, 1.5f))
        assertTrue(useWideLayout(1200f, 700f, 1.5f))

        assertTrue(wideListColumnCount(1000f, 1f) >= 2)
        assertEquals(1, wideListColumnCount(1000f, 1.3f))
        assertEquals(3, bookshelfColumnCount(358f, 24f))
        assertTrue(bookshelfColumnCount(1000f, 24f, minCellWidth = 140f) >
            bookshelfColumnCount(1000f, 24f, minCellWidth = 140f * 1.3f))
    }

    @Test
    fun homeReflowPreservesBookOrderDeduplicationAdultFilteringAndSectionLimit() {
        val books = (1..24).map { CoveredNovelImpl(name = "Book $it", url = "/detail/$it.html", isAdult = it % 3 == 0) }
        val source = books + books.take(3)
        val compact = prepareHomeSectionRows(source, adult = false)
        val wide = prepareHomeSectionRows(source, adult = false, columns = 6)
        assertEquals(listOf(4, 4, 4, 4), compact.map { it.size })
        assertEquals(listOf(6, 6, 4), wide.map { it.size })
        assertEquals(compact.flatten(), wide.flatten())
        assertTrue(wide.flatten().none { it.isAdult })
        assertEquals(books.take(16), prepareHomeSectionRows(source, adult = true, columns = 6).flatten())
    }
}
