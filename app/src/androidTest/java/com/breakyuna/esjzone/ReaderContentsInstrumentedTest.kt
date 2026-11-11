package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.page.ReaderContentsContent
import com.breakyuna.esjzone.ui.page.chapterIdentity
import com.breakyuna.esjzone.ui.reader.ReaderSidePanel
import com.breakyuna.esjzone.ui.reader.ReaderSidePanels
import com.breakyuna.esjzone.ui.reader.ReaderSidePanelsState
import com.breakyuna.esjzone.ui.reader.rememberReaderSidePanelsState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderContentsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun swipingInsideOpenPanelSwitchesTabsAndEntriesOpenTheirChapterOrRange() {
        val first = Chapter("第一章", "https://example.test/forum/123/1.html", true)
        val second = Chapter("第二章", "https://example.test/forum/123/2.html", true)
        val mark = ReaderUnderline(2, "a".repeat(64), 1, 4, "自由划线")
        var opened: Chapter? = null
        var selected: ReaderUnderline? = null
        lateinit var panels: ReaderSidePanelsState
        composeRule.setContent {
            MaterialTheme {
                panels = rememberReaderSidePanelsState()
                LaunchedEffect(panels) { panels.open(ReaderSidePanel.CONTENTS) }
                ReaderSidePanels(panels, true, false, {}, contents = {
                    ReaderContentsContent(listOf(first, second), first, "123", listOf(
                        Bookmark(second.url, "123", "本书", second.name),
                        Bookmark("https://example.test/forum/456/3.html", "456", "其他书", "其他书章节")
                    ), mapOf(chapterIdentity(second) to listOf(mark)),
                        onChapterSelected = { opened = it },
                        onUnderlineSelected = { chapter, underline -> opened = chapter; selected = underline },
                        onDismiss = panels::close)
                }, comments = {}) { Box(Modifier.fillMaxSize().testTag("reader")) }
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.reader_contents))
            .performTouchInput { swipeLeft() }
        composeRule.onNodeWithText(context.getString(R.string.reader_bookmark_action)).assertIsSelected()
        composeRule.onNodeWithText("其他书章节").assertDoesNotExist()
        composeRule.onNodeWithText(second.name).performClick()
        composeRule.runOnIdle { assertEquals(second, opened); assertTrue(panels.isOpen) }
        composeRule.onNodeWithContentDescription(context.getString(R.string.reader_bookmark_action))
            .performTouchInput { swipeLeft() }
        composeRule.onNodeWithText(context.getString(R.string.reader_underline_tab)).assertIsSelected()
        composeRule.onNodeWithText(mark.quote!!).performClick()
        composeRule.runOnIdle { assertEquals(second, opened); assertEquals(mark, selected); assertTrue(panels.isOpen) }
        composeRule.onNodeWithContentDescription(context.getString(R.string.reader_underline_tab))
            .performTouchInput { swipeRight() }
        composeRule.onNodeWithText(context.getString(R.string.reader_bookmark_action)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.reader_contents)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.reader_contents)).assertIsSelected()
    }

    @Test fun bookmarkTabShowsAndOpensEachParagraphOfTheSameChapter() {
        val chapter = Chapter("Chapter 1", "https://www.esjzone.cc/forum/123/1.html", true)
        var opened: Chapter? = null
        var selected: Bookmark? = null
        val first = Bookmark(chapter.url, "123", "Novel", chapter.name,
            anchor = ReaderAnchor.encode(ReaderAnchor(chapterIdentity(chapter), "a".repeat(64), 1)), excerpt = "First paragraph")
        val second = Bookmark("https://www.esjzone.one/forum/123/1.html", "123", "Novel", chapter.name,
            anchor = ReaderAnchor.encode(ReaderAnchor(chapterIdentity(chapter), "a".repeat(64), 5)), excerpt = "Second paragraph")
        composeRule.setContent {
            MaterialTheme {
                ReaderContentsContent(listOf(chapter), chapter, "123", listOf(first, second), emptyMap(),
                    { opened = it }, { _, _ -> }, {}, onBookmarkSelected = { target, bookmark ->
                        opened = target
                        selected = bookmark
                    })
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.reader_bookmark_action)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Second paragraph").fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithText("First paragraph").assertIsDisplayed()
        composeRule.onNodeWithText("Second paragraph").performClick()
        composeRule.runOnIdle { assertEquals(chapter, opened); assertEquals(second, selected) }
    }

    @Test fun emptyPagesStillAcceptHorizontalSwipes() {
        val chapter = Chapter("第一章", "https://example.test/forum/123/1.html", true)
        composeRule.setContent {
            MaterialTheme {
                ReaderContentsContent(listOf(chapter), chapter, "123", emptyList(), emptyMap(), {}, { _, _ -> }, {})
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.reader_bookmark_action)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.bookmarks_empty)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reader_bookmark_action))
            .performTouchInput { swipeLeft() }
        composeRule.onNodeWithText(context.getString(R.string.reader_underline_tab)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.reader_underlines_empty)).assertIsDisplayed()
    }
}
