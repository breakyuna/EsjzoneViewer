package com.breakyuna.esjzone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import coil3.Image
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.breakyuna.esjzone.ui.designsystem.loadCachedCover
import com.breakyuna.esjzone.ui.designsystem.AppCoverImage
import com.breakyuna.esjzone.ui.designsystem.AppLoadedImage
import com.breakyuna.esjzone.ui.designsystem.CachedCoverImage
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.novel.CoverFailure
import com.breakyuna.esjzone.novellibrary.novel.CoverState
import com.breakyuna.esjzone.ui.navigation.ContainerTransformDestination
import com.breakyuna.esjzone.ui.navigation.ContainerTransformLayout
import com.breakyuna.esjzone.ui.navigation.ContainerTransformTarget
import com.breakyuna.esjzone.ui.navigation.HomeCarouselElement
import com.breakyuna.esjzone.ui.navigation.LocalHomeCarouselTransformTarget
import com.breakyuna.esjzone.ui.navigation.LocalNovelSharedCovers
import com.breakyuna.esjzone.ui.navigation.LocalSharedCoverDestinationEnabled
import com.breakyuna.esjzone.ui.navigation.NovelSharedCovers
import com.breakyuna.esjzone.ui.navigation.rememberNovelCoverSource
import com.breakyuna.esjzone.ui.navigation.rememberSharedCoverOrigin
import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.novelCoverDestination
import com.breakyuna.esjzone.ui.navigation.containerContentTransform
import com.breakyuna.esjzone.ui.navigation.containerTransformMetadata
import com.breakyuna.esjzone.ui.navigation.containerTransformSource
import com.breakyuna.esjzone.ui.navigation.homeCarouselSharedElement
import com.breakyuna.esjzone.ui.navigation.usesContainerTransform
import com.breakyuna.esjzone.ui.navigation.popTransition
import com.breakyuna.esjzone.ui.navigation.pushTransition
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CachedCoverInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun missingUnknownAndFailedSourcesKeepTheTransferredCover() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).build()
        val cover = File.createTempFile("retained-cover-", ".png", context.cacheDir)
        val unexpectedImage = CountDownLatch(1)
        var state by mutableStateOf<CoverState>(CoverState.Missing)
        try {
            writeCover(cover, Color.BLUE)
            val request = ImageRequest.Builder(context).data(cover.toURI().toString())
                .allowHardware(false).build()
            val retained = AppLoadedImage(runBlocking { loader.execute(request) as SuccessResult }.image)
            composeRule.setContent {
                AppCoverImage(
                    model = null,
                    sourceState = state,
                    initialImage = retained,
                    onImageLoaded = { unexpectedImage.countDown() },
                    contentDescription = null,
                    modifier = Modifier.size(60.dp).testTag("retained-cover")
                )
            }
            for (source in listOf(
                CoverState.Missing,
                CoverState.Unknown,
                CoverState.Failed(CoverFailure.NETWORK),
                CoverState.Failed(CoverFailure.OTHER)
            )) {
                composeRule.runOnIdle { state = source }
                assertFalse("Fallback artwork must not be reported as a loaded cover: $source",
                    unexpectedImage.await(500, TimeUnit.MILLISECONDS))
                val pixels = composeRule.onNodeWithTag("retained-cover").captureToImage().toPixelMap()
                val center = pixels[pixels.width / 2, pixels.height / 2]
                assertTrue("The transferred cover must remain visible: $source",
                    center.blue > 0.95f && center.red < 0.05f)
            }
        } finally {
            cover.delete()
            loader.shutdown()
        }
    }

    @Test fun missingReplacementStillRestoresARealCachedCover() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cover = File.createTempFile("cached-entry-cover-", ".png", context.cacheDir)
        val callbacks = CountDownLatch(2)
        var restored by mutableStateOf<AppLoadedImage?>(null)
        try {
            writeCover(cover, Color.RED)
            composeRule.setContent {
                AppCoverImage(
                    model = null,
                    sourceState = CoverState.Missing,
                    cachedModel = cover.toURI().toString(),
                    onImageLoaded = { restored = it; callbacks.countDown() },
                    contentDescription = null,
                    modifier = Modifier.size(60.dp).testTag("cached-entry-cover")
                )
            }
            composeRule.waitUntil(5_000) { restored != null }
            assertFalse("Placeholder artwork must not replace the cached cover",
                callbacks.await(500, TimeUnit.MILLISECONDS))
            val pixels = composeRule.onNodeWithTag("cached-entry-cover").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue(center.red > 0.95f && center.blue < 0.05f)
        } finally {
            cover.delete()
        }
    }

    @Test fun missingReplacementReportsTheCoverRestoredFromMemory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = EsjzoneApplication.instance.container.imageLoader
        val cover = File.createTempFile("memory-entry-cover-", ".png", context.cacheDir)
        val cacheKey = "memory-entry-${cover.name}"
        val key = MemoryCache.Key(cacheKey)
        var restored by mutableStateOf<AppLoadedImage?>(null)
        try {
            writeCover(cover, Color.RED)
            val request = ImageRequest.Builder(context).data(cover.toURI().toString())
                .memoryCacheKey(cacheKey).allowHardware(false).build()
            val decoded = runBlocking { loader.execute(request) as SuccessResult }.image
            loader.memoryCache!![key] = MemoryCache.Value(decoded)
            cover.delete()
            composeRule.setContent {
                AppCoverImage(
                    model = null,
                    sourceState = CoverState.Missing,
                    cachedModel = request,
                    onImageLoaded = { restored = it },
                    contentDescription = null,
                    modifier = Modifier.size(60.dp).testTag("memory-entry-cover")
                )
            }
            composeRule.waitUntil(5_000) { restored != null }
            assertSame(decoded, restored!!.image)
            val pixels = composeRule.onNodeWithTag("memory-entry-cover").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue(center.red > 0.95f && center.blue < 0.05f)
        } finally {
            loader.memoryCache?.remove(key)
            cover.delete()
        }
    }

    @Test fun sourceReplacementWaitsForTheSharedCoverReturn() =
        verifySourceReplacementWaitsForReturn(carousel = false)

    @Test fun sourceReplacementWaitsForTheCarouselReturn() =
        verifySourceReplacementWaitsForReturn(carousel = true)

    private fun verifySourceReplacementWaitsForReturn(carousel: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = EsjzoneApplication.instance.container.imageLoader
        val old = File.createTempFile("return-old-cover-", ".png", context.cacheDir)
        val fresh = File.createTempFile("return-detail-cover-", ".png", context.cacheDir)
        val url = "/detail/return-cover-test.html"
        val target = ContainerTransformTarget.homeCarousel(EsjzoneUrls.canonicalPageKey(url), 42)
        val replacement = CountDownLatch(1)
        lateinit var stack: MutableList<NavKey>
        var entryImage by mutableStateOf<AppLoadedImage?>(null)
        var detailImage by mutableStateOf<AppLoadedImage?>(null)
        var returnedImage by mutableStateOf<AppLoadedImage?>(null)
        try {
            writeCover(old, Color.RED)
            writeCover(fresh, Color.BLUE)
            fun request(file: File) = ImageRequest.Builder(context)
                .data(file.toURI().toString()).size(720).allowHardware(false).build()
            val oldRequest = request(old)
            val freshRequest = request(fresh)
            composeRule.setContent {
                ContainerTransformLayout(Modifier.size(320.dp, 480.dp).background(ComposeColor.White)) {
                    stack = rememberNavBackStack(AppNavKey.HomeTab)
                    NavDisplay(
                        backStack = stack,
                        onBack = { stack.removeLastOrNull() },
                        transitionSpec = {
                            if (usesContainerTransform(initialState, targetState)) containerContentTransform()
                            else pushTransition()
                        },
                        popTransitionSpec = {
                            if (usesContainerTransform(initialState, targetState)) containerContentTransform(pop = true)
                            else popTransition()
                        },
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                        entryProvider = entryProvider {
                            entry<AppNavKey.HomeTab>(metadata = ::containerTransformMetadata) {
                                val source = rememberNovelCoverSource(
                                    url, old.toURI().toString(), enableSharedElement = !carousel
                                )
                                SideEffect { returnedImage = source.initialImage }
                                val cardModifier = if (carousel) {
                                    Modifier.containerTransformSource(target).size(220.dp)
                                } else Modifier.fillMaxSize()
                                Box(cardModifier) {
                                    val coverModifier = if (carousel) {
                                        Modifier.homeCarouselSharedElement(target, HomeCarouselElement.COVER)
                                    } else source.modifier
                                    AppNovelCover(
                                        coverUrl = old.toURI().toString(),
                                        title = "Test cover",
                                        initialImage = source.initialImage,
                                        onImageLoaded = {
                                            source.onImageLoaded(it)
                                            if (source.initialImage == null) entryImage = it
                                            else replacement.countDown()
                                        },
                                        modifier = coverModifier.size(60.dp).testTag("old-cover")
                                            .clickable {
                                                source.open {
                                                    stack.add(if (carousel) AppNavKey.Legacy(target.route, target.homeCarouselPage)
                                                    else AppNavKey.Legacy(LegacyRoute.Novel(url)))
                                                }
                                            }
                                    )
                                }
                            }
                            entry<AppNavKey.Legacy>(metadata = ::containerTransformMetadata) {
                                val detail: @androidx.compose.runtime.Composable () -> Unit = {
                                    val origin = rememberSharedCoverOrigin(url, releaseOnDispose = true)
                                    CachedCoverImage(
                                        request = freshRequest, cachedRequest = oldRequest,
                                        initialImage = origin?.image, contentDescription = null,
                                        modifier = Modifier
                                            .homeCarouselSharedElement(LocalHomeCarouselTransformTarget.current, HomeCarouselElement.COVER)
                                            .novelCoverDestination(url).size(120.dp),
                                        contentScale = ContentScale.Crop, imageLoader = loader,
                                        onImageLoaded = { origin?.image = it; detailImage = it }
                                    )
                                }
                                if (carousel) ContainerTransformDestination(target) { detail() } else detail()
                            }
                        }
                    )
                }
            }
            composeRule.waitUntil(5_000) { entryImage != null }
            composeRule.onNodeWithTag("old-cover").performClick()
            composeRule.waitUntil(5_000) { detailImage != null }
            composeRule.waitForIdle()
            runBlocking { loader.execute(oldRequest) }

            composeRule.mainClock.autoAdvance = false
            composeRule.runOnIdle { stack.removeLastOrNull() }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.mainClock.advanceTimeByFrame()
            assertSame("The returning source must receive the detail cover", detailImage, returnedImage)
            assertFalse("The cached source cover must not replace the detail cover during return",
                replacement.await(500, TimeUnit.MILLISECONDS))
            assertReturnCoverIsBlue()
            composeRule.mainClock.advanceTimeBy(120)
            assertFalse("The source cover must remain deferred while the transition runs",
                replacement.await(500, TimeUnit.MILLISECONDS))
            assertReturnCoverIsBlue()

            composeRule.mainClock.advanceTimeBy(700)
            composeRule.mainClock.autoAdvance = true
            composeRule.waitUntil(5_000) { replacement.count == 0L }
            val pixels = composeRule.onNodeWithTag("old-cover").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue("A decoded source cover may replace the detail cover after return",
                center.red > 0.95f && center.blue < 0.05f)
        } finally {
            composeRule.mainClock.autoAdvance = true
            old.delete()
            fresh.delete()
        }
    }

    private fun assertReturnCoverIsBlue() {
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        var blue = false
        var red = false
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            blue = blue || (pixel.blue > 0.95f && pixel.red < 0.05f && pixel.green < 0.05f)
            red = red || (pixel.red > 0.95f && pixel.blue < 0.05f && pixel.green < 0.05f)
        }
        assertTrue("The detail cover must remain visible during return", blue)
        assertFalse("The old cover must not appear during return", red)
    }

    @Test fun returnStartsWithLatestDetailImageWhileSourceUrlIsResolving() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).build()
        val old = File.createTempFile("entry-cover-", ".png", context.cacheDir)
        val fresh = File.createTempFile("detail-cover-", ".png", context.cacheDir)
        val broken = File.createTempFile("return-cover-", ".png", context.cacheDir)
        val url = "/detail/123.html"
        lateinit var stack: MutableList<NavKey>
        var detailImage: AppLoadedImage? = null
        var returnedImage: AppLoadedImage? = null
        var duplicateImage: AppLoadedImage? = null
        var resolving by mutableStateOf(true)
        try {
            writeCover(old, Color.RED)
            writeCover(fresh, Color.BLUE)
            broken.writeText("invalid image fixture")
            fun request(file: File) = ImageRequest.Builder(context)
                .data(file.toURI().toString()).allowHardware(false).build()
            val entryImage = AppLoadedImage(runBlocking { loader.execute(request(old)) as SuccessResult }.image)
            old.delete()
            loader.memoryCache?.clear()
            composeRule.setContent {
                ContainerTransformLayout(Modifier.size(320.dp, 480.dp)) {
                    stack = rememberNavBackStack(AppNavKey.HomeTab)
                    NavDisplay(
                        backStack = stack,
                        onBack = { stack.removeLastOrNull() },
                        transitionSpec = pushTransition,
                        popTransitionSpec = popTransition,
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                        entryProvider = entryProvider {
                            entry<AppNavKey.HomeTab> {
                                val duplicate = rememberNovelCoverSource(url, "duplicate-cover")
                                val clicked = rememberNovelCoverSource(url, if (resolving) "" else broken.toURI().toString())
                                SideEffect {
                                    duplicateImage = duplicate.initialImage
                                    returnedImage = clicked.initialImage
                                    if (clicked.initialImage == null) clicked.onImageLoaded(entryImage)
                                }
                                Box(Modifier.fillMaxSize()) {
                                    CachedCoverImage(
                                        request = request(broken), cachedRequest = request(old),
                                        initialImage = clicked.initialImage ?: entryImage,
                                        contentDescription = null,
                                        modifier = clicked.modifier.size(60.dp).testTag("return-cover")
                                            .clickable { clicked.open { stack.add(AppNavKey.Legacy(LegacyRoute.Novel(url))) } },
                                        contentScale = ContentScale.Crop, imageLoader = loader,
                                        loadEnabled = !resolving
                                    )
                                }
                            }
                            entry<AppNavKey.Legacy> {
                                val origin = rememberSharedCoverOrigin(url, releaseOnDispose = true)
                                Box(Modifier.fillMaxSize()) {
                                    CachedCoverImage(
                                        request = request(fresh), cachedRequest = request(old),
                                        initialImage = origin?.image, contentDescription = null,
                                        modifier = Modifier.novelCoverDestination(url).size(120.dp),
                                        contentScale = ContentScale.Crop, imageLoader = loader,
                                        onImageLoaded = { origin?.image = it; detailImage = it }
                                    )
                                }
                            }
                        }
                    )
                }
            }
            composeRule.onNodeWithTag("return-cover").performClick()
            composeRule.waitUntil(5_000) { detailImage != null }
            composeRule.waitForIdle()
            loader.memoryCache?.clear()
            composeRule.mainClock.autoAdvance = false
            composeRule.runOnIdle { stack.removeLastOrNull() }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.mainClock.advanceTimeByFrame()
            assertSame("Return must reuse the latest decoded detail image", detailImage, returnedImage)
            assertEquals("Duplicate occurrences must not inherit the clicked image", null, duplicateImage)
            val pixels = composeRule.onRoot().captureToImage().toPixelMap()
            assertTrue("The cover must stay blue at the start of return",
                (0 until pixels.height).any { y -> (0 until pixels.width).any { x ->
                    val pixel = pixels[x, y]
                    pixel.blue > 0.95f && pixel.red < 0.05f
                } })
            composeRule.mainClock.advanceTimeBy(700)
            composeRule.mainClock.autoAdvance = true
            composeRule.runOnIdle { resolving = false }
            composeRule.waitForIdle()
            val coverPixels = composeRule.onNodeWithTag("return-cover").captureToImage().toPixelMap()
            val center = coverPixels[coverPixels.width / 2, coverPixels.height / 2]
            assertTrue("Failed local replacement must keep the retained image after detail is disposed",
                center.blue > 0.95f && center.red < 0.05f)
        } finally {
            old.delete()
            fresh.delete()
            broken.delete()
            loader.shutdown()
        }
    }

    @Test fun clickedSourceImageIsAvailableBeforeAnyCacheRead() = verifySourceImageHandoff(carousel = false)

    @Test fun carouselImageIsAvailableWithItsSeparateSharedElementTransition() = verifySourceImageHandoff(carousel = true)

    private fun verifySourceImageHandoff(carousel: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).build()
        val old = File.createTempFile("source-cover-", ".png", context.cacheDir)
        val broken = File.createTempFile("replacement-cover-", ".png", context.cacheDir)
        val url = "/detail/123.html"
        var opened by mutableStateOf(false)
        var heroVisible by mutableStateOf(true)
        var received: AppLoadedImage? = null
        var registry: NovelSharedCovers? = null
        try {
            writeCover(old, Color.RED)
            broken.writeText("invalid image fixture")
            val cached = ImageRequest.Builder(context).data(old.toURI().toString())
                .allowHardware(false).build()
            val image = AppLoadedImage(runBlocking { loader.execute(cached) as SuccessResult }.image)
            old.delete()
            loader.memoryCache?.clear()
            val request = ImageRequest.Builder(context).data(broken.toURI().toString()).build()
            composeRule.setContent {
                ContainerTransformLayout {
                    registry = LocalNovelSharedCovers.current
                    if (!opened) {
                        // Duplicate occurrences must hand off the clicked instance's image.
                        rememberNovelCoverSource(url, cached.data.toString(), enableSharedElement = false)
                        val clicked = rememberNovelCoverSource(url, cached.data.toString(), enableSharedElement = false)
                        SideEffect { clicked.onImageLoaded(image) }
                        Box(Modifier.size(40.dp).testTag("open-cover")
                            .clickable { clicked.open { opened = true } })
                    } else {
                        CompositionLocalProvider(LocalSharedCoverDestinationEnabled provides !carousel) {
                            val origin = rememberSharedCoverOrigin(url, releaseOnDispose = true)
                            SideEffect { received = origin?.image }
                            if (heroVisible) {
                                // A lazy hero may leave composition while its page stays open.
                                rememberSharedCoverOrigin(url)
                                CachedCoverImage(
                                    request = request,
                                    cachedRequest = cached,
                                    initialImage = origin?.image,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).testTag("detail-cover"),
                                    contentScale = ContentScale.Crop,
                                    imageLoader = loader
                                )
                            }
                        }
                    }
                }
            }
            composeRule.onNodeWithTag("open-cover").performClick()
            composeRule.waitForIdle()
            assertSame(image, received)
            val pixels = composeRule.onNodeWithTag("detail-cover").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue("The source image survives a missing cache and failed replacement",
                center.red > 0.95f && center.green < 0.05f && center.blue < 0.05f)
            composeRule.runOnIdle { heroVisible = false }
            composeRule.waitForIdle()
            assertSame("Scrolling away must retain the page's source image",
                image, registry!!.sources.values.single().image)
            composeRule.runOnIdle { heroVisible = true }
            composeRule.waitForIdle()
            assertSame(image, received)
            composeRule.runOnIdle { opened = false }
            composeRule.waitForIdle()
            assertTrue("Leaving detail releases its image reference", registry!!.sources.isEmpty())
        } finally {
            old.delete()
            broken.delete()
            loader.shutdown()
        }
    }

    @Test fun displaysLocalCoverBeforeLoadingReplacementAndKeepsLastSuccessOnFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).build()
        val old = File.createTempFile("cached-cover-", ".png", context.cacheDir)
        val fresh = File.createTempFile("fresh-cover-", ".png", context.cacheDir)
        val broken = File.createTempFile("broken-cover-", ".png", context.cacheDir)
        fun request(file: File) = ImageRequest.Builder(context)
            .data(file.toURI().toString()).allowHardware(false).build()
        var displayed: Image? = null
        val colors = mutableListOf<Int>()
        try {
            writeCover(old, Color.RED)
            broken.writeText("invalid image fixture")
            loadCachedCover(request(fresh), request(old), loader, restoreCached = true,
                onImage = { image ->
                    displayed = image
                    val color = image.toBitmap().getPixel(0, 0)
                    colors += color
                    // The replacement cannot decode until the local image is displayed.
                    if (color == Color.RED) writeCover(fresh, Color.BLUE)
                }, onError = { throw AssertionError("Valid replacement must load", it) })
            assertEquals(listOf(Color.RED, Color.BLUE), colors)

            var failure: Throwable? = null
            colors.clear()
            loadCachedCover(request(broken), request(old), loader, restoreCached = false,
                onImage = { displayed = it; colors += it.toBitmap().getPixel(0, 0) },
                onError = { failure = it })
            assertNotNull(failure)
            assertEquals(emptyList<Int>(), colors)
            assertEquals(Color.BLUE, displayed!!.toBitmap().getPixel(0, 0))

            // Reopening with an unusable replacement still restores the shelf file.
            displayed = null
            loadCachedCover(request(broken), request(old), loader, restoreCached = true,
                onImage = { displayed = it }, onError = {})
            assertEquals(Color.RED, displayed!!.toBitmap().getPixel(0, 0))
        } finally {
            old.delete()
            fresh.delete()
            broken.delete()
            loader.shutdown()
        }
    }

    @Test fun missingLocalCopyStillAllowsReplacementToLoad() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).build()
        val missing = File.createTempFile("missing-cover-", ".png", context.cacheDir)
        val fresh = File.createTempFile("fresh-cover-", ".png", context.cacheDir)
        try {
            missing.delete()
            writeCover(fresh, Color.BLUE)
            val cached = ImageRequest.Builder(context).data(missing.toURI().toString()).build()
            val request = ImageRequest.Builder(context).data(fresh.toURI().toString())
                .allowHardware(false).build()
            val colors = mutableListOf<Int>()
            loadCachedCover(request, cached, loader, restoreCached = true,
                onImage = { colors += it.toBitmap().getPixel(0, 0) },
                onError = { throw AssertionError("Missing cache must not block replacement", it) })
            assertEquals(listOf(Color.BLUE), colors)
        } finally {
            missing.delete()
            fresh.delete()
            loader.shutdown()
        }
    }

    private fun writeCover(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }
}
