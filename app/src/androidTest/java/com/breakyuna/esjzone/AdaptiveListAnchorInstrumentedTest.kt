package com.breakyuna.esjzone

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.ui.designsystem.PreserveAdaptiveListAnchor
import com.breakyuna.esjzone.ui.designsystem.adaptiveItems
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveListAnchorInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var listState: LazyListState
    private val books = (0 until 80).map { "book:$it" }

    @Composable
    private fun Records(columns: Int, records: List<String> = books) {
        listState = rememberLazyListState()
        PreserveAdaptiveListAnchor(listState, records, columns, leadingItemCount = 1, key = { it })
        MaterialTheme {
            LazyColumn(state = listState, modifier = Modifier.size(300.dp, 96.dp)) {
                item(key = "filters") { Text("Filters", Modifier.height(48.dp)) }
                adaptiveItems(records, columns, key = { it }) {
                    Text(it, Modifier.height(48.dp).testTag(it))
                }
                item(key = "pagination") { Text("More", Modifier.height(48.dp)) }
            }
        }
    }

    private fun scrollTo(index: Int) {
        composeRule.runOnIdle { listState.requestScrollToItem(index, 7) }
        composeRule.waitForIdle()
    }

    private fun assertAnchor(row: Int) {
        composeRule.runOnIdle {
            assertEquals(row, listState.firstVisibleItemIndex)
            assertEquals(7, listState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun oneTwoAndThreeColumnsKeepTheFirstVisibleRecordAndOffset() {
        val columns = mutableIntStateOf(1)
        composeRule.setContent { Records(columns.intValue) }
        scrollTo(22) // One header followed by book 21; it lands in the second cell of a row.
        composeRule.runOnIdle { columns.intValue = 2 }
        assertAnchor(11)
        composeRule.onNodeWithTag("book:21").assertIsDisplayed()
        composeRule.runOnIdle { columns.intValue = 3 }
        assertAnchor(7) // The preceding row started at book 20.
        composeRule.onNodeWithTag("book:20").assertIsDisplayed()
        composeRule.runOnIdle { columns.intValue = 1 }
        assertAnchor(19) // The three-column row started at book 18.
        composeRule.onNodeWithTag("book:18").assertIsDisplayed()
    }

    @Test
    fun reflowLocatesTheRecordByIdentityWhenTheRecordsAlsoReorder() {
        val columns = mutableIntStateOf(1)
        val records = mutableStateOf(books)
        composeRule.setContent { Records(columns.intValue, records.value) }
        scrollTo(22)
        composeRule.runOnIdle {
            records.value = books.reversed()
            columns.intValue = 2
        }
        assertAnchor(30) // Book 21 is now record 58, after the header.
        composeRule.onNodeWithTag("book:21").assertIsDisplayed()
    }

    @Test
    fun recreationWithNewColumnsRestoresTheRecordInsteadOfTheOldRow() {
        val restoration = StateRestorationTester(composeRule)
        var columns = 1
        restoration.setContent { Records(columns) }
        scrollTo(22)
        // Change the configuration read by the new composition without reflowing the old one.
        columns = 2
        restoration.emulateSavedInstanceStateRestore()
        assertAnchor(11)
        composeRule.onNodeWithTag("book:21").assertIsDisplayed()
    }

    @Test
    fun shortListsAndVisibleHeadersDoNotJumpToTheBottom() {
        val columns = mutableIntStateOf(1)
        composeRule.setContent { Records(columns.intValue, books.take(26)) }
        scrollTo(21)
        composeRule.runOnIdle { columns.intValue = 2 }
        assertAnchor(11)
        composeRule.onNodeWithTag("book:20").assertIsDisplayed()
        scrollTo(0)
        composeRule.runOnIdle { columns.intValue = 3 }
        assertAnchor(0)
    }
}
