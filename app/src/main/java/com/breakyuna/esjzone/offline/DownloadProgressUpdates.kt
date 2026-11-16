package com.breakyuna.esjzone.offline

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Serialize concurrent chapter callbacks and finish foreground updates before returning a result. */
internal suspend fun <T> withDownloadProgressUpdates(
    update: suspend (DownloadProgress) -> Unit,
    block: suspend ((DownloadProgress) -> Unit) -> T
): T = coroutineScope {
    val updates = Channel<DownloadProgress>(Channel.CONFLATED)
    launch {
        try {
            for (progress in updates) update(progress)
        } finally {
            // A cancelled worker must reject callbacks even while its non-cancellable cleanup runs.
            updates.cancel()
        }
    }
    try {
        block { progress -> updates.trySend(progress) }
    } finally {
        // Normal completion drains the last update; coroutineScope awaits its acknowledgement.
        updates.close()
    }
}
