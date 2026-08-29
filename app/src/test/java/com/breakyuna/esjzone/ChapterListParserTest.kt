package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.novel.analyseChapterList
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterListParserTest {

    @Test
    fun parsesCurrentFlatChapterListContainer() {
        val integration = Jsoup.parse(
            """
            <div id="integration" class="tab-pane fade active show">
                <div class="sp-buttons mb-3">
                    <button class="btn-sort" data-sort="1">正序</button>
                </div>
                <div id="chapterList">
                    <a href="https://www.esjzone.cc/forum/1772649515/491415.html"
                       target="_blank" data-title="0.地狱也是摇滚"><p class="active">0.地狱也是摇滚</p></a>
                    <a href="https://www.esjzone.cc/forum/1772649515/491431.html"
                       target="_blank" data-title="1.如果地狱也算摇滚，那你妈也算金子"><p>1.如果地狱也算摇滚，那你妈也算金子</p></a>
                    <a href="https://www.esjzone.cc/forum/1772649515/569111.html"
                       target="_blank" data-title="126.愛, 子でしょ "><p>126.愛, 子でしょ</p></a>
                </div>
            </div>
            """.trimIndent()
        ).selectFirst("#integration")

        assertNotNull(integration)
        val chapterList = analyseChapterList(integration!!)

        assertEquals(3, chapterList.orderedChapters.size)
        assertEquals(
            listOf("0.地狱也是摇滚", "1.如果地狱也算摇滚，那你妈也算金子", "126.愛, 子でしょ"),
            chapterList.orderedChapters.map { it.name }
        )
        assertEquals(
            "https://www.esjzone.cc/forum/1772649515/491415.html",
            chapterList.orderedChapters.first().url
        )
        assertEquals("1772649515", chapterList.orderedChapters.first().novelId())
        assertTrue(chapterList.orderedChapters.first().isHistory)
        assertEquals(chapterList.orderedChapters.first(), chapterList.toRead)
    }

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
        assertEquals(
            listOf("第一章", "第二章"),
            chapterList.orderedChapters.map { it.name }
        )
    }
}
