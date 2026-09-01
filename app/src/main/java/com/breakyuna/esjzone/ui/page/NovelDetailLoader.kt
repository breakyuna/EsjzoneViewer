package com.breakyuna.esjzone.ui.page

import androidx.compose.runtime.mutableStateMapOf
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel

/** Shared, cancellable detail completion for lists that start with covered novels. */
class NovelDetailLoader(private val authorization: Authorization) : StateScreenModel<Unit>(Unit) {
    val details = mutableStateMapOf<String, DetailedNovel>()
    val failures = mutableStateMapOf<String, Boolean>()
    private val jobs = mutableMapOf<String, Job>()

    fun key(novel: Novel): String = novel.url.trim().ifBlank { novel.name.trim() }

    fun load(novel: Novel, retry: Boolean = false) {
        val key = key(novel)
        if (key.isBlank() || details.containsKey(key)) return
        if (!retry && jobs[key]?.isActive == true) return
        jobs[key]?.cancel()
        failures.remove(key)
        jobs[key] = screenModelScope.launch {
            val thisJob = coroutineContext[Job]
            try {
                val detail = withContext(Dispatchers.IO) {
                    EsjzoneClient.getNovelDetail(authorization, novel)
                }
                details[key] = detail
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures[key] = true
                com.breakyuna.esjzone.util.AppLogger.e("NovelDetailLoader", "Failed to load novel detail: $key", e)
            } finally {
                if (jobs[key] === thisJob) jobs.remove(key)
            }
        }
    }

    fun retry(novel: Novel) = load(novel, retry = true)
}
