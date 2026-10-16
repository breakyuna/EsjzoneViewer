package com.breakyuna.esjzone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.ContainerTransformLayout
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.novelCoverDestination
import com.breakyuna.esjzone.ui.navigation.popTransition
import com.breakyuna.esjzone.ui.navigation.pushTransition
import com.breakyuna.esjzone.ui.navigation.rememberNovelCoverSource
import com.breakyuna.esjzone.ui.navigation.rememberSharedCoverOrigin
import com.breakyuna.esjzone.ui.designsystem.contentArrival
import com.breakyuna.esjzone.ui.page.NovelDetailLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NovelSharedCoverInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun clickedCoverSurvivesDetailLoadingAndReturnsToItsOwnPosition() {
        val url = "/detail/123.html"
        lateinit var stack: MutableList<NavKey>
        var destinationPreview: String? = null
        var detailRevision by mutableStateOf(0)
        var heroInstance: Any? = null
        composeRule.setContent {
            MaterialTheme {
                stack = rememberNavBackStack(AppNavKey.HomeTab)
                ContainerTransformLayout(Modifier.size(320.dp, 480.dp).background(Color.White)) {
                    NavDisplay(
                        backStack = stack,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { stack.removeLastOrNull() },
                        transitionSpec = pushTransition,
                        popTransitionSpec = popTransition,
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                        entryProvider = entryProvider {
                            entry<AppNavKey.HomeTab> {
                                val first = rememberNovelCoverSource(url, "first-cover")
                                val second = rememberNovelCoverSource(url, "clicked-cover")
                                Box(Modifier.fillMaxSize()) {
                                    Box(Modifier.offset(16.dp, 16.dp).then(first.modifier)
                                        .size(48.dp, 72.dp).background(Color.Green))
                                    Box(Modifier.offset(16.dp, 300.dp).then(second.modifier)
                                        .size(48.dp, 72.dp).background(Color.Blue).testTag("open-book")
                                        .clickable {
                                            second.open { stack.add(AppNavKey.Legacy(LegacyRoute.Novel(url))) }
                                        })
                                }
                            }
                            entry<AppNavKey.Legacy> {
                                destinationPreview = rememberSharedCoverOrigin(url)?.coverUrl
                                NovelDetailLayout(
                                    hero = {
                                        val instance = remember { Any() }
                                        SideEffect { heroInstance = instance }
                                        Box(Modifier.size(280.dp, 148.dp)) {
                                            Box(Modifier.novelCoverDestination(url)
                                                .size(104.dp, 148.dp).background(Color.Blue))
                                            Box(Modifier.offset(120.dp, 24.dp)
                                                .contentArrival(url, delayMillis = 80)
                                                .size(80.dp, 24.dp).background(Color.Red)
                                                .testTag("hero-title-$detailRevision"))
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (detailRevision == 0) {
                                        item(key = "loading") {
                                            Box(Modifier.size(100.dp).background(Color.Gray))
                                        }
                                    } else {
                                        item(key = "actions") {
                                            Box(Modifier.size(100.dp).background(Color.Green))
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val source = blueBounds()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("open-book").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(120)
        val moving = blueBounds()
        val loadingHero = heroInstance
        composeRule.runOnIdle { detailRevision = 1 }
        composeRule.mainClock.advanceTimeByFrame()
        val movingWithDetail = blueBounds()
        assertSame("Loading completion must retain the shared cover and hero", loadingHero, heroInstance)
        composeRule.mainClock.advanceTimeBy(600)
        val destination = blueBounds()
        assertEquals("clicked-cover", destinationPreview)
        assertTrue(moving.width > source.width && moving.width < destination.width)
        assertTrue(movingWithDetail.width >= moving.width && movingWithDetail.width < destination.width)
        assertTrue(moving.top < source.top && moving.top > destination.top)
        composeRule.runOnIdle { detailRevision = 2 }
        composeRule.mainClock.advanceTimeByFrame()
        val titlePixels = composeRule.onNodeWithTag("hero-title-2").captureToImage().toPixelMap()
        val titleColor = titlePixels[titlePixels.width / 2, titlePixels.height / 2]
        assertTrue("Metadata refresh must not restart the title fade", titleColor.red > 0.95f && titleColor.green < 0.05f)
        composeRule.runOnIdle { stack.removeLastOrNull() }
        composeRule.mainClock.advanceTimeBy(700)
        assertEquals(source, blueBounds())
    }

    private fun blueBounds(): IntRect {
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        var left = pixels.width
        var top = pixels.height
        var right = 0
        var bottom = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            if (pixel.blue > pixel.red + 0.1f && pixel.blue > pixel.green + 0.1f) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + 1)
                bottom = maxOf(bottom, y + 1)
            }
        }
        assertTrue("The shared cover remains visible", right > left && bottom > top)
        return IntRect(left, top, right, bottom)
    }
}
