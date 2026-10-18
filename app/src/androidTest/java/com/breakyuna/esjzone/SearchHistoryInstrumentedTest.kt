package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.database.entity.SearchHistory
import com.breakyuna.esjzone.ui.tab.SearchHistoryList
import com.breakyuna.esjzone.ui.tab.SearchHistoryModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchHistoryInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun largeHistoryKeepsNewestFirstAndOldestReachableWithoutComposingEverything() {
        val histories = (0 until 240).map { index ->
            SearchHistory(index = index + 1, keyword = "关键词 $index", time = index.toString().padStart(6, '0'))
        }
        var selected: String? = null
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.size(320.dp, 400.dp)) {
                    SearchHistoryList(
                        state = SearchHistoryModel.State(histories = histories, loading = false),
                        onClear = {},
                        onSelect = { selected = it },
                        modifier = Modifier.fillMaxSize().testTag("search-history")
                    )
                }
            }
        }
        composeRule.onNodeWithText("关键词 239").assertIsDisplayed()
        composeRule.onNodeWithText("关键词 0").assertDoesNotExist()
        composeRule.onNodeWithTag("search-history").performScrollToNode(hasText("关键词 120"))
        composeRule.onNodeWithText("关键词 120").assertIsDisplayed()
        composeRule.onNodeWithTag("search-history").performScrollToNode(hasText("关键词 0"))
        composeRule.onNodeWithText("关键词 0").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("关键词 0", selected) }
    }
}
