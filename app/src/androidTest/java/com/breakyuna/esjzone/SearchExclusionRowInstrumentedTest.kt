package com.breakyuna.esjzone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.discovery.DiscoverySearchTopBar
import com.breakyuna.esjzone.ui.discovery.buildSearchTagExpression
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchExclusionRowInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun exclusionRowAppendsBangAndPlusWithoutRewritingTheQuery() {
        var submitted: String? = null
        composeRule.setContent {
            MaterialTheme {
                var query by remember { mutableStateOf("作品名") }
                var excluded by remember { mutableStateOf("") }
                var expanded by remember { mutableStateOf(false) }
                DiscoverySearchTopBar(
                    query = query,
                    onQueryChange = { query = it },
                    excluded = excluded,
                    onExcludedChange = { excluded = it },
                    tagRowExpanded = expanded,
                    onTagRowExpandedChange = { expanded = it },
                    onSearch = { submitted = buildSearchTagExpression(query, excluded).query },
                    onClear = { query = "" },
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithTag("search-tag-toggle").performClick()
        composeRule.onNodeWithTag("search-tag-exclude").performTextInput("TS 校园")
        composeRule.onNodeWithTag("search-tag-exclude").assertTextContains("!TS+!校园", substring = true)
        composeRule.onNodeWithTag("search-submit").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals("作品名+!TS+!校园", submitted) }
    }

    @Test fun invalidExclusionPrefixesCannotBeSubmitted() {
        composeRule.setContent {
            MaterialTheme {
                var excluded by remember { mutableStateOf("") }
                var expanded by remember { mutableStateOf(true) }
                DiscoverySearchTopBar(
                    query = "",
                    onQueryChange = {},
                    excluded = excluded,
                    onExcludedChange = { excluded = it },
                    tagRowExpanded = expanded,
                    onTagRowExpandedChange = { expanded = it },
                    onSearch = {},
                    onClear = {},
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithTag("search-tag-exclude").performTextInput("!!TS")
        composeRule.onNodeWithTag("search-submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("search-tag-exclude").performTextClearance()
        composeRule.onNodeWithTag("search-tag-exclude").performTextInput("TS")
        composeRule.onNodeWithTag("search-submit").assertIsEnabled()
    }
}
