@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.breakyuna.esjzone

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.page.buildCommunityLinkedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunityLinkedTextInstrumentedTest {
    private val styles = TextLinkStyles(style = SpanStyle(color = Color.Blue))
    private val listener = LinkInteractionListener { }

    @Test fun htmlLinkKeepsItsTargetAndBareWwwIsAddedOnce() {
        val base = "https://www.esjzone.cc/forum/1585405223/409387.html"
        val html = """<p><a href="/detail/123.html">https://example.com/title</a> 和 www.example.org/info</p>"""
        val text = "https://example.com/title 和 www.example.org/info"
        val result = buildCommunityLinkedText(text, html, base, styles, listener)
        val links = result.getLinkAnnotations(0, result.length)

        assertEquals(2, links.size)
        assertEquals("https://www.esjzone.cc/detail/123.html", (links[0].item as LinkAnnotation.Url).url)
        assertEquals("https://www.example.org/info", (links[1].item as LinkAnnotation.Url).url)
        assertTrue(result.text.contains("https://example.com/title"))
    }

    @Test fun pendingCommentIsTreatedAsLiteralText() {
        val text = "示例 <a href=\"https://example.com\">文字</a> 和 www.example.org"
        val result = buildCommunityLinkedText(text, text, "", styles, listener)
        assertEquals(text, result.text)
        assertEquals(2, result.getLinkAnnotations(0, result.length).size)
    }
}
