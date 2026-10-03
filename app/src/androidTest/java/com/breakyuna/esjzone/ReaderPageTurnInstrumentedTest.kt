package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.reader.ReaderPageTurnQueue
import com.breakyuna.esjzone.ui.reader.ReaderShell
import com.breakyuna.esjzone.ui.reader.animateReaderPageTurn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPageTurnInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun tapsDuringAnimationEachFinishOnePageIncludingReverseDirection() {
        val queue = ReaderPageTurnQueue()
        val completed = mutableListOf<Int>()
        var taps = 0
        lateinit var pager: PagerState
        composeRule.setContent {
            pager = rememberPagerState { 5 }
            LaunchedEffect(Unit) {
                while (true) {
                    val forward = snapshotFlow { queue.next }.filterNotNull().first()
                    val target = pager.currentPage + if (forward) 1 else -1
                    pager.animateReaderPageTurn({ target }, { queue.durationMillis }, reducedMotion = false)
                    completed += pager.currentPage
                    queue.complete()
                }
            }
            ReaderShell(background = Color.White, pagedGesturesEnabled = true,
                onReadingAreaTap = { x, _ ->
                    taps++
                    queue.enqueue(x > 0.5f, taps * 60L)
                }) {
                HorizontalPager(state = pager, userScrollEnabled = !queue.hasTurns,
                    modifier = Modifier.fillMaxSize().testTag("pages")) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("pages").performTouchInput { click(Offset(width * 0.9f, height * 0.5f)) }
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.runOnIdle {
            assertTrue(pager.isScrollInProgress)
            assertTrue(completed.isEmpty())
            assertTrue(pager.currentPageOffsetFraction > 0f)
        }
        composeRule.onNodeWithTag("pages").performTouchInput { click(Offset(width * 0.9f, height * 0.5f)) }
        composeRule.onNodeWithTag("pages").performTouchInput { click(Offset(width * 0.1f, height * 0.5f)) }
        composeRule.runOnIdle { assertEquals(3, taps) }
        // The first 180 ms turn must speed up when more taps arrive, without restarting.
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.runOnIdle {
            assertTrue(completed.isNotEmpty())
            assertEquals(1, completed.first())
        }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.runOnIdle {
            assertEquals(listOf(1, 2, 1), completed)
            assertEquals(1, pager.currentPage)
            assertEquals(0f, pager.currentPageOffsetFraction, 0.001f)
            assertFalse(queue.hasTurns)
            assertFalse(pager.isScrollInProgress)
        }
    }

    @Test fun missingDestinationReleasesScrollingAndAllowsNextTurn() {
        val start = mutableStateOf(false)
        val nextTurn = mutableStateOf(false)
        val target = mutableStateOf<Int?>(1)
        var firstFinished = false
        var firstCompleted = true
        var nextCompleted = false
        lateinit var pager: PagerState
        composeRule.setContent {
            pager = rememberPagerState { 3 }
            LaunchedEffect(Unit) {
                snapshotFlow { start.value }.first { it }
                firstCompleted = pager.animateReaderPageTurn({ target.value }, { 180f }, reducedMotion = false)
                firstFinished = true
                snapshotFlow { nextTurn.value }.first { it }
                assertFalse(pager.animateReaderPageTurn({ 99 }, { 180f }, reducedMotion = false))
                nextCompleted = pager.animateReaderPageTurn({ 1 }, { 180f }, reducedMotion = false)
            }
            HorizontalPager(state = pager, userScrollEnabled = false,
                modifier = Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { start.value = true }
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.runOnIdle {
            assertTrue(pager.isScrollInProgress)
            target.value = null
        }
        composeRule.mainClock.advanceTimeBy(48)
        composeRule.runOnIdle {
            assertTrue(firstFinished)
            assertFalse(firstCompleted)
            assertFalse(pager.isScrollInProgress)
            nextTurn.value = true
        }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.runOnIdle {
            assertTrue(nextCompleted)
            assertEquals(1, pager.currentPage)
            assertEquals(0f, pager.currentPageOffsetFraction, 0.001f)
            assertFalse(pager.isScrollInProgress)
        }
    }
}
