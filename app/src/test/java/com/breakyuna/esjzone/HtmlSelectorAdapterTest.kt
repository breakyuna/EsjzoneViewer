package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import com.breakyuna.esjzone.network.features.NovelCardLayout
import com.breakyuna.esjzone.network.features.parseNovelCard
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Contract tests for the selector boundary used by the incremental parser migration. */
class HtmlSelectorAdapterTest {

    @Test
    fun jsoupAdapter_preservesSemanticSelectionAndParserFields() {
        val card = Jsoup.parse(
            """
            <div class="card mb-30">
              <h5 class="card-title"><a href="/detail/42.html">Adapter Novel</a></h5>
              <div class="card-other"><span data-feather="eye"></span> 321</div>
              <div class="card-other"><span data-feather="heart"></span> 12</div>
              <div class="product-badge top">R18</div>
            </div>
            """.trimIndent()
        ).selectFirst(".card")!!

        val selector: HtmlSelector = JsoupHtmlSelector
        assertEquals("Adapter Novel", selector.first(card, ".card-title a")?.text())
        assertEquals(2, selector.select(card, ".card-other").size)
        assertNotNull(selector.first(card, ".product-badge.top"))

        val novel = parseNovelCard(
            card = card,
            r18 = false,
            layout = NovelCardLayout.LIST,
            selector = selector
        )
        assertEquals("Adapter Novel", novel.name)
        assertEquals(321, novel.views)
        assertEquals(12, novel.likes)
        assertEquals("/detail/42.html", novel.url)
        assertEquals(true, novel.isAdult)
    }

    @Test
    fun adapter_contract_canBeReplacedWithoutChangingParserCallShape() {
        val document = Jsoup.parse("<div class='card'><a class='title'>Fixture</a></div>")
        val recording = RecordingSelector()

        assertEquals("Fixture", recording.first(document, ".title")?.text())
        assertEquals(listOf(".title"), recording.queries)
    }

    private class RecordingSelector : HtmlSelector {
        private val delegate = JsoupHtmlSelector
        val queries = mutableListOf<String>()

        override fun select(root: Element, selector: String): List<Element> {
            queries += selector
            return delegate.select(root, selector)
        }
    }
}
