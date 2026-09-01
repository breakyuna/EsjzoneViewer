package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.parseNovelCard
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
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
}
