package com.breakyuna.esjzone.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.features.CloudViewReports
import kotlinx.coroutines.delay

@Composable
internal fun ChapterViewReportEffect(
    activeChapterKey: String?,
    activeChapter: ReaderChapter?,
    authorization: Authorization,
    novelId: String,
    chapterPageModel: ChapterPageModel,
    resolveBookUrl: () -> String
) {
    val currentBookUrl by rememberUpdatedState(resolveBookUrl)
    LaunchedEffect(
        activeChapterKey,
        authorization,
        chapterPageModel.networkFetchGeneration.value
    ) {
        val entry = activeChapter ?: return@LaunchedEffect
        if (entry.requiresPassword) return@LaunchedEffect
        delay(750)
        val bookId = novelId.ifBlank { entry.chapter.novelId() }
        val bookUrl = currentBookUrl()
        val fetchedGeneration = chapterPageModel.takeFreshNetworkFetch(entry.chapter)
        if (CloudViewReports.fetchIsCurrent(
                authorization, bookId, bookUrl, entry.chapter.url, fetchedGeneration
            )) {
            CloudViewReports.noteFetched(
                authorization, bookId, bookUrl, entry.chapter.url
            )
        } else {
            CloudViewReports.noteLocal(
                authorization, bookId, bookUrl, entry.chapter.url
            )
        }
    }
}
