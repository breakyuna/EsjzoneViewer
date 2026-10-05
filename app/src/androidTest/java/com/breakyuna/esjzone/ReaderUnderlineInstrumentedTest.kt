package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.ui.page.ReaderUnderlinesModel
import com.breakyuna.esjzone.ui.reader.ReaderShell
import com.breakyuna.esjzone.ui.reader.ReaderUnderlineText
import com.breakyuna.esjzone.ui.reader.ReaderUnderlineSelection
import com.breakyuna.esjzone.ui.reader.LocalReaderUnderlineSelection
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

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
                val rows by model.underlines.collectAsState()
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

    @Test fun longPressDirectlyUnderlinesOneCharacterInRedWithoutPopup() {
        val text = "Line 1\nLine 2\nLine 3\nSelect target\nLine 5\nLine 6\nLine 7\nLine 8"
        var marks by mutableStateOf(emptyList<ReaderUnderline>())
        composeRule.setContent {
            MaterialTheme {
                ReaderUnderlineText(AnnotatedString(text), emptyMap(),
                    TextStyle(fontSize = 18.sp, lineHeight = 24.sp), Color.Black,
                    Modifier.testTag("text"), true, 0, "a".repeat(64),
                    underlines = marks, onUnderline = { mark, remove ->
                        marks = ReaderUnderlines.update(marks, mark, remove)
                    })
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag("text").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val bounds = layouts.single().getBoundingBox(text.indexOf("target"))
        composeRule.onNodeWithTag("text").performTouchInput { longClick(bounds.center) }
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.runOnIdle {
            val start = text.indexOf("target")
            assertEquals(listOf(ReaderUnderline(0, "a".repeat(64), start, start + 1)), marks)
        }
        val pixels = composeRule.onNodeWithTag("text").captureToImage().toPixelMap()
        assertTrue((0 until pixels.height).any { y -> (0 until pixels.width).any { x ->
            val color = pixels[x, y]
            color.red > 0.8f && color.green < 0.2f && color.blue < 0.2f
        } })
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

    @Test fun draggingCanEndInsideAWordReverseDirectionAndShrinkTheRange() {
        val text = "target\nreader"
        var marks by mutableStateOf(emptyList<ReaderUnderline>())
        composeRule.setContent {
            MaterialTheme {
                ReaderUnderlineText(AnnotatedString(text), emptyMap(), TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
                    Color.Black, Modifier.testTag("text"), true, 0, "a".repeat(64), offset = 10,
                    underlines = marks, onUnderline = { mark, remove ->
                        marks = ReaderUnderlines.update(marks, mark, remove)
                    })
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag("text").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        composeRule.onNodeWithTag("text").performTouchInput {
            down(layout.getBoundingBox(2).center)
            advanceEventTime(1000)
            moveTo(layout.getBoundingBox(10).center, delayMillis = 150)
            moveTo(layout.getBoundingBox(3).center, delayMillis = 150)
            up()
        }
        composeRule.runOnIdle { assertEquals(listOf(ReaderUnderline(0, "a".repeat(64), 12, 14)), marks) }
        composeRule.runOnIdle { marks = emptyList() }
        composeRule.onNodeWithTag("text").performTouchInput {
            down(layout.getBoundingBox(10).center)
            advanceEventTime(1000)
            moveTo(layout.getBoundingBox(1).center, delayMillis = 150)
            up()
        }
        composeRule.runOnIdle { assertEquals(listOf(ReaderUnderline(0, "a".repeat(64), 11, 21)), marks) }
    }

    @Test fun dragPreviewShrinksAndCancelsWithoutChangingTextLayoutOrSavedMarks() {
        val text = AnnotatedString("甲乙丙丁戊己庚辛\nabcdefgh")
        val saved = listOf(ReaderUnderline(0, "a".repeat(64), 0, 1))
        var layouts = 0
        var saves = 0
        composeRule.setContent {
            MaterialTheme {
                ReaderUnderlineText(text, emptyMap(), TextStyle(fontSize = 22.sp, lineHeight = 32.sp),
                    Color.Black, Modifier.background(Color.White).testTag("text"), true, 0, "a".repeat(64),
                    underlines = saved, onUnderline = { _, _ -> saves++ },
                    onLayout = { if (it != null) layouts++ })
            }
        }
        val node = composeRule.onNodeWithTag("text")
        val initialLayouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(initialLayouts) }
        val layout = initialLayouts.single()
        val initialLayoutCount = layouts
        fun redPixelCount(): Int {
            val pixels = node.captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red > 0.8f && color.green < 0.2f && color.blue < 0.2f) count++
            }
            return count
        }
        val savedPixels = redPixelCount()
        assertTrue(savedPixels > 0)
        node.performTouchInput { down(layout.getBoundingBox(2).center) }
        composeRule.mainClock.advanceTimeBy(1000)
        node.performTouchInput { moveTo(layout.getBoundingBox(5).center, delayMillis = 150) }
        val expandedPixels = redPixelCount()
        assertTrue(expandedPixels > savedPixels)
        node.performTouchInput { moveTo(layout.getBoundingBox(3).center, delayMillis = 150) }
        val shrunkPixels = redPixelCount()
        assertTrue(shrunkPixels > savedPixels && shrunkPixels < expandedPixels)
        val previewLayouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(previewLayouts) }
        assertEquals(layout, previewLayouts.single())
        node.performTouchInput { cancel() }
        assertEquals(savedPixels, redPixelCount())
        composeRule.runOnIdle {
            assertEquals(initialLayoutCount, layouts)
            assertEquals(0, saves)
        }
    }

    @Test fun dragAcrossParagraphsPreviewsBothAndCanShrinkBackBeforeSaving() {
        val selection = ReaderUnderlineSelection()
        var marks by mutableStateOf(emptyList<ReaderUnderline>())
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalReaderUnderlineSelection provides selection) {
                    Column {
                        listOf("甲乙丙丁戊己庚辛壬癸", "戊己庚辛").forEachIndexed { index, text ->
                            ReaderUnderlineText(AnnotatedString(text), emptyMap(),
                                TextStyle(fontSize = 22.sp, lineHeight = 32.sp), Color.Black,
                                Modifier.testTag("paragraph$index"), true, index, "a".repeat(64),
                                underlines = marks, onUnderline = { mark, remove ->
                                    marks = ReaderUnderlines.update(marks, mark, remove)
                                })
                        }
                    }
                }
            }
        }
        fun point(tag: String, index: Int): Offset {
            val node = composeRule.onNodeWithTag(tag)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return node.fetchSemanticsNode().boundsInRoot.topLeft + layouts.single().getBoundingBox(index).center
        }
        val first = composeRule.onNodeWithTag("paragraph0")
        val origin = first.fetchSemanticsNode().boundsInRoot.topLeft
        val start = point("paragraph0", 1) - origin
        val end = point("paragraph1", 2) - origin
        val trailingWhitespace = Offset((point("paragraph0", 8) - origin).x, end.y)
        first.performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(1000)
        first.performTouchInput { moveTo(trailingWhitespace, delayMillis = 150) }
        composeRule.runOnIdle {
            assertEquals(1, selection.target!!.first.block)
            assertEquals(setOf(TextRange(1, 10), TextRange(0, 4)), selection.ranges.values.toSet())
        }
        first.performTouchInput { moveTo(end, delayMillis = 150) }
        composeRule.runOnIdle {
            assertEquals(setOf(TextRange(1, 10), TextRange(0, 3)), selection.ranges.values.toSet())
            assertEquals(1, selection.target!!.first.block)
            assertTrue(marks.isEmpty())
        }
        first.performTouchInput { moveTo(start, delayMillis = 150) }
        composeRule.runOnIdle {
            assertEquals(listOf(TextRange(1, 2)), selection.ranges.values.toList())
            assertEquals(0, selection.target!!.first.block)
        }
        first.performTouchInput { moveTo(end, delayMillis = 150); up() }
        composeRule.runOnIdle {
            assertEquals(setOf(ReaderUnderline(0, "a".repeat(64), 1, 10),
                ReaderUnderline(1, "a".repeat(64), 0, 3)), marks.toSet())
            assertNull(selection.target)
            assertTrue(selection.ranges.isEmpty())
        }
    }

    @Test fun reverseDragAcrossPageFragmentsSavesFullParagraphOffsetsAsOneMark() {
        val selection = ReaderUnderlineSelection()
        val saved = mutableListOf<ReaderUnderline>()
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalReaderUnderlineSelection provides selection) {
                    Column {
                        listOf("甲乙丙丁", "戊己庚辛").forEachIndexed { index, text ->
                            ReaderUnderlineText(AnnotatedString(text), emptyMap(),
                                TextStyle(fontSize = 22.sp, lineHeight = 32.sp), Color.Black,
                                Modifier.testTag("fragment$index"), true, 3, "a".repeat(64), offset = 10 + index * 4,
                                underlines = emptyList(), onUnderline = { mark, _ -> saved += mark })
                        }
                    }
                }
            }
        }
        val first = composeRule.onNodeWithTag("fragment0")
        val last = composeRule.onNodeWithTag("fragment1")
        val layouts = mutableListOf<TextLayoutResult>()
        last.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val start = layouts.single().getBoundingBox(2).center
        layouts.clear()
        first.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val end = first.fetchSemanticsNode().boundsInRoot.topLeft + layouts.single().getBoundingBox(1).center -
            last.fetchSemanticsNode().boundsInRoot.topLeft
        last.performTouchInput {
            down(start)
            advanceEventTime(1000)
            moveTo(end, delayMillis = 150)
            up()
        }
        composeRule.runOnIdle {
            assertEquals(listOf(ReaderUnderline(3, "a".repeat(64), 11, 17)), saved)
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

    @Test fun savedUnderlineFollowsWrappingChangesAndMixedTextDirections() {
        val text = "甲乙丙 abc אבג דהו xyz\n丁戊己"
        var width by mutableStateOf(240.dp)
        composeRule.setContent {
            MaterialTheme {
                ReaderUnderlineText(AnnotatedString(text), emptyMap(),
                    TextStyle(fontSize = 22.sp, lineHeight = 32.sp), Color.Black,
                    Modifier.width(width).background(Color.White).testTag("text"), false, 0, "a".repeat(64),
                    underlines = listOf(ReaderUnderline(0, "a".repeat(64), 0, text.length)),
                    onUnderline = { _, _ -> })
            }
        }
        fun assertUnderlineMatchesLayout(): Int {
            val node = composeRule.onNodeWithTag("text")
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val pixels = node.captureToImage().toPixelMap()
            val lineOffset = with(composeRule.density) { 2.dp.toPx() }
            for (index in text.indices) {
                if (text[index] == '\n') continue
                val bounds = layout.getBoundingBox(index)
                if (bounds.width <= 1f) continue
                val x = bounds.center.x.roundToInt().coerceIn(0, pixels.width - 1)
                val y = (layout.getLineBaseline(layout.getLineForOffset(index)) + lineOffset)
                    .roundToInt().coerceIn(0, pixels.height - 1)
                val color = pixels[x, y]
                assertTrue("Missing underline at character $index",
                    color.red > 0.8f && color.green < 0.2f && color.blue < 0.2f)
            }
            return layout.lineCount
        }
        val wideLines = assertUnderlineMatchesLayout()
        composeRule.runOnIdle { width = 120.dp }
        assertTrue(assertUnderlineMatchesLayout() > wideLines)
    }
}
