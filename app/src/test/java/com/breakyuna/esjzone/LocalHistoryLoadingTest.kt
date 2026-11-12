package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.ui.page.collectLocalHistory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHistoryLoadingTest {
    @Test fun repeatedEqualRoomSnapshotDoesNotReplaceCorrectedProgress() = runBlocking {
        withTimeout(5_000) {
            val row = activity("first")
            val updated = row.copy(chapterProgress = 0.75f, lastReadAt = 2L)
            val rows = MutableSharedFlow<List<LocalReadingActivity>>(extraBufferCapacity = 4)
            val results = Channel<List<LocalReadingActivity>>(Channel.UNLIMITED)
            val job = launch {
                collectLocalHistory(rows, MutableStateFlow(0L),
                    refreshProgress = { snapshot -> snapshot.map { it.copy(totalChapters = 20) } },
                    onResult = { results.trySend(it) },
                    onProgressFailure = { throw AssertionError(it) }
                )
            }
            try {
                rows.subscriptionCount.first { it > 0 }
                rows.emit(listOf(row))
                assertEquals(listOf(row), results.receive())
                assertEquals(listOf(row.copy(totalChapters = 20)), results.receive())
                // Room can emit equal fresh objects after a no-op UPDATE. Unlike StateFlow,
                // SharedFlow preserves that emission so this regression is actually exercised.
                rows.emit(listOf(row.copy()))
                rows.emit(listOf(updated))
                assertEquals(listOf(updated), results.receive())
                assertEquals(listOf(updated.copy(totalChapters = 20)), results.receive())
                assertTrue(results.tryReceive().isFailure)
            } finally { job.cancelAndJoin() }
        }
    }

    @Test fun showsRoomRowsBeforeStorageCompletesAndRefreshesOnDownloadChanges() = runBlocking {
        withTimeout(5_000) {
            val row = activity("first")
            val rows = MutableStateFlow(listOf(row))
            val changes = MutableStateFlow(0L)
            val results = Channel<List<LocalReadingActivity>>(Channel.UNLIMITED)
            val entered = Channel<Unit>(Channel.UNLIMITED)
            val release = CompletableDeferred<Unit>()
            val projected = listOf(row.copy(totalChapters = 20))
            val job = launch {
                collectLocalHistory(rows, changes,
                    refreshProgress = {
                        entered.send(Unit)
                        release.await()
                        projected
                    },
                    onResult = { results.trySend(it) },
                    onProgressFailure = { throw AssertionError(it) }
                )
            }
            try {
                assertEquals(listOf(row), results.receive())
                entered.receive()
                assertTrue("Storage is still blocked, but history is already visible", results.tryReceive().isFailure)
                release.complete(Unit)
                assertEquals(projected, results.receive())
                changes.value += 1L
                entered.receive()
                assertEquals(projected, results.receive())
            } finally {
                release.complete(Unit)
                job.cancelAndJoin()
            }
        }
    }

    @Test fun deletionAndNewPositionsStayVisibleWhileOldStorageScanIsBlocked() = runBlocking {
        withTimeout(5_000) {
            val old = activity("first")
            val current = old.copy(chapterProgress = 0.75f, lastReadAt = 2L)
            val rows = MutableStateFlow(listOf(old))
            val results = Channel<List<LocalReadingActivity>>(Channel.UNLIMITED)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val job = launch {
                collectLocalHistory(rows, MutableStateFlow(0L),
                    refreshProgress = { snapshot ->
                        if (snapshot == listOf(old)) {
                            entered.complete(Unit)
                            // Model a provider call or monitor wait that cannot be cancelled immediately.
                            withContext(NonCancellable) { release.await() }
                        }
                        snapshot.map { it.copy(totalChapters = 20) }
                    },
                    onResult = { results.trySend(it) },
                    onProgressFailure = { throw AssertionError(it) }
                )
            }
            try {
                assertEquals(listOf(old), results.receive())
                entered.await()
                rows.value = emptyList()
                assertEquals(emptyList<LocalReadingActivity>(), results.receive())
                rows.value = listOf(current)
                assertEquals(listOf(current), results.receive())
                release.complete(Unit)
                assertEquals(listOf(current.copy(totalChapters = 20)), results.receive())
                assertTrue("The old scan must never restore a deleted or outdated row", results.tryReceive().isFailure)
            } finally {
                release.complete(Unit)
                job.cancelAndJoin()
            }
        }
    }

    @Test fun unavailableStorageKeepsHistoryAndCanRecoverOnTheNextDownloadChange() = runBlocking {
        withTimeout(5_000) {
            val row = activity("first")
            val changes = MutableStateFlow(0L)
            val results = Channel<List<LocalReadingActivity>>(Channel.UNLIMITED)
            val failures = Channel<Exception>(Channel.UNLIMITED)
            val unavailable = IllegalStateException("Storage unavailable")
            var attempts = 0
            val job = launch {
                collectLocalHistory(MutableStateFlow(listOf(row)), changes,
                    refreshProgress = { snapshot ->
                        if (attempts++ == 0) throw unavailable
                        snapshot.map { it.copy(totalChapters = 20) }
                    },
                    onResult = { results.trySend(it) },
                    onProgressFailure = { failures.trySend(it) }
                )
            }
            try {
                assertEquals(listOf(row), results.receive())
                assertSame(unavailable, failures.receive())
                assertTrue("Storage failure must not replace the visible local rows", results.tryReceive().isFailure)
                changes.value += 1L
                assertEquals(listOf(row.copy(totalChapters = 20)), results.receive())
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test fun readerStartupDefersStorageButKeepsHistoryLive() = runBlocking {
        withTimeout(5_000) {
            val row = activity("first")
            val rows = MutableStateFlow(listOf(row))
            val allowed = MutableStateFlow(false)
            val results = Channel<List<LocalReadingActivity>>(Channel.UNLIMITED)
            val entered = Channel<List<LocalReadingActivity>>(Channel.UNLIMITED)
            val job = launch {
                collectLocalHistory(rows, MutableStateFlow(0L),
                    refreshProgress = { snapshot ->
                        entered.send(snapshot)
                        snapshot.map { it.copy(totalChapters = 20) }
                    },
                    onResult = { results.trySend(it) },
                    onProgressFailure = { throw AssertionError(it) },
                    storageWorkAllowed = allowed)
            }
            try {
                assertEquals(listOf(row), results.receive())
                allowed.subscriptionCount.first { it > 0 }
                assertTrue(entered.tryReceive().isFailure)
                val newest = row.copy(lastReadAt = 2L, chapterProgress = 0.75f)
                rows.value = listOf(newest)
                assertEquals(listOf(newest), results.receive())
                allowed.value = true
                assertEquals(listOf(newest), entered.receive())
                assertEquals(listOf(newest.copy(totalChapters = 20)), results.receive())
            } finally { job.cancelAndJoin() }
        }
    }

    private fun activity(id: String) = LocalReadingActivity(
        activityId = id, novelId = id, novelName = "Novel", novelUrl = "/detail/$id.html",
        chapterUrl = "/forum/$id/chapter.html", chapterName = "Chapter",
        chapterIndex = 0, totalChapters = 10, chapterProgress = 0.25f,
        startedAt = 1L, lastReadAt = 1L, durationMs = 0L
    )
}
