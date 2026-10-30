package com.breakyuna.esjzone

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.reader.ReaderImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderImageRetryInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun failedImageKeepsPlaceholderAndRetryLoadsTheSameImageWithoutOpeningViewer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val image = File.createTempFile("reader-image-retry-", ".png", context.cacheDir)
        try {
            image.writeText("Invalid image fixture")
            composeRule.setContent {
                MaterialTheme {
                    ReaderImage(image.toURI().toString(), "Illustration", Color.Black)
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag("reader-image-unavailable", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("reader-image-unavailable", useUnmergedTree = true).assertIsDisplayed()

            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            try {
                image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
            composeRule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("reader-image-unavailable")),
                useUnmergedTree = true).performClick()

            composeRule.waitUntil(5_000) {
                listOf("reader-image-unavailable", "reader-image-loading").all { tag ->
                    composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }
            }
            composeRule.onAllNodes(isDialog()).assertCountEquals(0)
        } finally {
            image.delete()
        }
    }
}
