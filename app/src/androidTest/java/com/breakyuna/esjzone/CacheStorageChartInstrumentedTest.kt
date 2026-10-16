package com.breakyuna.esjzone

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.designsystem.globalStringResource
import com.breakyuna.esjzone.ui.page.CacheStorageChart
import com.breakyuna.esjzone.ui.page.StorageCategory
import com.breakyuna.esjzone.ui.page.StorageUsage
import com.breakyuna.esjzone.ui.page.StorageUsagePart
import com.breakyuna.esjzone.ui.page.formatBytes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheStorageChartInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun tappingRowsIndependentlyExpandsAndCollapsesDetailsWithoutChangingTotal() {
        var cacheLabel = ""
        var resourcesLabel = ""
        var pageLabel = ""
        var fontLabel = ""
        val storage = listOf(
            StorageUsage(StorageCategory.CACHE, listOf(
                StorageUsagePart(R.string.storage_page_cache, 1024L),
                StorageUsagePart(R.string.settings_image_cache, 2048L))),
            StorageUsage(StorageCategory.READING_RESOURCES, listOf(
                StorageUsagePart(R.string.storage_bookshelf_covers, 4096L),
                StorageUsagePart(R.string.storage_reader_fonts, 8192L)))
        )
        composeRule.setContent {
            cacheLabel = globalStringResource(R.string.storage_cache)
            resourcesLabel = globalStringResource(R.string.storage_reading_resources)
            pageLabel = globalStringResource(R.string.storage_page_cache)
            fontLabel = globalStringResource(R.string.storage_reader_fonts)
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CacheStorageChart(storage, failed = false)
                }
            }
        }
        composeRule.onNodeWithText(pageLabel).assertDoesNotExist()
        composeRule.onNodeWithText(fontLabel).assertDoesNotExist()
        composeRule.onNodeWithText(cacheLabel).performScrollTo().performClick()
        composeRule.onNodeWithText(pageLabel).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(resourcesLabel).performScrollTo().performClick()
        composeRule.onNodeWithText(fontLabel).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(pageLabel).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(cacheLabel).performScrollTo().performClick()
        composeRule.onNodeWithText(pageLabel).assertDoesNotExist()
        composeRule.onNodeWithText(fontLabel).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(resourcesLabel).performScrollTo().performClick()
        composeRule.onNodeWithText(fontLabel).assertDoesNotExist()
        composeRule.onNodeWithText(formatBytes(15L * 1024L)).performScrollTo().assertIsDisplayed()
    }
}
