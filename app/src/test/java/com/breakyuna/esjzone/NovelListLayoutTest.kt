package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.novelListAnchorRow
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelListLayoutTest {
    @Test fun switchingLayoutsKeepsTheFirstVisibleBookInTheTargetRow() {
        assertEquals(2, novelListAnchorRow(7, sourceColumns = 1, targetColumns = 3))
        assertEquals(9, novelListAnchorRow(3, sourceColumns = 3, targetColumns = 1))
        assertEquals(3, novelListAnchorRow(7, sourceColumns = 2, targetColumns = 4))
    }

    @Test fun refreshErrorIsAFullWidthItemInBothLayouts() {
        assertEquals(0, novelListAnchorRow(0, sourceColumns = 1, targetColumns = 3, leadingItemCount = 1))
        assertEquals(3, novelListAnchorRow(7, sourceColumns = 1, targetColumns = 3, leadingItemCount = 1))
        assertEquals(7, novelListAnchorRow(3, sourceColumns = 3, targetColumns = 1, leadingItemCount = 1))
    }
}
