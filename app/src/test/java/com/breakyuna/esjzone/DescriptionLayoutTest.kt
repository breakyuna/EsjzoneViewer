package com.breakyuna.esjzone

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.novellibrary.component.FuriganaTextStyle
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.novellibrary.novel.analyseDescription
import com.breakyuna.esjzone.novellibrary.novel.copyText
import com.breakyuna.esjzone.novellibrary.novel.preview
import com.breakyuna.esjzone.ui.component.descriptionDisplayText
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionLayoutTest {
    @Test
    fun largeQuestionAndAnswerKeepFontWeightLineSpacingAndBlankParagraph() {
        val description = analyseDescription(Jsoup.parseBodyFragment(
            """<div class="description"><p><strong><span style="font-size:30px;">Q：这是需要换行的长问题。</span></strong></p><p><br></p><p><strong><span style="font-size:30px;">A：回答。</span></strong></p></div>"""
        ).selectFirst(".description")!!)

        val texts = description.components.filterIsInstance<TextComponent>()
        assertEquals(3, texts.size)
        val question = descriptionDisplayText(texts.first().toAnnotatedString())
        assertEquals(45.sp, question.paragraphStyles.single().item.lineHeight)
        assertTrue(question.spanStyles.any { it.item.fontSize == 30.sp && it.item.fontWeight == FontWeight.Bold })
        assertEquals("", descriptionDisplayText(texts[1].toAnnotatedString()).text)
        assertTrue(description.paragraphFormats.all { it.spaceAfter })
        assertEquals("Q：这是需要换行的长问题。 A：回答。", description.preview())
    }

    @Test
    fun centeredItalicIntroductionKeepsExplicitBreakAndParagraphAlignment() {
        val description = analyseDescription(Jsoup.parseBodyFragment(
            """<div class="description"><p style="text-align:center;"><strong><em><span style="font-size:24px;">生活并不愉快。<br>但你不能死。</span></em></strong></p><p style="text-align:center;"><br></p></div>"""
        ).selectFirst(".description")!!)

        assertTrue(description.paragraphFormats.all { it.alignment == "center" && it.spaceAfter })
        val text = descriptionDisplayText((description.components.first() as TextComponent).toAnnotatedString())
        assertEquals("生活并不愉快。\n但你不能死。", text.text)
        assertEquals(2, text.paragraphStyles.size)
        assertTrue(text.paragraphStyles.all { it.item.lineHeight == 36.sp })
        assertTrue(text.spanStyles.any { it.item.fontStyle == FontStyle.Italic && it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun inlineContentRetainsEnoughVerticalSpace() {
        val text = descriptionDisplayText(AnnotatedString("注音"), inlineHeight = 48f)
        assertEquals(48.sp, text.paragraphStyles.single().item.lineHeight)
    }

    private fun parseDescription(html: String): NovelDescription =
        analyseDescription(Jsoup.parse("<div>$html</div>").selectFirst("body > div")!!)

    @Test
    fun keepsParagraphsAndLineBreaksThatPreviewFlattens() {
        val description = parseDescription(
            "<p><strong>小說簡介：</strong></p><p>第一行<br>第二行 </p><p>第三段</p>"
        )

        assertEquals("小說簡介：\n第一行\n第二行\n第三段", description.copyText())
        assertEquals("第一行 第二行 第三段", description.preview(Int.MAX_VALUE))
    }

    @Test
    fun collapsesEmptyParagraphRunsAndDropsTrailingBreaks() {
        val description = parseDescription(
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
}
