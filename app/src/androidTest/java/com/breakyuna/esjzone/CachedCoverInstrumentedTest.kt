package com.breakyuna.esjzone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import coil3.Image
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.breakyuna.esjzone.ui.designsystem.loadCachedCover
import com.breakyuna.esjzone.ui.designsystem.AppLoadedImage
import com.breakyuna.esjzone.ui.designsystem.CachedCoverImage
import com.breakyuna.esjzone.ui.navigation.ContainerTransformLayout
import com.breakyuna.esjzone.ui.navigation.LocalNovelSharedCovers
import com.breakyuna.esjzone.ui.navigation.LocalSharedCoverDestinationEnabled
import com.breakyuna.esjzone.ui.navigation.NovelSharedCovers
import com.breakyuna.esjzone.ui.navigation.rememberNovelCoverSource
import com.breakyuna.esjzone.ui.navigation.rememberSharedCoverOrigin
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CachedCoverInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

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
