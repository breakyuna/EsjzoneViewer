package com.breakyuna.esjzone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.ui.page.ReaderUnderlinesModel
import com.breakyuna.esjzone.ui.reader.ReaderShell
import com.breakyuna.esjzone.ui.reader.ReaderUnderlineText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderUnderlineInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun corruptChapterDoesNotStopObservationOfValidChapterUpdates() {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),
            GeneralDatabase::class.java).build()
        val model = ReaderUnderlinesModel(database)
        val owner = ViewModelStore().apply { put("underlines", model) }
        val key = ReaderUnderlines.KEY_PREFIX + "good"
        val mark = ReaderUnderline(0, "a".repeat(64), 0, 5)
        var observed = emptyMap<String, List<ReaderUnderline>>()
        try {
            runBlocking(Dispatchers.IO) {
                database.cacheDao().putAtomic(ReaderUnderlines.KEY_PREFIX + "bad", "null")
                database.cacheDao().putAtomic(key, ReaderUnderlines.encode(listOf(mark)))
            }
            composeRule.setContent {
                val rows by model.underlines.collectAsState()
                observed = rows
            }
            composeRule.waitUntil(5_000) { observed["good"] == listOf(mark) }
            composeRule.runOnIdle { assertTrue(observed["bad"].orEmpty().isEmpty()) }
            val updated = mark.copy(end = 8)
            runBlocking { withContext(Dispatchers.IO) {
                database.cacheDao().putAtomic(key, ReaderUnderlines.encode(listOf(updated)))
            } }
            composeRule.waitUntil(5_000) { observed["good"] == listOf(updated) }
        } finally {
            composeRule.runOnIdle { owner.clear() }
            database.close()
        }
    }

    @Test fun selectionMenuAppearsNearTheSelectedLineInALongParagraph() {
        val text = "Line 1\nLine 2\nLine 3\nSelect target\nLine 5\nLine 6\nLine 7\nLine 8"
        var copyLabel = ""
        var density = 1f
        composeRule.setContent {
            copyLabel = com.breakyuna.esjzone.ui.designsystem.globalStringResource(android.R.string.copy)
            density = androidx.compose.ui.platform.LocalDensity.current.density
            MaterialTheme {
                ReaderUnderlineText(AnnotatedString(text), emptyMap(),
                    TextStyle(fontSize = 18.sp, lineHeight = 24.sp), Color.Black,
                    Modifier.testTag("text"), true, 0, "a".repeat(64),
                    underlines = emptyList(), onUnderline = { _, _ -> })
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag("text").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val bounds = layouts.single().getBoundingBox(text.indexOf("target"))
        composeRule.onNodeWithTag("text").performTouchInput { longClick(bounds.center) }
        val paragraphTop = composeRule.onNodeWithTag("text").fetchSemanticsNode().boundsInRoot.top
        val menuTextTop = composeRule.onNodeWithText(copyLabel).fetchSemanticsNode().boundsInRoot.top
        assertTrue(menuTextTop >= paragraphTop + bounds.bottom)
        assertTrue(menuTextTop < paragraphTop + bounds.bottom + 72.dp.value * density)
    }

    @Test fun longPressSavesAndRemovesWithoutTurningPageOrOpeningToolbar() {
        var marks by mutableStateOf(emptyList<ReaderUnderline>())
        var saveLabel = ""
        var removeLabel = ""
        var taps = 0
        lateinit var pagerState: PagerState
        composeRule.setContent {
            saveLabel = com.breakyuna.esjzone.ui.designsystem.globalStringResource(R.string.reader_save_underline)
            removeLabel = com.breakyuna.esjzone.ui.designsystem.globalStringResource(R.string.reader_remove_underline)
            pagerState = rememberPagerState { 2 }
            MaterialTheme {
                ReaderShell(background = Color.White, onReadingAreaTap = { _, _ -> taps++ },
                    pagedGesturesEnabled = true) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        if (page == 0) {
                            ReaderUnderlineText(AnnotatedString("Hello reader"), emptyMap(), TextStyle(fontSize = 22.sp),
                                Color.Black, Modifier.testTag("text"), true, 2, "a".repeat(64), offset = 10,
                                underlines = marks, onUnderline = { mark, remove ->
                                    marks = ReaderUnderlines.update(marks, mark, remove)
                                })
                        } else Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        composeRule.onNodeWithTag("text").performTouchInput { longClick(center) }
        composeRule.onNodeWithText(saveLabel).performClick()
        composeRule.runOnIdle {
            assertEquals(1, marks.size)
            assertEquals(2, marks.single().blockIndex)
            assertTrue(marks.single().start >= 10)
            assertEquals(0, taps)
            assertEquals(0, pagerState.currentPage)
        }
        composeRule.onNodeWithTag("text").performTouchInput { longClick(center) }
        composeRule.onNodeWithText(removeLabel).performClick()
        composeRule.runOnIdle { assertTrue(marks.isEmpty()) }
    }
}
