package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.parseNovelCard
import com.breakyuna.esjzone.network.features.NovelCardLayout
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Small, checked-in ESJ HTML samples that exercise the CSS parser boundary. */
class EsjHtmlParserFixtureTest {

    @Test
    fun novelCardFixture_keepsSemanticParserFieldsAligned() {
        val card = Jsoup.parse(
            """
            <div class="card mb-30">
              <div class="card-img-tiles"><a href="/detail/9001.html">
                <img src="/assets/img/empty_cover.jpg" data-src="/uploads/9001.jpg">
              </a></div>
              <div><h5 class="card-title"><a href="/detail/9001.html">Fixture Novel</a></h5></div>
              <div class="card-ep"><a href="/forum/9001/7.html">Chapter 7</a></div>
              <div class="card-author"><a href="/tags/author/">Fixture Author</a></div>
              <div class="card-other"><span data-feather="eye"></span> 1,234
                <span data-feather="heart"></span> 56</div>
              <div class="product-badge top">R18</div>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst(".card")!!

        assertEquals("Fixture Novel", card.selectFirst("h5.card-title a")?.text())
        assertEquals("/forum/9001/7.html", card.selectFirst(".card-ep a")?.attr("href"))
        assertEquals(1, card.select(".product-badge.top").size)

        val novel = parseNovelCard(card, r18 = false, layout = NovelCardLayout.LIST)
        assertEquals("Fixture Novel", novel.name)
        assertEquals(1234, novel.views)
        assertEquals(56, novel.likes)
        assertEquals("Fixture Author", novel.author)
        assertTrue(novel.isAdult)
    }

    @Test
    fun detailFixture_keepsStableChapterIntegrationMarker() {
        val document = Jsoup.parse(
            """
            <html><body>
              <div>header</div><div>banner</div>
              <div><section>
                <div><div id="integration">
                  <a href="/forum/9001/1.html" data-title="Chapter 1">Chapter 1</a>
                </div></div>
              </section></div>
            </body></html>
            """.trimIndent(),
            "https://www.esjzone.cc/detail/9001.html"
        )

        val integration = document.select("#integration")
        assertEquals(1, integration.size)
        assertEquals("integration", integration.single().id())
        assertEquals("Chapter 1", integration.single().text())
    }
}
