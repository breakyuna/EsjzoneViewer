package com.breakyuna.esjzone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.Image
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.breakyuna.esjzone.ui.designsystem.loadCachedCover
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CachedCoverInstrumentedTest {
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
        var refreshed = 0
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
                }, onError = { throw AssertionError("Valid replacement must load", it) },
                onRefreshSuccess = {
                    assertEquals(listOf(Color.RED, Color.BLUE), colors)
                    refreshed++
                })
            assertEquals(listOf(Color.RED, Color.BLUE), colors)
            assertEquals(1, refreshed)

            var failure: Throwable? = null
            colors.clear()
            loadCachedCover(request(broken), request(old), loader, restoreCached = false,
                onImage = { displayed = it; colors += it.toBitmap().getPixel(0, 0) },
                onError = { failure = it }, onRefreshSuccess = { refreshed++ })
            assertNotNull(failure)
            assertEquals(1, refreshed)
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
