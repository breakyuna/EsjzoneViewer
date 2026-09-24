package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.community.ForumTopic
import com.breakyuna.esjzone.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Background synchronization manager for community pages (guestbook and water cooler).
 * Pre-fetches and writes fresh HTML into [com.breakyuna.esjzone.network.PageCache] during app startup,
 * enabling immediate zero-wait loading when opening those community pages.
 */
object CommunitySyncManager {

    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isSyncing = AtomicBoolean(false)

    val waterCoolerTopic: ForumTopic
        get() = ForumTopic(
            boardId = EsjzoneUrls.WATER_COOLER_BOARD_ID,
            id = EsjzoneUrls.WATER_COOLER_TOPIC_ID,
            title = "灌水楼",
            author = null,
            createdAt = null,
            replyCount = null,
            viewCount = null,
            lastReplyAt = null,
            url = EsjzoneUrls.WaterCooler
        )

    fun schedulePreSync(authorization: Authorization, delayMillis: Long = 0L) {
        if (!isSyncing.compareAndSet(false, true)) return
        workerScope.launch {
            try {
                if (delayMillis > 0L) delay(delayMillis)
                AppLogger.i("CommunitySyncManager", "Starting background pre-sync for guestbook and water cooler")
                val guestbookJob = launch {
                    try {
                        PresentationAccess.client.getPageComments(
                            authorization = authorization,
                            pageUrl = EsjzoneUrls.Guestbook,
                            forceRefresh = true
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.w("CommunitySyncManager", "Failed to pre-sync guestbook", e)
                    }
                }
                val waterCoolerJob = launch {
                    try {
                        PresentationAccess.client.getForumPost(
                            authorization = authorization,
                            topic = waterCoolerTopic,
                            forceRefresh = true
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.w("CommunitySyncManager", "Failed to pre-sync water cooler", e)
                    }
                }
                joinAll(guestbookJob, waterCoolerJob)
                AppLogger.i("CommunitySyncManager", "Background pre-sync completed")
            } finally {
                isSyncing.set(false)
            }
        }
    }
}
