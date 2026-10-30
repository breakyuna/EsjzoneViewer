package com.breakyuna.esjzone.ui.tab

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.app.CoverLoadingPolicy
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.PageCache
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
import com.breakyuna.esjzone.network.features.enrichWeeklyPopular
import com.breakyuna.esjzone.network.features.getWeeklyUpdates
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val RANDOM_INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L

class HomeTabModel internal constructor(
    private val authorization: Authorization,
    private val loadHome: suspend (Boolean, (HomeData) -> Unit) -> HomeData
) : AppStateViewModel<HomeTabModel.State>(
    HomeDataCache.readSnapshot(authorization)?.let { State.Result(it) } ?: State.Loading
) {

    constructor(authorization: Authorization) : this(authorization, { refresh, progress ->
        PresentationAccess.client.getHomeData(authorization, forceRefresh = refresh, onProgress = progress)
    })

    private var loadStarted = false
    private var homeLoadJob: Job? = null
    private val homeLoadMutex = Mutex()
    private var homeLoadGeneration = 0L
    private var randomLoadJob: Job? = null
    private var inactivityJob: Job? = null
    private var randomPool = createRandomPool()
    private var randomAdultMode: Boolean? = null
    private val _randomRecommendations = MutableStateFlow(RandomRecommendationsState())
    val randomRecommendations = _randomRecommendations.asStateFlow()

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(
            val homeData: HomeData,
            val isSyncing: Boolean = false,
            val isRefreshing: Boolean = false,
            val refreshFailure: LoadFailureKind? = null
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
            randomPool = createRandomPool()
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

        randomLoadJob = viewModelScope.launch {
            if (replace) activeJob?.cancelAndJoin()
            val previous = _randomRecommendations.value
            _randomRecommendations.value = previous.copy(
                isLoading = true,
                failure = null,
                isActivated = activate || previous.isActivated
            )
            try {
                val collected = randomPool.nextBatch(adult, restartIfExhausted = replace)

                ensureActive()
                val items = if (replace) collected else previous.items + collected
                _randomRecommendations.value = RandomRecommendationsState(
                    items = items,
                    isLoading = false,
                    failure = null,
                    hasMore = randomPool.hasMore,
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

    private fun createRandomPool() = HomeRecommendationPool { sortType ->
        PresentationAccess.client.novels(authorization, novelType = 0, sortType = sortType)
    }

    fun getHomeData(forceRefresh: Boolean = false) {
        if (!forceRefresh && loadStarted) return
        loadStarted = true
        val generation = ++homeLoadGeneration
        homeLoadJob?.cancel()
        homeLoadJob = viewModelScope.launch {
            // Wait for cancelled callbacks and snapshot writes to unwind before publishing
            // another load, including when several refreshes arrive in quick succession.
            homeLoadMutex.withLock {
                ensureActive()
                val cacheEpoch = PageCache.currentEpoch
                CoverLoadingPolicy.homeDataLoading(true)
                val visibleData = mutableState.value as? State.Result
                mutableState.value = visibleData?.copy(
                    isSyncing = true,
                    isRefreshing = forceRefresh, refreshFailure = null
                ) ?: State.Loading
                try {
                    val data = loadHome(forceRefresh) { partialData ->
                        ensureActive()
                        mutableState.value = State.Result(
                            partialData,
                            isSyncing = true,
                            isRefreshing = forceRefresh
                        )
                        HomeDataCache.updateMemorySnapshot(authorization, partialData, cacheEpoch)
                    }
                    ensureActive()
                    mutableState.value = State.Result(data, isSyncing = false)
                    withContext(Dispatchers.IO) {
                        ensureActive()
                        HomeDataCache.updateMemorySnapshot(authorization, data, cacheEpoch)
                        HomeDataCache.writeSnapshot(authorization, data, cacheEpoch)
                    }
                } catch (e: CancellationException) {
                    if (generation == homeLoadGeneration) loadStarted = false
                    throw e
                } catch (e: Exception) {
                    if (generation == homeLoadGeneration) {
                        val current = mutableState.value as? State.Result
                        mutableState.value = current?.copy(isSyncing = false, isRefreshing = false, refreshFailure = e.loadFailureKind())
                            ?: State.Error(e.loadFailureKind())
                        loadStarted = false
                    }
                    com.breakyuna.esjzone.util.AppLogger.e("HomeTabModel", "Failed to load home data", e)
                } finally {
                    CoverLoadingPolicy.homeDataLoading(false)
                }
            }
        }
    }

    private var weeklyJob: Job? = null
    fun retryWeekly() {
        if (weeklyJob?.isActive == true) return
        weeklyJob = viewModelScope.launch {
            val before = mutableState.value as? State.Result ?: return@launch
            val cacheEpoch = PageCache.currentEpoch
            mutableState.value = before.copy(isSyncing = true)
            try {
                val days = com.breakyuna.esjzone.network.cancellablePageRequest {
                    PresentationAccess.client.getWeeklyUpdates(authorization, forceRefresh = true)
                }
                val current = mutableState.value as? State.Result ?: return@launch
                val updated = current.homeData.copy(weeklyUpdates = days, weeklyFailure = null)
                mutableState.value = current.copy(isSyncing = false, homeData = updated)
                withContext(Dispatchers.IO) {
                    HomeDataCache.updateMemorySnapshot(authorization, updated, cacheEpoch)
                    HomeDataCache.writeSnapshot(authorization, updated, cacheEpoch)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val current = mutableState.value as? State.Result ?: return@launch
                mutableState.value = current.copy(isSyncing = false,
                    homeData = current.homeData.copy(weeklyFailure = e.loadFailureKind()))
            }
        }
    }

    private val enrichedPopular = mutableSetOf<String>()
    suspend fun loadVisiblePopular(items: List<com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel>) {
        for (item in items) {
            if (item.url in enrichedPopular) continue
            val cacheEpoch = PageCache.currentEpoch
            val enriched = try {
                com.breakyuna.esjzone.network.cancellablePageRequest(background = true) {
                    PresentationAccess.client.enrichWeeklyPopular(authorization,
                        com.breakyuna.esjzone.network.features.WeeklyPopularSeed(rank = item.rank, name = item.name, url = item.url, weeklyViews = item.weeklyViews))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { continue }
            kotlin.coroutines.coroutineContext.ensureActive()
            enrichedPopular += item.url
            val current = mutableState.value as? State.Result ?: continue
            val updated = current.homeData.copy(weeklyPopular = current.homeData.weeklyPopular.map {
                if (it.url == item.url) com.breakyuna.esjzone.network.features.mergeWeeklyPopularEnrichment(it, enriched) else it
            })
            mutableState.value = current.copy(homeData = updated)
            HomeDataCache.updateMemorySnapshot(authorization, updated, cacheEpoch)
            withContext(Dispatchers.IO) { HomeDataCache.writeSnapshot(authorization, updated, cacheEpoch) }
        }
    }

    fun reload() {
        weeklyJob?.cancel()
        enrichedPopular.clear()
        unloadRandomRecommendations(clearDeduplication = true)
        getHomeData(forceRefresh = true)
    }
}
