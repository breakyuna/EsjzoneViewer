package com.breakyuna.esjzone

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.breakyuna.esjzone.ui.designsystem.ReaderInlineImage
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderImageCacheInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun disposedIllustrationReturnsFromMemoryWithoutLoadingOrExecutingAnotherRequest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("reader-image-cache-", ".png", context.cacheDir)
        val requests = AtomicInteger()
        val loadingFrames = AtomicInteger()
        val loader = ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(32L * 1024 * 1024).build() }
            .components {
                add(Interceptor { chain ->
                    requests.incrementAndGet()
                    chain.proceed()
                })
            }.build()
        try {
            val bitmap = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888)
            try {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
            val visible = mutableStateOf(true)
            val request = ImageRequest.Builder(context).data(file).build()
            composeRule.setContent {
                if (visible.value) ReaderInlineImage(
                    request, loader, "Cached illustration", Modifier.width(180.dp),
                    loading = {
                        SideEffect { loadingFrames.incrementAndGet() }
                        Text("Loading")
                    },
                    error = { Text("Failed") }
                )
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithContentDescription("Cached illustration")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.runOnIdle { visible.value = false }
            composeRule.waitForIdle()
            val before = loadingFrames.get()
            composeRule.runOnIdle { visible.value = true }
            composeRule.onNodeWithContentDescription("Cached illustration").assertIsDisplayed()
            assertEquals(1, requests.get())
            assertEquals(before, loadingFrames.get())
        } finally {
            loader.shutdown()
            file.delete()
        }
    }
}
