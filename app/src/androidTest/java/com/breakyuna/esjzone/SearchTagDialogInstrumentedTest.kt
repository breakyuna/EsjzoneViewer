package com.breakyuna.esjzone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.discovery.SearchTagDialog
import com.breakyuna.esjzone.ui.designsystem.LocalGlobalScript
import com.breakyuna.esjzone.ui.reader.ReaderScript
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchTagDialogInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun previewAndSubmitKeepTheExistingSearchAndBothKindsOfTags() {
        var submitted: String? = null
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalGlobalScript provides ReaderScript.TRADITIONAL) {
                    SearchTagDialog(currentQuery = "作品名", onDismiss = {}, onSearch = { submitted = it })
                }
            }
        }
        composeRule.onNodeWithTag("search-tag-include").performTextInput("后宫 校园")
        composeRule.onNodeWithTag("search-tag-exclude").performTextInput("TS")
        composeRule.onNodeWithTag("search-tag-preview").assertTextContains("作品名 后宫 校园 !TS", substring = true)
        composeRule.onNodeWithTag("search-tag-submit").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals("作品名 后宫 校园 !TS", submitted) }
    }

    @Test fun invalidExclusionPrefixesCannotBeSubmitted() {
        composeRule.setContent {
            MaterialTheme { SearchTagDialog(currentQuery = "", onDismiss = {}, onSearch = {}) }
        }
        composeRule.onNodeWithTag("search-tag-include").performTextInput("!TS")
        composeRule.onNodeWithTag("search-tag-submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("search-tag-include").performTextClearance()
        composeRule.onNodeWithTag("search-tag-exclude").performTextInput("!!TS")
        composeRule.onNodeWithTag("search-tag-submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("search-tag-exclude").performTextClearance()
        composeRule.onNodeWithTag("search-tag-exclude").performTextInput("TS")
        composeRule.onNodeWithTag("search-tag-submit").assertIsEnabled()
    }
}
