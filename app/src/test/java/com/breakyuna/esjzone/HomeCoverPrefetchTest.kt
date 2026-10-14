package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.tab.HomeCoverRow
import com.breakyuna.esjzone.ui.tab.upcomingHomeCovers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCoverPrefetchTest {
    private val rows = listOf(
        HomeCoverRow("hero", listOf("hero.jpg")),
        HomeCoverRow("actions"),
        HomeCoverRow("first", listOf("hero.jpg", "1.jpg", "2.jpg")),
        HomeCoverRow("second", listOf("3.jpg", "4.jpg", "5.jpg")),
        HomeCoverRow("third", listOf("6.jpg", "7.jpg", "8.jpg")),
        HomeCoverRow("last", listOf("9.jpg"))
    )

    @Test
    fun visibleImagesDoNotCompeteWithTheBoundedPrefetchWindow() {
        assertEquals((1..6).map { "$it.jpg" }, upcomingHomeCovers(rows, setOf("hero", "actions")))
        assertEquals((6..9).map { "$it.jpg" }, upcomingHomeCovers(rows, setOf("first", "second")))
        assertTrue(upcomingHomeCovers(rows, setOf("last")).isEmpty())
    }

    @Test
    fun coldStartIsBoundedAndUnknownLoadingItemsDoNotRestartTheQueue() {
        assertEquals(listOf("hero.jpg") + (1..5).map { "$it.jpg" }, upcomingHomeCovers(rows, emptySet()))
        assertTrue(upcomingHomeCovers(rows, setOf("loading")).isEmpty())
    }
}
