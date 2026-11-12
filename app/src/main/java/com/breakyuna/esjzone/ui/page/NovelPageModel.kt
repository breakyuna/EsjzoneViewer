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
import com.breakyuna.esjzone.network.features.getCachedNovelDetail
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
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
        data class Result(
            val detailed: DetailedNovel,
            val fromDownload: Boolean = false,
            val refreshing: Boolean = false,
            val refreshFailure: LoadFailureKind? = null
        ) : State()
    }

    fun getDetail(forceRefresh: Boolean = false) {
        synchronized(detailLoadLock) {
            if (detailLoadStarted) return
            detailLoadStarted = true
        }
        viewModelScope.launch(Dispatchers.IO) {
            val previous = mutableState.value as? State.Result
            val cached = localOrNull { PresentationAccess.client.getCachedNovelDetail(authorization, novel) }
            val downloaded = localOrNull { PresentationAccess.downloads.readDetailedNovel(novel.url) }
            val local = cached ?: downloaded ?: localOrNull { basicLocalDetail() }
            val normalizedLocal = local?.copy(name = preferredNovelTitle(local.name, novel.name, local.id(), local.url))
            val fallback = previous ?: normalizedLocal?.let { State.Result(it, fromDownload = cached == null) }
            ensureActive()
            mutableState.value = fallback?.copy(refreshing = true, refreshFailure = null) ?: State.Loading
            try {
                val fetchedDetail = cancellablePageRequest {
                    PresentationAccess.client.getNovelDetail(
                        authorization = authorization,
                        novel = novel,
                        includeComments = false,
                        forceRefresh = forceRefresh,
                        awaitFresh = fallback != null
                    )
                }
                val useDownloadedCatalog = fetchedDetail.chapterList.orderedChapters.isEmpty() && fallback?.detailed?.chapterList?.orderedChapters?.isNotEmpty() == true
                val detail = if (useDownloadedCatalog) {
                    val offlineDetail = requireNotNull(fallback).detailed
                    offlineDetail.copy(name = preferredNovelTitle(
                        fetchedDetail.name, offlineDetail.name, offlineDetail.id(), offlineDetail.url
                    ))
                } else fetchedDetail
                ensureActive()
                mutableState.value = State.Result(detail, fromDownload = useDownloadedCatalog)
                localOrNull { PresentationAccess.downloads.retainNovelDetail(fetchedDetail) }
                localOrNull { ReadingStatisticsRecorder.recordTags(detail) }
                try {
                    NovelTitleRepository.repair(detail.id(), detail.url, detail.name)
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { AppLogger.w("NovelPageModel", "Unable to repair title", error) }
                try {
                    BookshelfRepository.updateTitleFromDetail(authorization, fetchedDetail)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.w("NovelPageModel", "Failed to update bookshelf title", error)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                if (fallback != null) {
                    mutableState.value = fallback.copy(refreshing = false, refreshFailure = error.loadFailureKind())
                    AppLogger.w("NovelPageModel", "Keeping local novel detail for ${novel.name}", error)
                } else {
                    mutableState.value = State.Error(error.loadFailureKind())
                    AppLogger.e("NovelPageModel", "Failed to load novel detail for ${novel.name}", error)
                }
            }
        }
    }

    private inline fun <T> localOrNull(read: () -> T): T? = try {
        read()
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        AppLogger.w("NovelPageModel", "Unable to read or retain local detail", error)
        null
    }

    /** A known reading anchor must remain usable even if all detail caches were evicted. */
    private suspend fun basicLocalDetail(): DetailedNovel? {
        val id = BookshelfRepository.novelIdFor(novel.url)
        val db = PresentationAccess.database
        val activity = db.localReadingActivityDao().getLatestForIdentity(id, novel.url)
        val shelf = db.bookshelfDao().find(BookshelfRepository.scopeFor(authorization), BookshelfRepository.keyFor(novel.url))
        if (activity == null && shelf == null) return null
        val anchor = activity?.chapterUrl?.takeIf(String::isNotBlank)?.let {
            Chapter(activity.chapterName, it, true)
        }
        return DetailedNovel(
            name = preferredNovelTitle(novel.name, shelf?.title ?: activity?.novelName.orEmpty(), id, novel.url),
            url = novel.url,
            coverUrl = (novel as? CoveredNovel)?.coverUrl?.takeIf(String::isNotBlank)
                ?: shelf?.coverUrl ?: activity?.novelCoverUrl.orEmpty(),
            views = 0, likes = 0, words = 0, type = "", author = shelf?.author.orEmpty(), forumUrl = "",
            tags = emptyList(), isAdult = shelf?.isAdult ?: (activity?.isAdult == true),
            isFavorite = shelf?.visible == true, description = NovelDescription(emptyList()),
            chapterList = NovelChapterList(listOfNotNull(anchor?.let(::ChapterItem))),
            adultStatusKnown = shelf != null || activity?.isAdult != null,
            chapterOrderComplete = false
        )
    }

    fun retry() {
        synchronized(detailLoadLock) { detailLoadStarted = false }
        getDetail(forceRefresh = true)
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
                    val hasLocalContent = PresentationAccess.downloads.hasLocalContent(novel.url) ||
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
