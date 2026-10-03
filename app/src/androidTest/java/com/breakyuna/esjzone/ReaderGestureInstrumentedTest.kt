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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.reader.ReaderShell
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
    fun deliberateSideSwipesWorkWhileInitialHorizontalDriftStillAllowsListScrolling() {
        lateinit var listState: LazyListState
        var density = 1f
        var taps = 0
        val swipes = mutableListOf<Boolean>()
        composeRule.setContent {
            density = LocalDensity.current.density
            listState = rememberLazyListState()
            Box(Modifier.size(240.dp).testTag("reader")) {
                ReaderShell(
                    background = Color.White,
                    onReadingAreaTap = { _, _ -> taps++ },
                    horizontalSwipeEnabled = true,
                    deliberateHorizontalSwipe = true,
                    onHorizontalSwipe = { swipes.add(it) }
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(30) { Box(Modifier.height(64.dp)) }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("reader").performTouchInput {
            swipe(center + Offset(20f, 60f) * density, center - Offset(20f, 60f) * density, 300)
        }
        var position = 0 to 0
        composeRule.runOnIdle {
            assertTrue(listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
            assertTrue(swipes.isEmpty())
            position = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
        composeRule.onNodeWithTag("reader").performTouchInput {
            val start = center + Offset(-20f, 60f) * density
            down(start)
            moveTo(start + Offset(20f, -15f) * density, delayMillis = 16)
            moveTo(start + Offset(24f, -120f) * density, delayMillis = 100)
            up()
        }
        composeRule.runOnIdle {
            assertTrue(listState.firstVisibleItemIndex > position.first ||
                (listState.firstVisibleItemIndex == position.first && listState.firstVisibleItemScrollOffset > position.second))
            assertTrue(swipes.isEmpty())
            position = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
        for (direction in listOf(1f, -1f)) {
            composeRule.onNodeWithTag("reader").performTouchInput {
                val movement = Offset(60f, 4f) * density * direction
                swipe(center - movement, center + movement, 300)
            }
        }
        composeRule.runOnIdle {
            assertEquals(listOf(false, true), swipes)
            assertEquals(position, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
            assertEquals(0, taps)
        }
    }

    @Test
    fun downwardPullsToggleBookmarkWhileUpwardSwipesDoNothingAndPagingStillWorks() {
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
                    bookmarkPullEnabled = true,
                    onBookmarkPull = {
                        bookmarked = !bookmarked
                        swipes.add(bookmarked)
                    }
                ) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().testTag("pages")) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        composeRule.onNodeWithTag("reader").performTouchInput {
            swipe(center + Offset(80f, 20f) * density, center - Offset(80f, 20f) * density, 300)
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
        val pageTop = composeRule.onNodeWithTag("pages").fetchSemanticsNode().boundsInRoot.top
        repeat(2) { completedPulls ->
            composeRule.onNodeWithTag("reader").performTouchInput {
                val movement = Offset(20f, 60f) * density
                down(center - movement)
                moveTo(center + movement, delayMillis = 300)
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
        var density = 1f
        var pulls = 0
        composeRule.setContent {
            density = LocalDensity.current.density
            scrollState = rememberScrollState(initial = 140)
            Box(Modifier.size(240.dp).testTag("reader")) {
                ReaderShell(background = Color.White, onReadingAreaTap = { _, _ -> },
                    bookmarkPullEnabled = true, onBookmarkPull = { pulls++ }) {
                    Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        Box(Modifier.height(800.dp))
                    }
                }
            }
        }
        composeRule.onNodeWithTag("reader").performTouchInput {
            swipe(center - Offset(0f, 80f) * density, center + Offset(0f, 80f) * density, 300)
        }
        composeRule.runOnIdle {
            assertTrue(scrollState.value < 140)
            assertEquals(0, pulls)
        }
    }
}
