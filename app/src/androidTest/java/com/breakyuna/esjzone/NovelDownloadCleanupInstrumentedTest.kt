package com.breakyuna.esjzone

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.breakyuna.esjzone.offline.NovelDownloadManager
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.offline.NovelDownloadWorker
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NovelDownloadCleanupInstrumentedTest {
    @Test fun deletingAbsentDirectoryCancelsQueuedDownload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "download-cleanup-").toFile()
        val novelUrl = "https://www.esjzone.cc/detail/999999991.html"
        val workManager = WorkManager.getInstance(context)
        val workName = NovelDownloadManager.uniqueWorkName(novelUrl)
        val request = OneTimeWorkRequestBuilder<NovelDownloadWorker>()
            .setInitialDelay(1, TimeUnit.DAYS).build()
        try {
            NovelDownloadStore.initializeDirectory(root)
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
                .result.get(10, TimeUnit.SECONDS)
            assertEquals(WorkInfo.State.ENQUEUED,
                workManager.getWorkInfoById(request.id).get(10, TimeUnit.SECONDS)!!.state)
            assertEquals(0L, NovelDownloadStore.storageBytes(novelUrl))

            assertTrue(NovelDownloadStore.delete(novelUrl))
            withTimeout(10_000) {
                workManager.getWorkInfoByIdFlow(request.id).first { it?.state == WorkInfo.State.CANCELLED }
            }
            assertTrue(root.listFiles()!!.isEmpty())
        } finally {
            workManager.cancelUniqueWork(workName).result.get(10, TimeUnit.SECONDS)
            NovelDownloadStore.initialize(context)
            root.deleteRecursively()
        }
    }
}
