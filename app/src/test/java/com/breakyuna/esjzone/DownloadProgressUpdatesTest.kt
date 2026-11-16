package com.breakyuna.esjzone

import com.breakyuna.esjzone.offline.DownloadProgress
import com.breakyuna.esjzone.offline.withDownloadProgressUpdates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressUpdatesTest {
    @Test fun completionWaitsForFinalUpdateAndRejectsLateCallbacks() = runBlocking {
        val received = mutableListOf<DownloadProgress>()
        val progress = DownloadProgress(3, 10, "Chapter 3")
        lateinit var publish: (DownloadProgress) -> Unit
        val result = withDownloadProgressUpdates({ received.add(it) }) { callback ->
            publish = callback
            callback(progress)
            "finished"
        }
        publish(DownloadProgress(4, 10, "Late chapter"))
        assertEquals("finished", result)
        assertEquals(listOf(progress), received)
    }

    @Test fun cancellationStopsInFlightUpdateAndRejectsLateChapterProgress() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val received = mutableListOf<DownloadProgress>()
        lateinit var publish: (DownloadProgress) -> Unit
        val download = launch {
            withDownloadProgressUpdates({ progress ->
                entered.complete(Unit)
                release.await()
                received.add(progress)
            }) { callback ->
                publish = callback
                callback(DownloadProgress(1, 10, "Chapter 1"))
                awaitCancellation()
            }
        }
        entered.await()
        download.cancelAndJoin()
        publish(DownloadProgress(2, 10, "Late chapter"))
        release.complete(Unit)
        assertTrue(received.isEmpty())
    }
}
