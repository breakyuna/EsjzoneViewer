package com.breakyuna.esjzone

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.ui.page.ReaderImageAppearanceControls
import com.breakyuna.esjzone.ui.reader.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderTextGlowInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun advancedGlowBackButtonReturnsToAppearanceControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            ReaderImageAppearanceControls(ReaderImageBackground(), onChange = {})
        }
        composeRule.onNodeWithText(context.getString(R.string.reader_glow_advanced)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reader_back)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.reader_glow_enabled)).assertExists()
    }

    @Test fun oppositeGlowAppearsForBothTextColorsAndDisappearsAtZeroOrWhenDisabled() {
        val white = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        val black = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
        var background by mutableStateOf(white)
        var settings by mutableStateOf(ReaderImageBackground(glowEnabled = false))
        composeRule.setContent {
            ReaderGlowSurface(background, settings, Modifier.size(160.dp, 100.dp).testTag("photo")
                .readerBackdrop(Color.Black, remember(background) { BitmapPainter(background.asImageBitmap()) })) {
                ReaderGlowBodyText("Readable text", TextStyle(fontSize = 24.sp), Modifier.padding(20.dp))
            }
        }
        fun shadedPixels(): Int {
            val pixels = composeRule.onNodeWithTag("photo").captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val red = pixels[x, y].red
                if (settings.textColor == 0xFFFFFFFFL && red < 0.9f || settings.textColor == 0xFF111111L && red > 0.15f) count++
            }
            return count
        }
        for (color in listOf(0xFFFFFFFFL, 0xFF111111L)) {
            composeRule.runOnIdle {
                background = if (color == 0xFFFFFFFFL) white else black
                settings = ReaderImageBackground(textColor = color, glowEnabled = false)
            }
            assertEquals(0, shadedPixels())
            for (strength in listOf(70f, 100f)) {
                composeRule.runOnIdle { settings = settings.copy(glowEnabled = true, glowStrength = strength) }
                composeRule.waitUntil(5_000) { shadedPixels() > 20 }
            }
            composeRule.runOnIdle { settings = settings.copy(glowStrength = 0f) }
            assertEquals(0, shadedPixels())
            composeRule.runOnIdle { settings = settings.copy(glowStrength = 70f, glowEnabled = false) }
            assertEquals(0, shadedPixels())
        }
        // Published bitmaps remain owned by Compose until the rule disposes its composition.
    }

    @Test fun mixedBackgroundEnhancesBothLightAndDarkRegions() {
        val background = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
        for (y in 0 until 120) for (x in 0 until 240) {
            background.setPixel(x, y, if (x < 120) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        var enabled by mutableStateOf(false)
        composeRule.setContent {
            ReaderGlowSurface(background, ReaderImageBackground(glowEnabled = enabled),
                Modifier.size(240.dp, 120.dp).testTag("mixed")
                    .readerBackdrop(Color.Black, remember { BitmapPainter(background.asImageBitmap()) })) {
                ReaderGlowBodyText("MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM", TextStyle(fontSize = 20.sp),
                    Modifier.fillMaxWidth().padding(16.dp))
            }
        }
        val before = composeRule.onNodeWithTag("mixed").captureToImage().toPixelMap()
        composeRule.runOnIdle { enabled = true }
        fun enhancedOnBothSides(): Boolean {
            val after = composeRule.onNodeWithTag("mixed").captureToImage().toPixelMap()
            var leftChanged = false
            var rightChanged = false
            for (y in 0 until after.height) for (x in 0 until after.width) {
                if (kotlin.math.abs(after[x, y].red - before[x, y].red) > 0.05f) {
                    if (x < after.width / 2) leftChanged = true else rightChanged = true
                }
            }
            return leftChanged && rightChanged
        }
        composeRule.waitUntil(5_000) { enhancedOnBothSides() }
    }

    @Test fun smallBodyAndControlsDoNotAcquireGlow() {
        val background = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        composeRule.setContent {
            ReaderGlowSurface(background, ReaderImageBackground(), Modifier.size(160.dp, 100.dp).testTag("small")
                .readerBackdrop(Color.White, BitmapPainter(background.asImageBitmap()))) {
                Column {
                    ReaderGlowBodyText("Small body", TextStyle(fontSize = 12.sp))
                    androidx.compose.material3.Text("Heading/control", color = Color.White, fontSize = 24.sp)
                }
            }
        }
        val pixels = composeRule.onNodeWithTag("small").captureToImage().toPixelMap()
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) assertEquals(1f, pixels[x, y].red, 0.01f)
    }
}
