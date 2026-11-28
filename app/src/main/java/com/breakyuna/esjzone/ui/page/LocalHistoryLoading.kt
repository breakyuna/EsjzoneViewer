package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class CachedLocalHistoryRow(
    val input: LocalReadingActivity,
    val progressVersion: Long,
    val refreshed: LocalReadingActivity
)

/** Publish Room rows independently of slow download storage; callbacks stay on the caller's context. */
internal suspend fun collectLocalHistory(
    activities: Flow<List<LocalReadingActivity>>,
    downloadChanges: Flow<Long>,
    refreshProgress: suspend (List<LocalReadingActivity>) -> List<LocalReadingActivity>,
    onResult: (List<LocalReadingActivity>) -> Unit,
    onProgressFailure: (Exception) -> Unit,
    storageWorkAllowed: Flow<Boolean> = flowOf(true),
    progressVersionFor: ((String) -> Long)? = null
) = coroutineScope {
    val latestRows = MutableStateFlow<List<LocalReadingActivity>?>(null)
    val publicationLock = Any()
    var cachedRows = emptyMap<String, CachedLocalHistoryRow>()
    var lastPublishedSource: List<LocalReadingActivity>? = null
    launch {
        activities.distinctUntilChanged().collect { rows ->
            synchronized(publicationLock) {
                onResult(rows)
                latestRows.value = rows
            }
        }
    }
    combine(latestRows.filterNotNull(), downloadChanges, storageWorkAllowed) { rows, _, allowed -> rows to allowed }
        .collectLatest { (rows, allowed) ->
            // Empty history needs no storage access, even while an older scan is finishing.
            if (!allowed || rows.isEmpty() || latestRows.value !== rows) return@collectLatest
            try {
                val refreshed = if (progressVersionFor == null) {
                    refreshProgress(rows)
                } else {
                    val versions = LongArray(rows.size) { index -> progressVersionFor(rows[index].novelUrl) }
                    val toRefresh = ArrayList<LocalReadingActivity>()
                    val toRefreshIndices = ArrayList<Int>()
                    val assembled = ArrayList<LocalReadingActivity>(rows.size)
                    rows.forEachIndexed { index, activity ->
                        val cached = cachedRows[activity.activityId]
                        if (cached != null && cached.input == activity && cached.progressVersion == versions[index]) {
                            assembled.add(cached.refreshed)
                        } else {
                            assembled.add(activity)
                            toRefresh.add(activity)
                            toRefreshIndices.add(index)
                        }
                    }
                    if (toRefresh.isEmpty() && lastPublishedSource === rows) {
                        return@collectLatest
                    }
                    if (toRefresh.isNotEmpty()) {
                        val updated = refreshProgress(toRefresh)
                        if (updated.size == toRefresh.size) {
                            toRefreshIndices.forEachIndexed { i, rowIndex ->
                                assembled[rowIndex] = updated[i]
                            }
                        } else {
                            val fallback = refreshProgress(rows)
                            currentCoroutineContext().ensureActive()
                            cachedRows = rows.indices.associate { idx ->
                                val activity = rows[idx]
                                activity.activityId to CachedLocalHistoryRow(activity, versions[idx], fallback[idx])
                            }
                            synchronized(publicationLock) {
                                if (latestRows.value === rows) {
                                    onResult(fallback)
                                    lastPublishedSource = rows
                                }
                            }
                            return@collectLatest
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    cachedRows = rows.indices.associate { idx ->
                        val activity = rows[idx]
                        activity.activityId to CachedLocalHistoryRow(activity, versions[idx], assembled[idx])
                    }
                    assembled
                }
                currentCoroutineContext().ensureActive()
                // Room can publish a newer position or deletion while storage is blocked.
                synchronized(publicationLock) {
                    if (latestRows.value === rows) {
                        onResult(refreshed)
                        lastPublishedSource = rows
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onProgressFailure(error)
            }
        }
}
