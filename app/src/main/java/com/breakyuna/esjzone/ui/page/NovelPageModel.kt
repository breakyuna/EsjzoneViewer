package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess

import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.database.ReadingStatisticsRecorder
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Displays downloaded detail immediately, then refreshes it without hiding readable content. */
class NovelPageModel(
    private val authorization: Authorization,
    private val novel: Novel
) : AppStateViewModel<NovelPageModel.State>(State.Loading) {

    private val detailLoadLock = Any()
    private var detailLoadStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val detailed: DetailedNovel, val fromDownload: Boolean = false) : State()
    }

    fun getDetail() {
        synchronized(detailLoadLock) {
            if (detailLoadStarted) return
            detailLoadStarted = true
        }
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            val downloaded = runCatching { PresentationAccess.downloads.readDetailedNovel(novel.url) }
                .onFailure { AppLogger.w("NovelPageModel", "Failed to read downloaded detail", it) }
                .getOrNull()
            ensureActive()
            if (downloaded != null) mutableState.value = State.Result(downloaded, fromDownload = true)
            try {
                val fetchedDetail = cancellablePageRequest {
                    PresentationAccess.client.getNovelDetail(
                        authorization = authorization,
                        novel = novel,
                        includeComments = false
                    )
                }
                val detail = if (fetchedDetail.chapterList.orderedChapters.isEmpty()) {
                    downloaded ?: fetchedDetail
                } else {
                    fetchedDetail
                }
                ensureActive()
                mutableState.value = State.Result(detail, fromDownload = detail === downloaded)
                ReadingStatisticsRecorder.recordTags(detail)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                if (downloaded != null) {
                    mutableState.value = State.Result(downloaded, fromDownload = true)
                    ReadingStatisticsRecorder.recordTags(downloaded)
                    AppLogger.w("NovelPageModel", "Using downloaded novel detail for ${novel.name}", error)
                } else {
                    mutableState.value = State.Error(error.loadFailureKind())
                    AppLogger.e("NovelPageModel", "Failed to load novel detail for ${novel.name}", error)
                }
            }
        }
    }

    fun retry() {
        synchronized(detailLoadLock) { detailLoadStarted = false }
        getDetail()
    }

    fun persistFavorite(desired: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BookshelfRepository.setFavorite(
                    authorization = authorization,
                    novel = novel,
                    desired = desired
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(
                    "NovelPageModel",
                    "Failed to persist favorite intent for ${novel.name}",
                    error
                )
            }
        }
    }

    fun seedFavoriteMetadata(author: String, coverUrl: String, isAdult: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BookshelfRepository.seedRemoteFavorite(
                    authorization = authorization,
                    novel = novel,
                    author = author,
                    coverUrl = coverUrl,
                    isAdult = isAdult
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("NovelPageModel", "Failed to seed favorite metadata for ${novel.name}", error)
            }
        }
    }
}
