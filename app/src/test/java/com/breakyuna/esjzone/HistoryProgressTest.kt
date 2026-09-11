package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.fullBookProgress
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure boundary coverage for the local history book-progress projection. */
class HistoryProgressTest {
    @Test
    fun mapsChapterFractionIntoWholeBookFraction() {
        assertEquals(0.05f, fullBookProgress(chapterIndex = 0, totalChapters = 10, chapterProgress = 0.5f))
        assertEquals(0.95f, fullBookProgress(chapterIndex = 9, totalChapters = 10, chapterProgress = 0.5f))
        assertEquals(1f, fullBookProgress(chapterIndex = 9, totalChapters = 10, chapterProgress = 1f))
    }

    @Test
    fun clampsInvalidFractionsAndFallsBackWhenBookPositionUnknown() {
        assertEquals(0.2f, fullBookProgress(chapterIndex = 2, totalChapters = 10, chapterProgress = Float.NaN))
        assertEquals(1f, fullBookProgress(chapterIndex = 2, totalChapters = 10, chapterProgress = 4f))
        assertEquals(0.4f, fullBookProgress(chapterIndex = -1, totalChapters = 0, chapterProgress = 0.4f))
    }
}
