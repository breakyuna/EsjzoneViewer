package com.breakyuna.esjzone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.reader.ReaderImageBackground
import com.breakyuna.esjzone.ui.reader.ReaderShell
import com.breakyuna.esjzone.ui.reader.readerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.breakyuna.esjzone.ui.reader.ReaderImageBackgroundStore
import com.breakyuna.esjzone.ui.reader.rememberReaderImageBackdrop
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderImageBackdropInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun aWarmBackgroundIsPresentInTheFirstComposition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File.createTempFile("reader-first-frame-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        val target = runBlocking(Dispatchers.IO) {
            val preview = ReaderImageBackgroundStore.render(source)
            ReaderImageBackgroundStore.persist(context, source, preview)
        }
        try {
            var captured = false
            var firstHasPainter = false
            var firstLoading = true
            composeRule.setContent {
                val backdrop = rememberReaderImageBackdrop(ReaderImageBackground(fileName = target.name), true)
                SideEffect {
                    if (!captured) {
                        firstHasPainter = backdrop.painter != null
                        firstLoading = backdrop.loading
                        captured = true
                    }
                }
            }
            composeRule.runOnIdle {
                assertTrue(captured)
                assertTrue(firstHasPainter)
                assertFalse(firstLoading)
            }
        } finally {
            source.delete()
            target.parentFile?.listFiles()?.filter { it.name.startsWith("${target.name}.render-") }?.forEach { it.delete() }
            target.delete()
        }
    }

    @Test fun aClearPhotoDoesNotFadeTheForeground() {
        composeRule.setContent {
            val painter = ColorPainter(Color.Red)
            Box(Modifier.size(40.dp)) {
                ReaderShell(background = Color.Black, backgroundPainter = painter, onReadingAreaTap = { _, _ -> }) {
                    Box(Modifier.fillMaxSize().testTag("reader-page").readerBackdrop(Color.Black, painter)) {
                        Box(Modifier.size(10.dp).background(Color.Blue))
                    }
                }
            }
        }
        val pixels = composeRule.onNodeWithTag("reader-page").captureToImage().toPixelMap()
        assertEquals(1f, pixels[pixels.width / 2, pixels.height / 2].red, 0.03f)
        assertEquals(1f, pixels[pixels.width / 8, pixels.height / 8].blue, 0.03f)
    }
}
