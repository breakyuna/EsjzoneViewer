package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess

import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.database.ReadingStatisticsRecorder
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.external.CloudflareChallengeRequiredException
import com.breakyuna.esjzone.network.external.CloudflareWebViewUnavailableException
import com.breakyuna.esjzone.network.external.WenkuCookieStoreUnavailableException
import com.breakyuna.esjzone.network.wenku8.Wenku8ParseException
import com.breakyuna.esjzone.network.wenku8.Wenku8RestrictedException
import com.breakyuna.esjzone.network.wenku8.Wenku8Urls
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Loads detail data and falls back to an offline manifest without UI coupling. */
class NovelPageModel(
    private val authorization: Authorization,
    private val novel: Novel
) : AppStateViewModel<NovelPageModel.State>(State.Loading) {

    private val detailLoadLock = Any()
    private var detailLoadStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind, val message: Int? = null,
            val requiresVerification: Boolean = false) : State()
        data class Result(val detailed: DetailedNovel) : State()
    }

    fun getDetail(forceRefresh: Boolean = false) {
        synchronized(detailLoadLock) {
            if (detailLoadStarted) return
            detailLoadStarted = true
        }
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val fetchedDetail = cancellablePageRequest {
                    PresentationAccess.client.getNovelDetail(authorization = authorization, novel = novel,
                        includeComments = false, forceRefresh = forceRefresh)
                }
                val detail = if (fetchedDetail.chapterList.orderedChapters.isEmpty()) {
                    PresentationAccess.downloads.readDetailedNovel(novel.url) ?: fetchedDetail
                } else {
                    fetchedDetail
                }
                ensureActive()
                ReadingStatisticsRecorder.recordTags(detail)
                mutableState.value = State.Result(detail)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val downloaded = PresentationAccess.downloads.readDetailedNovel(novel.url)
                if (downloaded != null) {
                    ReadingStatisticsRecorder.recordTags(downloaded)
                    mutableState.value = State.Result(downloaded)
                    AppLogger.w("NovelPageModel", "Using downloaded novel detail for ${novel.name}", error)
                } else {
                    val message = if (Wenku8Urls.detailIdentity(novel.url) != null) when (error) {
                        is CloudflareChallengeRequiredException -> R.string.wenku8_verification_needed
                        is CloudflareWebViewUnavailableException -> R.string.wenku_webview_unavailable
                        is WenkuCookieStoreUnavailableException -> R.string.wenku_cookie_store_unavailable
                        is Wenku8ParseException -> R.string.wenku8_page_unrecognized
                        is Wenku8RestrictedException -> R.string.wenku8_content_restricted
                        else -> null
                    } else null
                    mutableState.value = State.Error(error.loadFailureKind(), message,
                        requiresVerification = error is CloudflareChallengeRequiredException)
                    AppLogger.e("NovelPageModel", "Failed to load novel detail for ${novel.name}", error)
                }
            }
        }
    }

    fun retry() {
        synchronized(detailLoadLock) { detailLoadStarted = false }
        getDetail(forceRefresh = true)
    }

    fun persistFavorite(desired: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BookshelfRepository.setFavorite(
                    authorization = authorization,
                    novel = (state.value as? State.Result)?.detailed ?: novel,
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
                    novel = (state.value as? State.Result)?.detailed ?: novel,
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
