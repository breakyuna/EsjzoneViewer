package com.breakyuna.esjzone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.ContainerTransformLayout
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.novelCoverDestination
import com.breakyuna.esjzone.ui.navigation.popTransition
import com.breakyuna.esjzone.ui.navigation.predictivePopTransition
import com.breakyuna.esjzone.ui.navigation.pushTransition
import com.breakyuna.esjzone.ui.navigation.rememberNovelCoverSource
import com.breakyuna.esjzone.ui.navigation.rememberSharedCoverOrigin
import com.breakyuna.esjzone.ui.page.NovelPageModel
import com.breakyuna.esjzone.ui.page.rememberNovelDetailPresentationState
import com.breakyuna.esjzone.ui.page.rememberNovelDetailTransitionSettled
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Reproduces entry -> predictive back mid-flight -> entry again, including visible page pixels. */
class NovelInterruptedTransitionInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    private val url = "/detail/123.html"
    private val detail = AppNavKey.Legacy(LegacyRoute.Novel(url))
    private val input = DirectNavigationEventInput()
    private val detailSource = MutableStateFlow<NovelPageModel.State>(NovelPageModel.State.Loading)
    private lateinit var stack: MutableList<NavKey>
    private lateinit var openBook: () -> Unit
    private var detailSettled = false
    private var presentedDetail: NovelPageModel.State = NovelPageModel.State.Loading

    @Test fun ordinaryPageLeftGestureKeepsItsFullPageSlideOnCommit() =
        verifyOrdinaryPageBack(NavigationEvent.EDGE_LEFT)

    @Test fun ordinaryPageRightGestureKeepsItsDirectionOnCommit() =
        verifyOrdinaryPageBack(NavigationEvent.EDGE_RIGHT)

    private fun verifyOrdinaryPageBack(edge: Int) {
        mount(AppNavKey.Legacy(LegacyRoute.Search("fixture")))
        openDuringPausedClock()
        settle()
        startBack(edge)
        val before = redBounds()
        val pixels = composeRule.onNodeWithTag("motion-host").captureToImage().toPixelMap()
        assertEquals("The ordinary page must not shrink", pixels.height, before.height)
        assertTrue("Either back edge must slide the page right", before.left > 0)
        val color = pixels[(before.left + before.right) / 2, pixels.height / 2]
        assertTrue("The ordinary page must remain opaque", color.red > 0.99f && color.green < 0.01f && color.blue < 0.01f)
        composeRule.runOnIdle { input.backCompleted() }
        composeRule.mainClock.advanceTimeBy(96)
        val after = redBounds()
        assertEquals("Commit must preserve the full page height", pixels.height, after.height)
        assertTrue("Commit must continue sliding right", after.left > before.left)
        settle()
        composeRule.runOnIdle { assertEquals(AppNavKey.HomeTab, stack.last()) }
    }

    @Test fun leftEdgeBackKeepsDetailOpaqueAndFullHeight() =
        verifyDetailGesture(NavigationEvent.EDGE_LEFT)

    @Test fun rightEdgeBackUsesTheSameDetailExit() =
        verifyDetailGesture(NavigationEvent.EDGE_RIGHT)

    private fun verifyDetailGesture(edge: Int) {
        mount()
        openDuringPausedClock()
        settle()
        startBack(edge)
        val page = redBounds()
        val pixels = composeRule.onNodeWithTag("motion-host").captureToImage().toPixelMap()
        assertTrue("Both edges must move the detail page towards its normal right-hand exit", page.left > 0)
        assertEquals("The detail page must not shrink", 0, page.top)
        assertEquals("The detail page must retain its full height", pixels.height, page.bottom)
        val color = pixels[(page.left + page.right) / 2, pixels.height / 2]
        assertTrue("The detail page must not fade", color.red > 0.99f && color.green < 0.01f && color.blue < 0.01f)
        composeRule.runOnIdle { input.backCancelled() }
        settle()
        assertPageFillsHost()
    }

    @Test fun interruptedBackMovesBothPageAndCoverAndKeepsNextEntryVisible() {
        mount()
        val sourceCover = blueBounds()
        openDuringPausedClock()
        startBack()
        val coverBefore = blueBounds()
        val pageBefore = redBounds()
        composeRule.runOnIdle { input.backCompleted() }
        composeRule.mainClock.advanceTimeBy(96)
        val coverAfter = blueBounds()
        val pageAfter = redBounds()
        assertTrue("The page must move out instead of freezing until disposal", pageAfter.left > pageBefore.left)
        assertTrue("The cover must move back towards the source", coverAfter.top > coverBefore.top)
        settle()
        assertEquals(sourceCover, blueBounds())
        composeRule.runOnIdle { assertEquals(AppNavKey.HomeTab, stack.last()) }

        // Reopen only after the interrupted exit has finished, as in the device recording.
        openDuringPausedClock()
        assertPageIsSlidingAboveHome()
        settle()
        assertPageFillsHost()
    }

    @Test fun repushingSameNovelBeforeInterruptedExitFinishesKeepsPageVisible() {
        mount()
        openDuringPausedClock()
        startBack()
        composeRule.runOnIdle { input.backCompleted() }
        composeRule.mainClock.advanceTimeBy(32)
        composeRule.runOnIdle { openBook() }
        composeRule.mainClock.advanceTimeBy(96)
        redBounds() // A shared cover alone cannot satisfy this pixel assertion.
        blueBounds()
        settle()
        composeRule.runOnIdle { assertEquals(detail, stack.last()) }
        assertPageFillsHost()
    }

    @Test fun cancellingBackDefersDetailUpdatesAndPreservesFollowingEntry() {
        mount()
        openDuringPausedClock()
        startBack()
        val failure = NovelPageModel.State.Error(LoadFailureKind.NETWORK)
        composeRule.runOnIdle { detailSource.value = failure }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            assertFalse("An active back gesture is not a settled page", detailSettled)
            assertEquals(NovelPageModel.State.Loading, presentedDetail)
            input.backCancelled()
        }
        settle()
        composeRule.runOnIdle {
            assertEquals(detail, stack.last())
            assertTrue(detailSettled)
            assertEquals(failure, presentedDetail)
            stack.removeLastOrNull()
        }
        settle()
        openDuringPausedClock()
        assertPageIsSlidingAboveHome()
        settle()
        assertPageFillsHost()
    }

    private fun mount(destination: AppNavKey.Legacy = detail) {
        composeRule.setContent {
            MaterialTheme {
                val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current)
                    .navigationEventDispatcher
                DisposableEffect(dispatcher) {
                    dispatcher.addInput(input)
                    onDispose { dispatcher.removeInput(input) }
                }
                stack = rememberNavBackStack(AppNavKey.HomeTab)
                ContainerTransformLayout(Modifier.size(320.dp, 480.dp).testTag("motion-host")) {
                    NavDisplay(
                        backStack = stack,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { stack.removeLastOrNull() },
                        transitionSpec = pushTransition,
                        popTransitionSpec = popTransition,
                        predictivePopTransitionSpec = predictivePopTransition,
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                        entryProvider = entryProvider {
                            entry<AppNavKey.HomeTab> {
                                val source = rememberNovelCoverSource(url, "fixture-cover")
                                SideEffect { openBook = { source.open { stack.add(destination) } } }
                                Box(Modifier.fillMaxSize().background(Color.White)) {
                                    Box(Modifier.offset(16.dp, 300.dp).then(source.modifier)
                                        .size(48.dp, 72.dp).background(Color.Blue))
                                }
                            }
                            entry<AppNavKey.Legacy> {
                                rememberSharedCoverOrigin(url, releaseOnDispose = true)
                                val settled by rememberNovelDetailTransitionSettled()
                                val presented by rememberNovelDetailPresentationState(detailSource)
                                SideEffect {
                                    detailSettled = settled
                                    presentedDetail = presented
                                }
                                Box(Modifier.fillMaxSize().background(Color.Red)) {
                                    Box(Modifier.offset(16.dp, 80.dp).novelCoverDestination(url)
                                        .size(104.dp, 148.dp).background(Color.Blue))
                                }
                            }
                        }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    private fun openDuringPausedClock() {
        composeRule.runOnIdle { openBook() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(96)
    }

    private fun startBack(edge: Int = NavigationEvent.EDGE_LEFT) {
        composeRule.runOnIdle {
            input.backStarted(NavigationEvent(swipeEdge = edge))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            input.backProgressed(NavigationEvent(swipeEdge = edge, progress = 0.25f))
        }
        composeRule.mainClock.advanceTimeBy(64)
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
    }

    private fun assertPageIsSlidingAboveHome() {
        val page = redBounds()
        val pixels = composeRule.onNodeWithTag("motion-host").captureToImage()
        assertTrue("The new page must already be visible during the cover flight", page.width > 0)
        assertTrue("The page must still be sliding in", page.left > 0 && page.left < pixels.width)
    }

    private fun assertPageFillsHost() {
        val page = redBounds()
        val pixels = composeRule.onNodeWithTag("motion-host").captureToImage()
        assertEquals(0, page.left)
        assertEquals(pixels.width, page.right)
        assertEquals(pixels.height, page.bottom)
    }

    private fun redBounds(): IntRect = colorBounds(cover = false)
    private fun blueBounds(): IntRect = colorBounds(cover = true)

    private fun colorBounds(cover: Boolean): IntRect {
        val pixels = composeRule.onNodeWithTag("motion-host").captureToImage().toPixelMap()
        var left = pixels.width
        var top = pixels.height
        var right = 0
        var bottom = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            val matches = if (cover) {
                pixel.blue > pixel.red + 0.2f && pixel.blue > pixel.green + 0.2f
            } else {
                pixel.red > pixel.green + 0.2f && pixel.red > pixel.blue + 0.2f
            }
            if (matches) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + 1)
                bottom = maxOf(bottom, y + 1)
            }
        }
        assertTrue(
            if (cover) "The shared cover must be visible" else "The page must not be hidden below the home scene",
            right > left && bottom > top
        )
        return IntRect(left, top, right, bottom)
    }
}
