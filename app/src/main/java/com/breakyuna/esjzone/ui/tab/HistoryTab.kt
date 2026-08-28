package com.breakyuna.esjzone.ui.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getHistories
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.page.ChapterPage
import com.breakyuna.esjzone.ui.page.HistoryPage

/** The history tab can be reselected twice to jump straight to the latest location. */
object HistoryTab : Tab {

    private val openLastReadingRequest = mutableIntStateOf(0)

    private fun readResolve(): Any = HistoryTab

    internal fun requestOpenLastReading() {
        openLastReadingRequest.intValue += 1
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 1u,
            title = stringResource(id = R.string.history),
            icon = rememberVectorPainter(image = Icons.Filled.History)
        )

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val request = openLastReadingRequest.intValue
        var handledRequest by rememberSaveable { mutableIntStateOf(0) }
        var opening by remember { mutableStateOf(false) }

        HistoryPage.Content(showBack = false)

        LaunchedEffect(request) {
            if (request == 0 || request == handledRequest || opening) return@LaunchedEffect
            handledRequest = request
            opening = true
            try {
                val latest = withContext(Dispatchers.IO) {
                    EsjzoneClient.getHistories(authorization).firstOrNull()
                }
                if (latest != null) {
                    val novel = withContext(Dispatchers.IO) {
                        EsjzoneClient.getNovelDetail(authorization, latest)
                    }
                    navigator?.push(
                        ChapterPage(
                            novelId = novel.id().ifBlank { latest.chapter.novelId() },
                            chapter = latest.chapter,
                            history = mutableStateOf(latest.chapter),
                            chapterOrder = novel.chapterList.orderedChapters
                        )
                    )
                }
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e(
                    "HistoryTab",
                    "Failed to open the latest reading location",
                    e
                )
            } finally {
                opening = false
            }
        }
    }
}
