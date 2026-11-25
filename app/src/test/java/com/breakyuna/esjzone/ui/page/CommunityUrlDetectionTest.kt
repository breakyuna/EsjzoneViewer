@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.breakyuna.esjzone.ui.page

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityUrlDetectionTest {
    private val linkStyles = TextLinkStyles(style = SpanStyle(color = Color.Blue))
    private val linkListener = LinkInteractionListener { }

    private fun detected(text: String): List<Pair<String, String>> = findCommunityUrls(text).map {
        text.substring(it.start, it.end) to it.url
    }

    @Test fun recognizesWebAndWwwPrefixesWithoutEatingSentencePunctuation() {
        val text = "看https://example.com/path，或（www.Example.com/page）。"
        assertEquals(
            listOf(
                "https://example.com/path" to "https://example.com/path",
                "www.Example.com/page" to "https://www.example.com/page"
            ),
            detected(text)
        )
    }

    @Test fun keepsQueryAndBalancedBracketsButRemovesTheSurroundingPunctuation() {
        val text = "(https://example.com/wiki/Example_(film)?id=1&sort=2), http://example.com/a!"
        assertEquals(
            listOf(
                "https://example.com/wiki/Example_(film)?id=1&sort=2" to
                    "https://example.com/wiki/Example_(film)?id=1&sort=2",
                "http://example.com/a" to "http://example.com/a"
            ),
            detected(text)
        )
    }

    @Test fun keepsUnicodePathAndFragments() {
        val text = "https://example.com/小说#目录"
        val match = findCommunityUrls(text).single()
        assertEquals(text, text.substring(match.start, match.end))
        assertTrue(match.url.startsWith("https://example.com/"))
        assertTrue(match.url.contains("#"))
    }

    @Test fun stopsAtBackticksEllipsesAndNonBreakingSpaces() {
        val text = "`https://example.com/guide` www.example.org/page……下一步 https://example.net/a\u00a0尾注"
        assertEquals(
            listOf(
                "https://example.com/guide" to "https://example.com/guide",
                "www.example.org/page" to "https://www.example.org/page",
                "https://example.net/a" to "https://example.net/a"
            ),
            detected(text)
        )
    }

    @Test fun ignoresEmailFragmentsAndAddressesWithCredentials() {
        val text = "user@www.example.com abcwww.example.com https://user:pass@example.com/path ftp://www.example.com/file"
        assertTrue(findCommunityUrls(text).isEmpty())
    }

    @Test fun multipleLinksAndUnmatchedBracketsAreHandledIndependently() {
        val text = "[www.example.com/a] https://example.org/b。"
        assertEquals(
            listOf(
                "www.example.com/a" to "https://www.example.com/a",
                "https://example.org/b" to "https://example.org/b"
            ),
            detected(text)
        )
    }

    @Test fun htmlLinkKeepsItsTargetAndBareWwwIsAddedOnce() {
        val base = "https://www.esjzone.cc/forum/1585405223/409387.html"
        val html = """<p><a href="/detail/123.html">https://example.com/title</a> 和 www.example.org/info</p>"""
        val text = "https://example.com/title 和 www.example.org/info"
        val result = buildCommunityLinkedText(text, html, base, linkStyles, linkListener)
        val links = result.getLinkAnnotations(0, result.length)

        assertEquals(2, links.size)
        assertEquals("https://www.esjzone.cc/detail/123.html", (links[0].item as LinkAnnotation.Url).url)
        assertEquals("https://www.example.org/info", (links[1].item as LinkAnnotation.Url).url)
        assertTrue(result.text.contains("https://example.com/title"))
    }

    @Test fun pendingCommentIsTreatedAsLiteralText() {
        val text = "示例 <a href=\"https://example.com\">文字</a> 和 www.example.org"
        val result = buildCommunityLinkedText(text, text, "", linkStyles, linkListener)
        assertEquals(text, result.text)
        assertEquals(2, result.getLinkAnnotations(0, result.length).size)
    }
}
