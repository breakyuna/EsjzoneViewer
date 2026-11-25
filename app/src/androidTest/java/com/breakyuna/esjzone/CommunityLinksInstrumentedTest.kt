@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.breakyuna.esjzone

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.ui.page.buildCommunityLinkedText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunityLinksInstrumentedTest {
    @Test
    fun relativeHtmlLinkKeepsItsTargetAndBareWwwIsAddedOnce() {
        val site = EsjzoneUrls.Base
        val base = "$site/forum/1585405223/409387.html"
        val html = """<p><a href="/detail/123.html">https://example.com/title</a> 和 www.example.org/info</p>"""
        val text = "https://example.com/title 和 www.example.org/info"
        val result = buildCommunityLinkedText(text, html, base, TextLinkStyles(), LinkInteractionListener { })

        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(listOf("$site/detail/123.html", "https://www.example.org/info"),
            links.map { (it.item as LinkAnnotation.Url).url })
        assertEquals("https://example.com/title", result.text.substring(links.first().start, links.first().end))
    }
}
