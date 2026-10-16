package com.breakyuna.esjzone

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.analyseDescription
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
}
