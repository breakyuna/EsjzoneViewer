package com.breakyuna.esjzone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.ContainerTransformDestination
import com.breakyuna.esjzone.ui.navigation.ContainerTransformLayout
import com.breakyuna.esjzone.ui.navigation.ContainerTransformTarget
import com.breakyuna.esjzone.ui.navigation.HomeCarouselElement
import com.breakyuna.esjzone.ui.navigation.LocalHomeCarouselTransformTarget
import com.breakyuna.esjzone.ui.navigation.homeCarouselSharedElement
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.containerContentTransform
import com.breakyuna.esjzone.ui.navigation.containerTransformContent
import com.breakyuna.esjzone.ui.navigation.containerTransformSource
import com.breakyuna.esjzone.ui.navigation.containerTransformMetadata
import com.breakyuna.esjzone.ui.navigation.usesContainerTransform
import com.breakyuna.esjzone.ui.navigation.pushTransition
import com.breakyuna.esjzone.ui.navigation.popTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pixel bounds distinguish a growing card from a full-screen fade or slide. */
@RunWith(AndroidJUnit4::class)
class ContainerTransformInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val sourceContentSizes = mutableSetOf<IntSize>()
    private val destinationContentSizes = mutableSetOf<IntSize>()

    @Test fun forumExpandsFromItsCardAndReturns() = assertExpansion(ContainerTransformTarget.FORUM)
    @Test fun guestbookExpandsFromItsCardAndReturns() = assertExpansion(ContainerTransformTarget.GUESTBOOK)
    @Test fun waterCoolerExpandsFromItsCardAndReturns() = assertExpansion(ContainerTransformTarget.WATER_COOLER)
    @Test fun settingsExpandsFromItsCardAndReturns() = assertExpansion(ContainerTransformTarget.SETTINGS)
    @Test fun carouselContainerAndSharedElementsMoveTogetherWithRepeatedBooks() = assertExpansion(
        ContainerTransformTarget.homeCarousel("/detail/123.html", page = 100)
    )

    private fun assertExpansion(target: ContainerTransformTarget) {
        lateinit var stack: MutableList<NavKey>
        composeRule.setContent {
            MaterialTheme {
                stack = rememberNavBackStack(target.origin)
                ContainerTransformLayout(Modifier.size(320.dp, 480.dp).background(Color.Red)) {
                    NavDisplay(
                        backStack = stack,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { stack.removeLastOrNull() },
                        transitionSpec = {
                            if (usesContainerTransform(initialState, targetState)) containerContentTransform()
                            else pushTransition()
                        },
                        popTransitionSpec = {
                            if (usesContainerTransform(initialState, targetState)) containerContentTransform()
                            else popTransition()
                        },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator()
                        ),
                        entryProvider = entryProvider {
                            entry<AppNavKey.HomeTab>(metadata = ::containerTransformMetadata) {
                                val page = target.homeCarouselPage
                                if (page == null) {
                                    SourceCard(target)
                                } else {
                                    val pager = rememberPagerState(initialPage = page, pageCount = { Int.MAX_VALUE })
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        HorizontalPager(
                                            state = pager,
                                            beyondViewportPageCount = 1,
                                            modifier = Modifier.fillMaxWidth().height(196.dp)
                                        ) { instance ->
                                            SourceCard(
                                                ContainerTransformTarget.homeCarousel(
                                                    (target.route as LegacyRoute.Novel).identity,
                                                    instance
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            entry<AppNavKey.ProfileTab>(metadata = ::containerTransformMetadata) { SourceCard(target) }
                            entry<AppNavKey.Legacy>(metadata = ::containerTransformMetadata) { key ->
                                ContainerTransformDestination(checkNotNull(ContainerTransformTarget.forKey(key))) {
                                    Box(
                                        Modifier.fillMaxSize().background(Color.Blue)
                                            .onSizeChanged { destinationContentSizes.add(it) }
                                    ) {
                                        LocalHomeCarouselTransformTarget.current?.let {
                                            SharedElements(it, destination = true)
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
        composeRule.mainClock.autoAdvance = false
        val card = bluePixelBounds()
        val sourceElements = if (target.homeCarouselPage != null) sharedElementPixelBounds() else emptyList()
        composeRule.runOnIdle { stack.add(AppNavKey.Legacy(target.route, target.homeCarouselPage)) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(120)
        val expanding = bluePixelBounds()
        val movingElements = if (sourceElements.isNotEmpty()) sharedElementPixelBounds() else emptyList()
        assertStableContentSizes()
        composeRule.mainClock.advanceTimeBy(500)
        val fullScreen = bluePixelBounds()
        assertBetween(card, expanding, fullScreen)
        val destinationElements = if (sourceElements.isNotEmpty()) sharedElementPixelBounds() else emptyList()
        for (index in sourceElements.indices) {
            assertElementBetween(sourceElements[index], movingElements[index], destinationElements[index])
        }

        composeRule.runOnIdle { stack.removeLastOrNull() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(120)
        val contracting = bluePixelBounds()
        assertBetween(card, contracting, fullScreen)
        if (sourceElements.isNotEmpty()) {
            val returningElements = sharedElementPixelBounds()
            for (index in sourceElements.indices) {
                assertElementBetween(sourceElements[index], returningElements[index], destinationElements[index])
            }
        }
        assertStableContentSizes()
        composeRule.mainClock.advanceTimeBy(500)
        assertEquals(card, bluePixelBounds())
        if (sourceElements.isNotEmpty()) assertEquals(sourceElements, sharedElementPixelBounds())
    }

    @androidx.compose.runtime.Composable
    private fun SourceCard(target: ContainerTransformTarget) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.align(Alignment.Center)
                    .containerTransformSource(target)
                    .size(
                        if (target.homeCarouselPage == null) 96.dp else 280.dp,
                        if (target.homeCarouselPage == null) 64.dp else 196.dp
                    )
                    .background(Color.Blue)
            ) {
                Box(
                    Modifier.containerTransformContent().fillMaxSize()
                        .onSizeChanged { sourceContentSizes.add(it) }
                ) {
                    if (target.homeCarouselPage != null) SharedElements(target, destination = false)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SharedElements(target: ContainerTransformTarget, destination: Boolean) {
        // Different positions and sizes distinguish element motion from the outer bounds.
        HomeCarouselElement.entries.forEachIndexed { index, element ->
            Box(
                Modifier.offset(
                    x = if (destination) 20.dp else 100.dp,
                    y = (if (destination) 24.dp else 12.dp) + (index * 44).dp
                )
                    .homeCarouselSharedElement(target, element)
                    .size(if (destination) 64.dp else 24.dp, if (destination) 32.dp else 20.dp)
                    .background(listOf(Color.Green, Color.Yellow, Color.Cyan)[index])
            )
        }
    }

    private fun assertElementBetween(source: IntRect, moving: IntRect, destination: IntRect) {
        assertTrue(moving.left > destination.left && moving.left < source.left)
        assertTrue(moving.top > destination.top && moving.top < source.top)
        assertTrue(moving.width > source.width && moving.width < destination.width)
        assertTrue(moving.height > source.height && moving.height < destination.height)
    }

    private fun sharedElementPixelBounds(): List<IntRect> = listOf(
        pixelBounds { it.green > it.red + 0.1f && it.green > it.blue + 0.1f },
        pixelBounds { minOf(it.red, it.green) > it.blue + 0.1f },
        pixelBounds { minOf(it.green, it.blue) > it.red + 0.1f }
    )

    private fun assertStableContentSizes() {
        composeRule.runOnIdle {
            assertEquals("Source content must not resize while the card expands", 1, sourceContentSizes.size)
            assertEquals("Destination content must stay at its final size", 1, destinationContentSizes.size)
        }
    }

    private fun assertBetween(card: IntRect, intermediate: IntRect, fullScreen: IntRect) {
        assertTrue(intermediate.width > card.width && intermediate.width < fullScreen.width)
        assertTrue(intermediate.height > card.height && intermediate.height < fullScreen.height)
        assertTrue(intermediate.left < card.left && intermediate.left > fullScreen.left)
        assertTrue(intermediate.top < card.top && intermediate.top > fullScreen.top)
    }

    private fun bluePixelBounds(): IntRect = pixelBounds {
        it.blue > it.red + 0.1f && it.blue > it.green + 0.1f
    }

    private fun pixelBounds(matches: (Color) -> Boolean): IntRect {
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        var left = pixels.width
        var top = pixels.height
        var right = 0
        var bottom = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (matches(pixel)) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x + 1)
                    bottom = maxOf(bottom, y + 1)
                }
            }
        }
        assertTrue("The transforming container must remain visible", right > left && bottom > top)
        return IntRect(left, top, right, bottom)
    }
}
