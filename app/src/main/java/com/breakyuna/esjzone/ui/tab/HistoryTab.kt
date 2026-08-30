package com.breakyuna.esjzone.ui.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.page.ChapterPage
import com.breakyuna.esjzone.ui.page.HistoryPage

/** The history tab can be reselected twice to jump straight to the latest location. */
object HistoryTab : Tab {

    private val openLastReadingRequest = mutableIntStateOf(0)
    private val requestLock = Any()
    private var lastHandledOpenLastReadingRequest = 0

    private fun readResolve(): Any = HistoryTab

    internal fun requestOpenLastReading() {
        openLastReadingRequest.intValue += 1
    }

    private fun claimOpenLastReadingRequest(request: Int): Boolean = synchronized(requestLock) {
        if (request == 0 || request <= lastHandledOpenLastReadingRequest) {
            false
        } else {
            lastHandledOpenLastReadingRequest = request
            true
        }
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 2u,
            title = stringResource(id = R.string.history),
            icon = rememberVectorPainter(image = Icons.Filled.History)
        )

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val request = openLastReadingRequest.intValue

        HistoryPage.Content(showBack = false)

        LaunchedEffect(request) {
            if (!claimOpenLastReadingRequest(request)) return@LaunchedEffect
            try {
                val latest = withContext(Dispatchers.IO) {
                    MainActivity.database.localReadingActivityDao().getLatest()
                }
                if (latest != null) {
                    openLocalActivity(navigator, latest)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e(
                    "HistoryTab",
                    "Failed to open the latest reading location",
                    e
                )
            }
        }
    }
}

private fun openLocalActivity(
    navigator: Navigator?,
    activity: LocalReadingActivity
) {
    val chapter = Chapter(
        activity.chapterName,
        activity.chapterUrl,
        true
    )
    navigator?.pushIfNotCurrent(
        ChapterPage(
            novelId = activity.novelId.ifBlank { chapter.novelId() },
            chapter = chapter,
            history = ChapterStateHolder(chapter),
            novelName = activity.novelName,
            novelUrl = activity.novelUrl,
            novelCoverUrl = activity.novelCoverUrl
        )
    )
}
