package com.breakyuna.esjzone

import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.ui.reader.readerChapterLinks
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLinksTest {
    private val base = "https://www.esjzone.cc/forum/1/2.html"

    @Test
    fun originalHtmlRestoresAnchorsForStructuredAndDownloadedBodies() {
        val html = """<p>前言 <a href="/detail/123.html"><strong>小说</strong>详情</a>，
            网址 www.example.org/info。 <a href="https://target.example/path">https://label.example</a></p>""".trimIndent()
        val components = analyseComponents(Jsoup.parseBodyFragment(html, base).body())
        // The persisted body deliberately does not include href metadata.
        val body = ChapterBody.from(components).validate()
        val blocks = body.readerBlocks()
        val text = blocks.single().sourceText()
        val links = readerChapterLinks(blocks, html, base).getValue(0)
        assertEquals(listOf("小说详情", "www.example.org/info", "https://label.example"),
            links.map { text.substring(it.start, it.end) })
        assertEquals(listOf("https://www.esjzone.cc/detail/123.html", "https://www.example.org/info",
            "https://target.example/path"), links.map { it.url })
        assertEquals(body.fingerprint, ChapterBody.fromBlocks(blocks).fingerprint)
    }

    @Test
    fun unsafeAndMismatchedHtmlCannotLinkUnrelatedText() {
        val unsafe = """<p><a href="javascript:alert(1)">不可打开</a>
            <a href="https://user:pass@example.com/">凭据</a></p>"""
        val parsed = ChapterBody.from(analyseComponents(Jsoup.parseBodyFragment(unsafe, base).body())).readerBlocks()
        assertTrue(readerChapterLinks(parsed, unsafe, base).isEmpty())

        val blocks = listOf(ReaderBlock.Text("无关文字 www.example.org"))
        val result = readerChapterLinks(blocks, """<p><a href="/detail/99.html">无关文字</a></p>""", base)
        val link = result.getValue(0).single()
        assertEquals("www.example.org", blocks.single().sourceText().substring(link.start, link.end))
        assertEquals("https://www.example.org/", link.url)
    }
}
