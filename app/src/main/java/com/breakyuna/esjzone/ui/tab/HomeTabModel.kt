package com.breakyuna.esjzone.ui.tab

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.network.features.HomeDataCache
import com.breakyuna.esjzone.network.features.getHomeData
import com.breakyuna.esjzone.network.features.novels
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

internal const val RANDOM_RECOMMENDATION_BATCH_SIZE = 24
internal const val RANDOM_RECOMMENDATION_MAX_PAGES_PER_BATCH = 3
internal const val RANDOM_INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L

class HomeTabModel(
    private val authorization: Authorization
) : AppStateViewModel<HomeTabModel.State>(
    HomeDataCache.readSnapshot(authorization.domain)?.let { State.Result(it) } ?: State.Loading
) {

    private var loadStarted = false
    private var randomLoadJob: Job? = null
    private var inactivityJob: Job? = null
    private var randomRequester: PageableRequester<CoveredNovel>? = null
    private var randomAdultMode: Boolean? = null
    private val randomVisitedPages = mutableSetOf<Int>()
    private val randomSeenNovelKeys = mutableSetOf<String>()
    private val _randomRecommendations = MutableStateFlow(RandomRecommendationsState())
    val randomRecommendations = _randomRecommendations.asStateFlow()

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(
            val homeData: HomeData,
            val isSyncing: Boolean = false
        ) : State()
    }

    data class RandomRecommendationsState(
        val items: List<CoveredNovel> = emptyList(),
        val isLoading: Boolean = false,
        val failure: LoadFailureKind? = null,
        val hasMore: Boolean = true,
        val isActivated: Boolean = false
    )

    fun onRandomAdultModeChanged(adult: Boolean) {
        val previousMode = randomAdultMode
        randomAdultMode = adult
        if (previousMode != null && previousMode != adult) {
            unloadRandomRecommendations(clearDeduplication = true)
        }
    }

    fun activateRandomRecommendations(adult: Boolean) {
        if (_randomRecommendations.value.isActivated && _randomRecommendations.value.items.isNotEmpty()) return
        randomAdultMode = adult
        loadRandomRecommendations(adult = adult, replace = true, activate = true)
    }

    fun replaceRandomRecommendations(adult: Boolean) {
        randomAdultMode = adult
        loadRandomRecommendations(adult = adult, replace = true, activate = true)
    }

    fun retryRandomRecommendations(adult: Boolean) {
        randomAdultMode = adult
        loadRandomRecommendations(adult = adult, replace = false, activate = true)
    }

    fun loadMoreRandomRecommendations(adult: Boolean) {
        val state = _randomRecommendations.value
        if (!state.isActivated || !state.hasMore || state.isLoading || state.failure != null) return
        loadRandomRecommendations(adult = adult, replace = false, activate = true)
    }

    fun unloadRandomRecommendations(clearDeduplication: Boolean = false) {
        inactivityJob?.cancel()
        inactivityJob = null
        randomLoadJob?.cancel()
        randomLoadJob = null
        if (clearDeduplication) {
            randomVisitedPages.clear()
            randomSeenNovelKeys.clear()
            randomRequester = null
        }
        _randomRecommendations.value = RandomRecommendationsState(
            items = emptyList(),
            isLoading = false,
            failure = null,
            hasMore = true,
            isActivated = false
        )
    }

    fun onHomeHidden(safeToUnload: () -> Boolean = { true }) {
        if (!_randomRecommendations.value.isActivated) return
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch(Dispatchers.IO) {
            delay(RANDOM_INACTIVITY_TIMEOUT_MS)
            ensureActive()
            if (isNetworkConnected()) {
                withContext(Dispatchers.Main.immediate) {
                    if (safeToUnload()) {
                        unloadRandomRecommendations(clearDeduplication = false)
                    }
                }
            }
        }
    }

    fun onHomeShown() {
        inactivityJob?.cancel()
        inactivityJob = null
    }

    private fun isNetworkConnected(): Boolean {
        return try {
            val app = EsjzoneApplication.instance
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun loadRandomRecommendations(adult: Boolean, replace: Boolean, activate: Boolean) {
        val activeJob = randomLoadJob
        if (!replace && activeJob?.isActive == true) return

        randomLoadJob = viewModelScope.launch(Dispatchers.IO) {
            if (replace) activeJob?.cancelAndJoin()
            val previous = _randomRecommendations.value
            _randomRecommendations.value = previous.copy(
                isLoading = true,
                failure = null,
                isActivated = activate || previous.isActivated
            )
            try {
                val requester = randomRequester ?: PresentationAccess.client
                    .novels(authorization, novelType = 0, sortType = 1)
                    .first
                    .also { randomRequester = it }
                if (replace && randomVisitedPages.size >= requester.pages()) {
                    randomVisitedPages.clear()
                    randomSeenNovelKeys.clear()
                }
                val collected = ArrayList<CoveredNovel>(RANDOM_RECOMMENDATION_BATCH_SIZE)
                var requestedPages = 0

                while (
                    collected.size < RANDOM_RECOMMENDATION_BATCH_SIZE &&
                    requestedPages < RANDOM_RECOMMENDATION_MAX_PAGES_PER_BATCH &&
                    randomVisitedPages.size < requester.pages()
                ) {
                    ensureActive()
                    val page = chooseUnvisitedRandomPage(requester.pages()) ?: break
                    randomVisitedPages += page
                    requestedPages += 1
                    requester.more(page)
                        .filter { adult || !it.isAdult }
                        .shuffled()
                        .forEach { novel ->
                            if (
                                collected.size < RANDOM_RECOMMENDATION_BATCH_SIZE &&
                                randomSeenNovelKeys.add(novelKey(novel))
                            ) {
                                collected += novel
                            }
                        }
                }

                ensureActive()
                val items = if (replace) collected else previous.items + collected
                _randomRecommendations.value = RandomRecommendationsState(
                    items = items,
                    isLoading = false,
                    failure = null,
                    hasMore = randomVisitedPages.size < requester.pages(),
                    isActivated = true
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _randomRecommendations.value = previous.copy(
                    isLoading = false,
                    failure = error.loadFailureKind(),
                    isActivated = activate || previous.isActivated
                )
                com.breakyuna.esjzone.util.AppLogger.e(
                    "HomeTabModel",
                    "Failed to load random recommendations",
                    error
                )
            }
        }
    }

    private fun chooseUnvisitedRandomPage(pageCount: Int): Int? {
        if (pageCount <= 0 || randomVisitedPages.size >= pageCount) return null
        repeat(12) {
            val candidate = Random.nextInt(1, pageCount + 1)
            if (candidate !in randomVisitedPages) return candidate
        }
        return (1..pageCount).firstOrNull { it !in randomVisitedPages }
    }

    fun getHomeData(forceRefresh: Boolean = false) {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            val visibleData = mutableState.value as? State.Result
            mutableState.value = visibleData?.copy(isSyncing = true) ?: State.Loading
            try {
                val data = PresentationAccess.client.getHomeData(
                    authorization = authorization,
                    forceRefresh = forceRefresh,
                    onProgress = { partialData ->
                        ensureActive()
                        HomeDataCache.writeSnapshot(authorization.domain, partialData)
                        mutableState.value = State.Result(partialData, isSyncing = true)
                    }
                )
                ensureActive()
                HomeDataCache.writeSnapshot(authorization.domain, data)
                mutableState.value = State.Result(data, isSyncing = false)
            } catch (e: CancellationException) {
                loadStarted = false
                throw e
            } catch (e: Exception) {
                if (mutableState.value !is State.Result) {
                    mutableState.value = State.Error(e.loadFailureKind())
                } else {
                    val current = mutableState.value as State.Result
                    mutableState.value = current.copy(isSyncing = false)
                }
                loadStarted = false
                com.breakyuna.esjzone.util.AppLogger.e("HomeTabModel", "Failed to load home data", e)
            }
        }
    }

    fun reload() {
        loadStarted = false
        unloadRandomRecommendations(clearDeduplication = true)
        getHomeData(forceRefresh = true)
    }
}
