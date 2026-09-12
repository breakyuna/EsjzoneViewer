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

    @Test
    fun detailFixture_parsesContentTagsFromWidgetAndFallback() {
        val html = """
            <html><body>
              <section class="widget widget-tags hidden-lg-up mt-30">
                <h3 class="widget-title">內容標籤</h3>
                <a class="tag" href="/tags/R18/">R18</a>
                <a class="tag" href="/tags/%E6%A0%A1%E5%9C%92/">校園</a>
                <a class="tag" href="/tags/%E6%88%80%E6%84%9B/">戀愛</a>
              </section>
              <div class="hidden-xs hidden-sm col-xl-3 col-lg-4">
                <section class="widget widget-tags m-t-20">
                  <h3 class="widget-title">內容標籤</h3>
                  <a class="tag" href="/tags/R18/">R18</a>
                  <a class="tag" href="/tags/%E6%A0%A1%E5%9C%92/">校園</a>
                </section>
              </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://www.esjzone.cc/detail/9001.html")
        val selector = ".widget-tags a, .widget-tags a.tag, a.tag[href*='/tags/'], .book-detail .tags a, .book-tags a, .tags a, [data-tags] a"
        val tags = document.select(selector).map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        assertEquals(listOf("R18", "校園", "戀愛"), tags)
    }
}
