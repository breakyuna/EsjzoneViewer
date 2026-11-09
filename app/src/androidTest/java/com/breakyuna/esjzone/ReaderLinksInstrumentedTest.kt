@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderLink
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets
import com.breakyuna.esjzone.ui.reader.ReaderBlocks
import com.breakyuna.esjzone.ui.reader.ReaderPage
import com.breakyuna.esjzone.ui.reader.ReaderPageContent
import com.breakyuna.esjzone.ui.reader.ReaderPageSegment
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.reader.ReaderShell
import com.breakyuna.esjzone.ui.reader.addReaderLinks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderLinksInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun transformedAndPaginatedRangesKeepTheOriginalDestination() {
        val offsets = ReaderTextOffsets("甲乙名链接", intArrayOf(0, 0, 1, 2, 3),
            intArrayOf(1, 1, 2, 3, 4), 4)
        val target = "https://example.com/"
        val result = addReaderLinks(AnnotatedString("链接"), listOf(ReaderLink(2, 4, target)),
            offsets, segmentStart = 3, listener = LinkInteractionListener { })
        val link = result.getLinkAnnotations(0, result.length).single()
        assertEquals(0, link.start)
        assertEquals(2, link.end)
        assertEquals(target, (link.item as LinkAnnotation.Url).url)
    }

    @Test fun scrollingLinksConsumeTapBeforeReaderControls() = verifyLinkTap(paged = false)
    @Test fun pagedLinksConsumeTapBeforeReaderControls() = verifyLinkTap(paged = true)

    private fun verifyLinkTap(paged: Boolean) {
        val target = "https://example.com/"
        val text = "查看链接"
        val links = mapOf(0 to listOf(ReaderLink(0, text.length, target)))
        val offsets = ReaderTextOffsets.identity(text)
        val underlineEnabled = mutableStateOf(false)
        var opened = ""
        var readerTaps = 0
        composeRule.setContent {
            val settings = ReaderSettings(longPressUnderline = underlineEnabled.value)
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            Box(Modifier.size(280.dp)) {
                ReaderShell(background = Color.White, pagedGesturesEnabled = paged,
                    onReadingAreaTap = { _, _ -> readerTaps++ }) {
                    if (paged) {
                        val page = ReaderPage(listOf(ReaderPageSegment.TextLine(
                            AnnotatedString(text), emptyMap(), 0.dp)), 0f)
                        ReaderPageContent(page, settings, TextStyle(fontSize = 20.sp), Color.Black,
                            { it }, 200.dp, textOffsets = { offsets }, links = links,
                            onLinkClick = { opened = it })
                    } else {
                        ReaderBlocks(listOf(ReaderBlock.Text(text)), settings, measurer, density, Color.Black,
                            links = links, onLinkClick = { opened = it })
                    }
                }
            }
        }
        repeat(2) { index ->
            composeRule.runOnIdle { underlineEnabled.value = index == 1; opened = "" }
            composeRule.onNodeWithText(text).performTouchInput { click() }
            composeRule.runOnIdle {
                assertEquals(target, opened)
                assertEquals(0, readerTaps)
            }
        }
    }
}
