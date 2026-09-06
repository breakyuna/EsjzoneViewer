package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.parseNovelCard
import com.breakyuna.esjzone.network.features.NovelCardLayout
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelCardParserTest {
    @Test
    fun parsesSharedFieldsAndCountTextWithoutChangingR18Override() {
        val card = Jsoup.parse(
            """<div class="card"><img data-src="/cover.jpg"><span class="ignored"></span></div>""",
            "https://www.esjzone.cc/"
        ).selectFirst("div")!!
        val novel = parseNovelCard(card, "小說", "/detail/42.html", "觀看：1,234", "收藏：56", true)
        assertEquals("小說", novel.name)
        assertEquals("/detail/42.html", novel.url)
        assertEquals(1234, novel.views)
        assertEquals(56, novel.likes)
        assertTrue(novel.isAdult)
    }

    @Test
    fun parsesHomeLatestChapterAndKeepsListOnlyMetadataAbsent() {
        val card = Jsoup.parse(
            """
            <div class="card mb-30">
                <div class="card-img-tiles"><a href="/detail/42.html"><img data-src="/cover.jpg"></a></div>
                <div><h5 class="card-title"><a href="/detail/42.html">小說</a></h5></div>
                <div class="card-ep"><a href="/forum/42/7.html">第 7 章</a></div>
                <div class="card-other"><span data-feather="eye"></span> 1,234</div>
                <div class="card-other"><span data-feather="heart"></span> 56</div>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst(".card")!!

        val novel = parseNovelCard(card, false, NovelCardLayout.HOME)

        assertEquals("第 7 章", novel.latestTitle)
        assertEquals("/forum/42/7.html", novel.latestUrl)
        assertEquals(1234, novel.views)
        assertEquals(56, novel.likes)
        assertNull(novel.author)
        assertNull(novel.words)
        assertNull(novel.articleCount)
        assertNull(novel.discussionCount)
    }

    @Test
    fun parsesListMetadataAcrossMultipleCardOtherBlocks() {
        val card = Jsoup.parse(
            """
            <div class="card mb-30">
                <div class="card-img-tiles"><a href="/detail/42.html"><img data-src="/cover.jpg"></a></div>
                <div><h5 class="card-title"><a href="/detail/42.html">小說</a></h5></div>
                <div class="card-ep"><a href="/forum/42/7.html">第 7 章</a></div>
                <div class="card-author"><a href="/tags/author/">作者</a></div>
                <div class="card-other"><span data-feather="file-text"></span> 205,943</div>
                <div class="card-other"><span data-feather="eye"></span> 1,234 <span data-feather="heart"></span> 56</div>
                <div class="card-other"><span data-feather="feather"></span> 480 <span data-feather="message-square"></span> 1023</div>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst(".card")!!

        val novel = parseNovelCard(card, false, NovelCardLayout.LIST)

        assertEquals("作者", novel.author)
        assertEquals("/tags/author/", novel.authorUrl)
        assertEquals(205943, novel.words)
        assertEquals(480, novel.articleCount)
        assertEquals(1023, novel.discussionCount)
        assertEquals(1234, novel.views)
        assertEquals(56, novel.likes)
    }
}
