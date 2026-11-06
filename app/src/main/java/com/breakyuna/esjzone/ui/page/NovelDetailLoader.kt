package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess

import androidx.compose.runtime.mutableStateMapOf
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel

/** Shared, cancellable detail completion for lists that start with covered novels. */
class NovelDetailLoader(
    private val authorization: Authorization,
    private val readDownloaded: (String) -> DetailedNovel? = { PresentationAccess.downloads.readDetailedNovel(it) },
    private val fetchDetail: (Novel) -> DetailedNovel = { PresentationAccess.client.getNovelDetail(authorization, it) }
) : AppStateViewModel<Unit>(Unit) {
    val details = mutableStateMapOf<String, DetailedNovel>()
    val failures = mutableStateMapOf<String, LoadFailureKind>()
    private val jobs = mutableMapOf<String, Job>()
    private val detailPermits = Semaphore(3)
    private val adultStatusRequired = mutableSetOf<String>()

    fun key(novel: Novel): String = novel.url.trim().ifBlank { novel.name.trim() }

    fun load(novel: Novel, requireAdultStatus: Boolean, retry: Boolean = false) {
        val key = key(novel)
        if (key.isBlank()) return
        if (requireAdultStatus) adultStatusRequired.add(key) else adultStatusRequired.remove(key)
        val available = details[key]
        if (!retry && available != null && (!requireAdultStatus || available.adultStatusKnown)) return
        if (!retry && jobs[key]?.isActive == true) return
        jobs[key]?.cancel()
        failures.remove(key)
        jobs[key] = viewModelScope.launch {
            val thisJob = coroutineContext[Job]
            try {
                if (!retry) {
                    val cached = details[key] ?: withContext(Dispatchers.IO) {
                        try {
                            readDownloaded(novel.url)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (cached != null) {
                        // Keep usable cover/catalog data even while a stricter filter needs verification.
                        details[key] = cached
                        // Re-read the current requirement after IO: the setting can change mid-load.
                        if (key !in adultStatusRequired || cached.adultStatusKnown) return@launch
                    }
                }
                val detail = detailPermits.withPermit {
                    withContext(Dispatchers.IO) {
                        fetchDetail(novel)
                    }
                }
                details[key] = detail
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures[key] = e.loadFailureKind()
                com.breakyuna.esjzone.util.AppLogger.e("NovelDetailLoader", "Failed to load novel detail: $key", e)
            } finally {
                if (jobs[key] === thisJob) jobs.remove(key)
            }
        }
    }

    fun retry(novel: Novel, requireAdultStatus: Boolean) = load(novel, requireAdultStatus, retry = true)
}
