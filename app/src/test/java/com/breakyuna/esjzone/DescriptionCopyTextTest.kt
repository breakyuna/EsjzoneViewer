package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.component.FuriganaTextStyle
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.novellibrary.novel.analyseDescription
import com.breakyuna.esjzone.novellibrary.novel.copyText
import com.breakyuna.esjzone.novellibrary.novel.preview
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class DescriptionCopyTextTest {

    private fun parse(html: String): NovelDescription =
        analyseDescription(Jsoup.parse("<div>$html</div>").selectFirst("body > div")!!)

    @Test
    fun keepsParagraphsAndLineBreaksThatPreviewFlattens() {
        val description = parse(
            "<p><strong>小說簡介：</strong></p><p>第一行<br>第二行 </p><p>第三段</p>"
        )

        assertEquals("小說簡介：\n第一行\n第二行\n第三段", description.copyText())
        assertEquals("第一行 第二行 第三段", description.preview(Int.MAX_VALUE))
    }

    @Test
    fun collapsesEmptyParagraphRunsAndDropsTrailingBreaks() {
        val description = parse(
            "<p>開頭<br></p><p><br></p><p><br></p><p><br></p><p>結尾</p><p><br></p>"
        )

        assertEquals("開頭\n\n結尾", description.copyText())
    }

    @Test
    fun copiesRubyBaseTextAndSkipsImages() {
        val description = NovelDescription(
            listOf(
                ImageComponent("https://example.invalid/cover.jpg"),
                TextComponent("在").append(
                    TextComponent("英雄").style(FuriganaTextStyle(TextComponent("Hero")))
                ).append(TextComponent("番組")),
            )
        )

        assertEquals("在英雄番組", description.copyText())
    }

    @Test
    fun emptyDescriptionCopiesNothing() {
        assertEquals("", NovelDescription(emptyList()).copyText())
    }
}
