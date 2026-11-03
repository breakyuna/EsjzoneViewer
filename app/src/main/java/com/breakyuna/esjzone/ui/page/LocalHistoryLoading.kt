package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Publish Room rows independently of slow download storage; callbacks stay on the caller's context. */
internal suspend fun collectLocalHistory(
    activities: Flow<List<LocalReadingActivity>>,
    downloadChanges: Flow<Long>,
    refreshProgress: suspend (List<LocalReadingActivity>) -> List<LocalReadingActivity>,
    onResult: (List<LocalReadingActivity>) -> Unit,
    onProgressFailure: (Exception) -> Unit
) = coroutineScope {
    val latestRows = MutableStateFlow<List<LocalReadingActivity>?>(null)
    val publicationLock = Any()
    launch {
        activities.distinctUntilChanged().collect { rows ->
            synchronized(publicationLock) {
                onResult(rows)
                latestRows.value = rows
            }
        }
    }
    latestRows.filterNotNull().combine(downloadChanges) { rows, _ -> rows }
        .collectLatest { rows ->
            // Empty history needs no storage access, even while an older scan is finishing.
            if (rows.isEmpty()) return@collectLatest
            try {
                val refreshed = refreshProgress(rows)
                currentCoroutineContext().ensureActive()
                // Room can publish a newer position or deletion while storage is blocked.
                synchronized(publicationLock) {
                    if (latestRows.value === rows) onResult(refreshed)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onProgressFailure(error)
            }
        }
}
