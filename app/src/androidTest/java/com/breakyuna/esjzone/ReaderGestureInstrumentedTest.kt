package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.reader.ReaderShell
import com.breakyuna.esjzone.ui.reader.ReaderSidePanel
import com.breakyuna.esjzone.ui.reader.ReaderSidePanels
import com.breakyuna.esjzone.ui.reader.ReaderSidePanelsState
import com.breakyuna.esjzone.ui.reader.rememberReaderSidePanelsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderGestureInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun verticalReadingLocksOutSidePanelsEvenWhenTheFingerTurnsSideways() {
        lateinit var listState: LazyListState
        lateinit var panels: ReaderSidePanelsState
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            listState = rememberLazyListState()
            panels = rememberReaderSidePanelsState()
            Box(Modifier.size(240.dp).testTag("reader")) {
                ReaderSidePanels(panels, true, true, {}, {}, {}) {
                    ReaderShell(background = Color.White, onReadingAreaTap = { _, _ -> }) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(30) { Box(Modifier.height(64.dp)) }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("reader").performTouchInput {
            down(center + Offset(0f, 60f) * density)
            moveTo(center - Offset(0f, 40f) * density, delayMillis = 200)
        }
        // Vertical reading follows the finger before release.
        composeRule.runOnIdle {
            assertTrue(listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
            assertEquals(null, panels.panel)
        }
        composeRule.onNodeWithTag("reader").performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader").performTouchInput {
            val start = center + Offset(-60f, 60f) * density
            down(start)
            moveTo(start + Offset(20f, -15f) * density, delayMillis = 50)
            moveTo(start + Offset(120f, -25f) * density, delayMillis = 150)
            up()
        }
        composeRule.runOnIdle { assertEquals(null, panels.panel) }
    }

    @Test
    fun actualSidePanelContentFollowsDragAndSettlesOnBothSides() {
        lateinit var panels: ReaderSidePanelsState
        lateinit var readerList: LazyListState
        lateinit var panelList: LazyListState
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            panels = rememberReaderSidePanelsState()
            readerList = rememberLazyListState()
            panelList = rememberLazyListState()
            Box(Modifier.size(240.dp).testTag("reader")) {
                ReaderSidePanels(
                    panels, true, true, {},
                    contents = {
                        LazyColumn(state = panelList, modifier = Modifier.fillMaxSize().testTag("contents")) {
                            items(30) { Box(Modifier.height(64.dp)) }
                        }
                    },
                    comments = { Box(Modifier.fillMaxSize().testTag("comments")) }
                ) {
                    ReaderShell(background = Color.White, onReadingAreaTap = { _, _ -> }) {
                        LazyColumn(state = readerList, modifier = Modifier.fillMaxSize()) {
                            items(30) { Box(Modifier.height(64.dp)) }
                        }
                    }
                }
            }
        }
        for ((side, direction, tag) in listOf(
            Triple(ReaderSidePanel.CONTENTS, 1f, "contents"),
            Triple(ReaderSidePanel.COMMENTS, -1f, "comments")
        )) {
            composeRule.onNodeWithTag("reader").performTouchInput {
                down(center - Offset(60f * direction, 0f) * density)
                moveTo(center - Offset(10f * direction, 0f) * density, delayMillis = 150)
            }
            val firstWidth = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.width
            composeRule.runOnIdle {
                assertEquals(side, panels.panel)
                assertTrue(panels.fraction > 0f && panels.fraction < 0.4f)
                assertTrue(!panels.isOpen)
            }
            composeRule.onNodeWithTag("reader").performTouchInput {
                moveTo(center + Offset(60f * direction, 0f) * density, delayMillis = 200)
            }
            assertTrue(composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.width > firstWidth)
            composeRule.runOnIdle { assertTrue(!panels.isOpen) }
            composeRule.onNodeWithTag("reader").performTouchInput { up() }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertTrue(panels.isOpen)
                assertEquals(1f, panels.fraction, 0.001f)
                assertEquals(0, readerList.firstVisibleItemScrollOffset)
            }
            if (side == ReaderSidePanel.CONTENTS) {
                composeRule.onNodeWithTag("contents").performTouchInput {
                    swipe(center + Offset(2f, 60f) * density, center - Offset(2f, 60f) * density, 300)
                }
                composeRule.runOnIdle {
                    assertTrue(panelList.firstVisibleItemIndex > 0 || panelList.firstVisibleItemScrollOffset > 0)
                    assertTrue(panels.isOpen)
                }
            }
            composeRule.onNodeWithTag("reader").performTouchInput {
                if (side == ReaderSidePanel.CONTENTS) click(Offset(width - 1f, centerY))
                else swipe(center + Offset(60f * direction, 0f) * density, center - Offset(60f * direction, 0f) * density, 300)
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle { assertEquals(null, panels.panel) }
        }
    }

    @Test
    fun shortPullReturnsAndUnavailableCommentsNeverReveal() {
        lateinit var panels: ReaderSidePanelsState
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            panels = rememberReaderSidePanelsState()
            Box(Modifier.size(240.dp).testTag("reader")) {
                ReaderSidePanels(panels, true, false, {}, { Box(Modifier.fillMaxSize()) }, {}) {
                    ReaderShell(background = Color.White, onReadingAreaTap = { _, _ -> }) { }
                }
            }
        }
        composeRule.onNodeWithTag("reader").performTouchInput {
            swipe(center, center + Offset(28f, 0f) * density, 200)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(null, panels.panel) }
        composeRule.onNodeWithTag("reader").performTouchInput {
            swipe(center + Offset(70f, 0f) * density, center - Offset(70f, 0f) * density, 300)
        }
        composeRule.runOnIdle { assertEquals(null, panels.panel) }
    }

    @Test
    fun horizontalPagesFollowDragAndVerticalPullsDoNotTurnPages() {
        lateinit var pagerState: PagerState
        var density = 1f
        var taps = 0
        var bookmarked = false
        val swipes = mutableListOf<Boolean>()
        composeRule.setContent {
            density = LocalDensity.current.density
            pagerState = rememberPagerState { 3 }
            Box(Modifier.size(240.dp).testTag("reader")) {
                ReaderShell(
                    background = Color.White,
                    onReadingAreaTap = { _, _ -> taps++ },
                    pagedGesturesEnabled = true,
                    bookmarkPullEnabled = true,
                    onBookmarkPull = {
                        bookmarked = !bookmarked
                        swipes.add(bookmarked)
                    }
                ) {
                    HorizontalPager(
                        state = pagerState,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            pagerSnapDistance = PagerSnapDistance.atMost(1),
                            snapPositionalThreshold = 0.5f
                        ),
                        modifier = Modifier.fillMaxSize().testTag("pages")
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        composeRule.onNodeWithTag("reader").performTouchInput {
            swipe(center, center - Offset(32f, 0f) * density, 600)
        }
        composeRule.runOnIdle {
            assertEquals(0, pagerState.currentPage)
            assertEquals(0f, pagerState.currentPageOffsetFraction, 0.001f)
        }
        composeRule.onNodeWithTag("reader").performTouchInput {
            val start = center + Offset(80f, 20f) * density
            down(start)
            moveTo(start - Offset(40f, 10f) * density, delayMillis = 75)
            moveTo(start - Offset(80f, 20f) * density, delayMillis = 75)
        }
        composeRule.runOnIdle {
            assertEquals(0, pagerState.currentPage)
            assertTrue(pagerState.currentPageOffsetFraction > 0.1f)
            assertTrue(swipes.isEmpty())
        }
        composeRule.onNodeWithTag("reader").performTouchInput {
            moveTo(center - Offset(80f, 20f) * density, delayMillis = 150)
            up()
        }
        composeRule.runOnIdle {
            assertEquals(1, pagerState.currentPage)
            assertTrue(swipes.isEmpty())
        }
        // Upward swipes and short pulls must leave the bookmark unchanged.
        composeRule.onNodeWithTag("reader").performTouchInput {
            val upward = Offset(0f, 30f) * density
            swipe(center + upward, center - upward, 300)
            val shortPull = Offset(0f, 15f) * density
            swipe(center - shortPull, center + shortPull, 300)
        }
        composeRule.runOnIdle { assertTrue(swipes.isEmpty()) }
        // Once vertical intent is locked, a later sideways bend cannot turn a page.
        composeRule.onNodeWithTag("reader").performTouchInput {
            val start = center - Offset(70f, 60f) * density
            down(start)
            moveTo(start + Offset(8f, 24f) * density, delayMillis = 50)
            moveTo(start + Offset(140f, 45f) * density, delayMillis = 150)
            up()
        }
        composeRule.runOnIdle {
            assertEquals(1, pagerState.currentPage)
            assertEquals(0f, pagerState.currentPageOffsetFraction, 0.001f)
            assertTrue(swipes.isEmpty())
        }
        val pageTop = composeRule.onNodeWithTag("pages").fetchSemanticsNode().boundsInRoot.top
        repeat(2) { completedPulls ->
            composeRule.onNodeWithTag("reader").performTouchInput {
                val movement = Offset(20f, 60f) * density
                down(center - movement)
                moveTo(center - movement / 3f, delayMillis = 100)
                moveTo(center + movement / 3f, delayMillis = 100)
                moveTo(center + movement, delayMillis = 100)
            }
            composeRule.waitForIdle()
            assertTrue(composeRule.onNodeWithTag("pages").fetchSemanticsNode().boundsInRoot.top > pageTop)
            composeRule.runOnIdle { assertEquals(completedPulls, swipes.size) }
            composeRule.onNodeWithTag("reader").performTouchInput { up() }
            composeRule.waitForIdle()
            assertEquals(pageTop, composeRule.onNodeWithTag("pages").fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        composeRule.runOnIdle {
            assertEquals(listOf(true, false), swipes)
            assertEquals(1, pagerState.currentPage)
            assertEquals(0f, pagerState.currentPageOffsetFraction, 0.001f)
            assertEquals(0, taps)
        }
    }

    @Test
    fun nestedVerticalScrollingTakesPriorityOverBookmarkPulls() {
        lateinit var scrollState: ScrollState
        lateinit var pagerState: PagerState
        var density = 1f
        var pulls = 0
        composeRule.setContent {
            density = LocalDensity.current.density
            scrollState = rememberScrollState(initial = 140)
            pagerState = rememberPagerState(initialPage = 1) { 3 }
            Box(Modifier.size(240.dp).testTag("reader")) {
                ReaderShell(background = Color.White, onReadingAreaTap = { _, _ -> },
                    pagedGesturesEnabled = true, verticalContentScrollable = true,
                    bookmarkPullEnabled = true, onBookmarkPull = { pulls++ }) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                            Box(Modifier.height(800.dp))
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("reader").performTouchInput {
            swipe(center - Offset(20f, 60f) * density, center + Offset(20f, 60f) * density, 300)
        }
        composeRule.runOnIdle {
            assertTrue(scrollState.value < 140)
            assertEquals(0, pulls)
            assertEquals(1, pagerState.currentPage)
        }
    }
}
