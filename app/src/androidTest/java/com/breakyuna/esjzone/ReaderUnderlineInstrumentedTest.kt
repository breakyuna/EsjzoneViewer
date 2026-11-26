package com.breakyuna.esjzone

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.domain.reader.ReaderLink
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.ui.page.ReaderUnderlinesModel
import com.breakyuna.esjzone.ui.reader.ReaderShell
import com.breakyuna.esjzone.ui.reader.ReaderUnderlineText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderUnderlineInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun corruptChapterDoesNotStopObservationOfValidChapterUpdates() {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),
            GeneralDatabase::class.java).build()
        val model = ReaderUnderlinesModel(database)
        val owner = ViewModelStore().apply { put("underlines", model) }
        val key = ReaderUnderlines.KEY_PREFIX + "good"
        val mark = ReaderUnderline(0, "a".repeat(64), 0, 5)
        var observed = emptyMap<String, List<ReaderUnderline>>()
        try {
            runBlocking(Dispatchers.IO) {
                database.cacheDao().putAtomic(ReaderUnderlines.KEY_PREFIX + "bad", "null")
                database.cacheDao().putAtomic(key, ReaderUnderlines.encode(listOf(mark)))
            }
            composeRule.setContent {
                val rows by model.underlines.collectAsStateWithLifecycle()
                observed = rows
            }
            composeRule.waitUntil(5_000) { observed["good"] == listOf(mark) }
            composeRule.runOnIdle { assertTrue(observed["bad"].orEmpty().isEmpty()) }
            val updated = mark.copy(end = 8)
            runBlocking { withContext(Dispatchers.IO) {
                database.cacheDao().putAtomic(key, ReaderUnderlines.encode(listOf(updated)))
            } }
            composeRule.waitUntil(5_000) { observed["good"] == listOf(updated) }
        } finally {
            composeRule.runOnIdle { owner.clear() }
            database.close()
        }
    }

    @Test fun longPressSavesWithoutTurningPageOrOpeningToolbarAndRepeatedPressKeepsUnderline() {
        var marks by mutableStateOf(emptyList<ReaderUnderline>())
        var taps = 0
        lateinit var pagerState: PagerState
        composeRule.setContent {
            pagerState = rememberPagerState { 2 }
            MaterialTheme {
                ReaderShell(background = Color.White, onReadingAreaTap = { _, _ -> taps++ },
                    pagedGesturesEnabled = true) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        if (page == 0) {
                            ReaderUnderlineText(AnnotatedString("Hello reader"), emptyMap(), TextStyle(fontSize = 22.sp),
                                Color.Black, Modifier.testTag("text"), true, 2, "a".repeat(64), offset = 10,
                                underlines = marks, onUnderline = { mark, remove ->
                                    marks = ReaderUnderlines.update(marks, mark, remove)
                                })
                        } else Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        composeRule.onNodeWithTag("text").performTouchInput { longClick(center) }
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(1, marks.size)
            assertEquals(2, marks.single().blockIndex)
            assertTrue(marks.single().start >= 10)
            assertEquals(0, taps)
            assertEquals(0, pagerState.currentPage)
        }
        composeRule.onNodeWithTag("text").performTouchInput { longClick(center) }
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(1, marks.size)
            assertEquals(0, taps)
            assertEquals(0, pagerState.currentPage)
        }
    }

    @Test fun tappingSavedUnderlineOffersRemovalEvenWhenCreationIsDisabled() {
        val mark = ReaderUnderline(0, "a".repeat(64), 1, 3)
        var marks by mutableStateOf(listOf(mark))
        var taps = 0
        composeRule.setContent {
            MaterialTheme {
                ReaderShell(background = Color.White, onReadingAreaTap = { _, _ -> taps++ }) {
                    ReaderUnderlineText(AnnotatedString("甲乙丙丁"), emptyMap(), TextStyle(fontSize = 22.sp),
                        Color.Black, Modifier.testTag("text"), false, 0, "a".repeat(64),
                        underlines = marks, onUnderline = { selected, remove ->
                            marks = ReaderUnderlines.update(marks, selected, remove)
                        })
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        val node = composeRule.onNodeWithTag("text")
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        node.performTouchInput { click(layouts.single().getBoundingBox(1).center) }
        composeRule.onNode(isPopup()).assertExists()
        composeRule.runOnIdle { assertEquals(listOf(mark), marks); assertEquals(0, taps) }
        composeRule.onNode(hasClickAction() and hasAnyAncestor(isPopup())).performClick()
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.runOnIdle { assertTrue(marks.isEmpty()); assertEquals(0, taps) }
        node.performTouchInput { click(layouts.single().getBoundingBox(1).center) }
        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun tappingOnlyTheMarkedPartOfALinkOffersBothActions() {
        val text = "甲乙丙丁戊己"
        val prefix = "上一页"
        val offset = prefix.length
        val offsets = ReaderTextOffsets.identity(prefix + text)
        val signature = "a".repeat(64)
        val mark = ReaderUnderline(0, signature, offset, offset + 3)
        val url = "https://example.com/reference"
        val links = listOf(ReaderLink(offset + 1, offset + 5, url))
        var marks by mutableStateOf(listOf(mark))
        val opened = mutableListOf<String>()
        var taps = 0
        composeRule.setContent {
            MaterialTheme {
                ReaderShell(background = Color.White, onReadingAreaTap = { _, _ -> taps++ }) {
                    ReaderUnderlineText(AnnotatedString(text), emptyMap(), TextStyle(fontSize = 22.sp),
                        Color.Black, Modifier.testTag("linked-text"), false, 0, signature, offset = offset,
                        underlines = marks, onUnderline = { selected, remove ->
                            marks = ReaderUnderlines.update(marks, selected, remove)
                        }, links = links, linkOffsets = offsets, onLinkClick = { opened.add(it) })
                }
            }
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val popupAction = hasClickAction() and hasAnyAncestor(isPopup())
        val linkAction = popupAction and hasText(context.getString(R.string.reader_visit_link))
        val removeAction = popupAction and hasText(context.getString(R.string.reader_remove_underline))
        val node = composeRule.onNodeWithTag("linked-text")
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()

        node.performTouchInput { click(layout.getBoundingBox(3).center) }
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(listOf(url), opened) }

        val overlap = layout.getBoundingBox(1).center
        node.performTouchInput { click(overlap) }
        composeRule.onAllNodes(popupAction).assertCountEquals(2)
        composeRule.runOnIdle { assertEquals(listOf(url), opened) }
        composeRule.onNode(linkAction).performClick()
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(listOf(url, url), opened)
            assertEquals(listOf(mark), marks)
        }

        node.performTouchInput { click(overlap) }
        composeRule.onNode(removeAction).performClick()
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(listOf(url, url), opened)
            assertTrue(marks.isEmpty())
            assertEquals(0, taps)
        }
    }
}
