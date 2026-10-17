package com.breakyuna.esjzone

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.database.ReadingStatisticsSummary
import com.breakyuna.esjzone.ui.designsystem.globalStringResource
import com.breakyuna.esjzone.ui.page.ReadingHeatmap
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingHeatmapInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun startsAtLatestWeekAndLoadsOlderDaysWhenScrolled() {
        val today = LocalDate.of(2026, 10, 17)
        val firstDate = today.minusYears(1).plusDays(1)
        val summary = ReadingStatisticsSummary(emptyList(), today)
        var todayDescription = ""
        var firstDescription = ""
        var clickedDate: LocalDate? = null

        composeRule.setContent {
            val duration = globalStringResource(R.string.reading_stats_zero_minutes)
            todayDescription = globalStringResource(R.string.reading_stats_day_detail, today.toString(), duration)
            firstDescription = globalStringResource(R.string.reading_stats_day_detail, firstDate.toString(), duration)
            var selectedDate by remember { mutableStateOf(today) }
            MaterialTheme {
                Column(Modifier.width(220.dp)) {
                    ReadingHeatmap(summary, selectedDate) {
                        selectedDate = it
                        clickedDate = it
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription(todayDescription).assertIsDisplayed().assertIsSelected()
        assertEquals(
            composeRule.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot.right,
            composeRule.onNodeWithContentDescription(todayDescription).fetchSemanticsNode().boundsInRoot.right,
            1f
        )
        composeRule.onNodeWithContentDescription(firstDescription).assertDoesNotExist()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        composeRule.onNodeWithContentDescription(firstDescription).assertIsDisplayed().performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(firstDate, clickedDate) }
        composeRule.onNodeWithContentDescription(todayDescription).assertDoesNotExist()
    }

    @Test
    fun latestWeeksMonthLabelRemainsFullyVisibleWithLargerText() {
        val today = LocalDate.of(2026, 11, 1)
        val summary = ReadingStatisticsSummary(emptyList(), today)
        composeRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.ENGLISH) }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(typography = MaterialTheme.typography.copy(
                    labelSmall = MaterialTheme.typography.labelSmall.copy(fontSize = 20.sp)
                )) {
                    Column(Modifier.width(220.dp)) {
                        ReadingHeatmap(summary, today) {}
                    }
                }
            }
        }
        val label = composeRule.onNodeWithText("Nov").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        val bounds = label.fetchSemanticsNode().boundsInRoot
        val viewport = composeRule.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        assertEquals("The entire month label must remain visible", layouts.single().size.width.toFloat(),
            bounds.width, 1f)
        assertTrue(bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f)
    }
}
