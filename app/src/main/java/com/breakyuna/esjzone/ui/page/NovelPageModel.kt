package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess

import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.database.ReadingStatisticsRecorder
import com.breakyuna.esjzone.database.NovelTitleRepository
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.novellibrary.novel.preferredNovelTitle
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.util.AppLogger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Displays downloaded detail immediately, then refreshes it without hiding readable content. */
class NovelPageModel(
    private val authorization: Authorization,
    private val novel: Novel
) : AppStateViewModel<NovelPageModel.State>(State.Loading) {

    private val detailLoadLock = Any()
    private var detailLoadStarted = false
    private val favoriteIntentVersion = AtomicLong(0L)
    private val mutableCleanupState = MutableStateFlow(CleanupState.Idle)
    val cleanupState = mutableCleanupState.asStateFlow()

    enum class CleanupState { Idle, Offered, Cleaning, Completed, Failed }

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
            val downloaded = try {
                PresentationAccess.downloads.readDetailedNovel(novel.url)?.let { stored ->
                    val title = NovelTitleRepository.resolve(null, stored.id(), stored.url, novel.name)
                    NovelTitleRepository.repair(stored.id(), stored.url, title)
                    stored.copy(name = preferredNovelTitle(title, stored.name, stored.id(), stored.url))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("NovelPageModel", "Failed to read downloaded detail", error)
                null
            }
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
                val useDownloadedCatalog = fetchedDetail.chapterList.orderedChapters.isEmpty() && downloaded != null
                val detail = if (useDownloadedCatalog) {
                    val offlineDetail = requireNotNull(downloaded)
                    offlineDetail.copy(name = preferredNovelTitle(
                        fetchedDetail.name, offlineDetail.name, offlineDetail.id(), offlineDetail.url
                    ))
                } else fetchedDetail
                ensureActive()
                mutableState.value = State.Result(detail, fromDownload = useDownloadedCatalog)
                ReadingStatisticsRecorder.recordTags(detail)
                NovelTitleRepository.repair(detail.id(), detail.url, detail.name)
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
        val version = favoriteIntentVersion.incrementAndGet()
        if (mutableCleanupState.value != CleanupState.Cleaning) {
            mutableCleanupState.value = CleanupState.Idle
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BookshelfRepository.setFavorite(
                    authorization = authorization,
                    novel = favoriteSource(),
                    desired = desired
                )
                if (!desired && favoriteIntentVersion.get() == version) {
                    val hasLocalContent = PresentationAccess.downloads.storageBytes(novel.url) > 0L ||
                        PresentationAccess.client.hasCachedNovelPages(authorization, localPageUrls())
                    ensureActive()
                    if (hasLocalContent && favoriteIntentVersion.get() == version &&
                        mutableCleanupState.value != CleanupState.Cleaning) {
                        mutableCleanupState.value = CleanupState.Offered
                    }
                }
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

    fun dismissCleanup() {
        if (mutableCleanupState.value == CleanupState.Offered ||
            mutableCleanupState.value == CleanupState.Failed) {
            mutableCleanupState.value = CleanupState.Idle
        }
    }

    fun clearLocalContent() {
        if (mutableCleanupState.value != CleanupState.Offered &&
            mutableCleanupState.value != CleanupState.Failed) return
        mutableCleanupState.value = CleanupState.Cleaning
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Read the manifest before deleting it, including chapters no longer in the live TOC.
                val urls = localPageUrls()
                val downloadsCleared = PresentationAccess.downloads.delete(novel.url)
                val pagesCleared = PresentationAccess.client.clearCachedNovelPages(authorization, urls)
                mutableCleanupState.value = if (downloadsCleared && pagesCleared) {
                    CleanupState.Completed
                } else CleanupState.Failed
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("NovelPageModel", "Failed to clear novel local content", error)
                mutableCleanupState.value = CleanupState.Failed
            }
        }
    }

    private fun localPageUrls(): List<String> = buildList {
        add(novel.url)
        (mutableState.value as? State.Result)?.detailed?.chapterList?.orderedChapters
            ?.forEach { add(it.url) }
        PresentationAccess.downloads.manifest(novel.url)?.chapters?.forEach { add(it.url) }
    }

    private fun favoriteSource(): Novel {
        val detailed = (state.value as? State.Result)?.detailed
        val title = preferredNovelTitle(
            detailed?.name.orEmpty(), novel.name, detailed?.id().orEmpty(), novel.url
        )
        return if (novel is CoveredNovel) {
            object : CoveredNovel by novel {
                override val name = title
            }
        } else FavoriteNovel(title, novel.url)
    }

    fun seedFavoriteMetadata(author: String, coverUrl: String, isAdult: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BookshelfRepository.seedRemoteFavorite(
                    authorization = authorization,
                    novel = favoriteSource(),
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
