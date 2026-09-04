package com.breakyuna.esjzone.ui.page

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Loads detail data and falls back to an offline manifest without UI coupling. */
class NovelPageModel(
    private val authorization: Authorization,
    private val novel: Novel
) : StateScreenModel<NovelPageModel.State>(State.Loading) {

    private val detailLoadLock = Any()
    private var detailLoadStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val detailed: DetailedNovel) : State()
    }

    fun getDetail() {
        synchronized(detailLoadLock) {
            if (detailLoadStarted) return
            detailLoadStarted = true
        }
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val fetchedDetail = EsjzoneClient.getNovelDetail(
                    authorization = authorization,
                    novel = novel,
                    includeComments = false
                )
                val detail = if (fetchedDetail.chapterList.orderedChapters.isEmpty()) {
                    NovelDownloadStore.readDetailedNovel(novel.url) ?: fetchedDetail
                } else {
                    fetchedDetail
                }
                ensureActive()
                mutableState.value = State.Result(detail)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val downloaded = NovelDownloadStore.readDetailedNovel(novel.url)
                if (downloaded != null) {
                    mutableState.value = State.Result(downloaded)
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
        screenModelScope.launch(Dispatchers.IO) {
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
        screenModelScope.launch(Dispatchers.IO) {
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
