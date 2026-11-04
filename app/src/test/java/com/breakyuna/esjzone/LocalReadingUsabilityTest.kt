package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.offline.DownloadedChapterRecord
import com.breakyuna.esjzone.ui.page.filterReaderCatalog
import com.breakyuna.esjzone.ui.page.localResumeRecord
import org.junit.Assert.*
import org.junit.Test

class LocalReadingUsabilityTest {
    @Test fun resumeRequiresLocalBodyAndFallsBackToFirstReadableChapter() {
        val records = listOf(
            DownloadedChapterRecord(0, "Missing", "https://www.esjzone.cc/forum/1/10.html", "10.json", false),
            DownloadedChapterRecord(1, "Text", "https://www.esjzone.cc/forum/1/11.html", "11.json", false, bodyAvailable = true),
            DownloadedChapterRecord(2, "Complete", "https://www.esjzone.cc/forum/1/12.html", "12.json", true)
        )
        assertEquals("Text", localResumeRecord(records, records[0].url)?.name)
        assertEquals("Complete", localResumeRecord(records, records[2].url)?.name)
        assertNull(localResumeRecord(records.take(1), records[0].url))
    }

    @Test fun catalogSearchKeepsOriginalOrderAndChapterIdentity() {
        val chapters = listOf(Chapter("Volume 2", "/2", true), Chapter("Volume 1", "/1", true), Chapter("Appendix", "/3", true))
        assertEquals(chapters.take(2), filterReaderCatalog(chapters, " volume "))
        assertEquals(chapters, filterReaderCatalog(chapters, "  "))
        assertTrue(filterReaderCatalog(chapters, "missing").isEmpty())
    }
}
