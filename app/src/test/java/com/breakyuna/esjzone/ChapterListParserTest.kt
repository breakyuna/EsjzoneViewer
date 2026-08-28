package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.novel.analyseChapterList
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChapterListParserTest {

    @Test
    fun parsesIntegrationDetailsAndNestedChapterLinks() {
        val integration = Jsoup.parse(
            """
            <div id="integration">
                <div class="volumes">
                    <details>
                        <summary>第一卷</summary>
                        <div class="chapter-row">
                            <a href="/forum/123/1.html" data-title="第一章"><p>第一章</p></a>
                            <a href="/forum/123/2.html"><p>第二章</p></a>
                        </div>
                    </details>
                </div>
            </div>
            """.trimIndent()
        ).selectFirst("#integration")

        assertNotNull(integration)
        val chapterList = analyseChapterList(integration!!)
        assertEquals(1, chapterList.items.size)

        val volume = chapterList.items.single() as ChapterListItem
        assertEquals(2, volume.chapters.size)
        assertEquals("第一章", volume.chapters[0].name)
        assertEquals("第二章", volume.chapters[1].name)
        assertEquals("/forum/123/2.html", volume.chapters[1].url)
    }
}
