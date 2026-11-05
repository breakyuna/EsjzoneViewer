package com.breakyuna.esjzone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderImageBackdropInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun transparencyChangesImageWithoutFadingContentOrDoubleApplyingAcrossPages() {
        var transparency by mutableFloatStateOf(0f)
        composeRule.setContent {
            val alpha = ReaderImageBackground(transparency = transparency).imageAlpha
            val painter = ColorPainter(Color.Red)
            Box(Modifier.size(40.dp)) {
                ReaderShell(
                    background = Color.Black, backgroundPainter = painter,
                    backgroundImageAlpha = alpha, onReadingAreaTap = { _, _ -> }
                ) {
                    Box(Modifier.fillMaxSize().testTag("reader-page").readerBackdrop(Color.Black, painter, alpha)) {
                        Box(Modifier.size(10.dp).background(Color.Blue))
                    }
                }
            }
        }

        for ((value, expectedRed) in listOf(0f to 1f, 50f to 0.5f, 100f to 0f)) {
            composeRule.runOnIdle { transparency = value }
            val pixels = composeRule.onNodeWithTag("reader-page").captureToImage().toPixelMap()
            val image = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals(expectedRed, image.red, 0.03f)
            assertEquals(0f, image.green, 0.03f)
            assertEquals(0f, image.blue, 0.03f)
            val foreground = pixels[pixels.width / 8, pixels.height / 8]
            assertEquals(0f, foreground.red, 0.03f)
            assertEquals(0f, foreground.green, 0.03f)
            assertEquals(1f, foreground.blue, 0.03f)
        }
    }
}
