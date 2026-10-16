package com.breakyuna.esjzone.network.features

import android.content.Context
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.CacheDiagnostics
import com.breakyuna.esjzone.network.CacheEventType
import com.breakyuna.esjzone.network.CacheLayer
import com.breakyuna.esjzone.network.CacheEventReason
import com.breakyuna.esjzone.network.hasCredentials
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent cloud-history snapshot scoped by authenticated account and domain,
 * ensuring no cross-account leakage occurs when switching accounts or offline.
 */
object HistoryDataCache {

    private const val DIRECTORY_NAME = "history_cache"
    private val gson = Gson()
    private val ioLock = Any()
    private val memoryCache = ConcurrentHashMap<String, List<HistoryNovel>>()

    @Volatile
    private var storageDir: File? = null

    fun scopeFor(authorization: Authorization): String? {
        if (!authorization.hasCredentials()) return null
        val input = EsjzoneClient.accountScope(authorization)
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .take(16)
    }

    fun initialize(context: Context) {
        initializeDirectory(resolveDirectory(context.applicationContext))
    }

    internal fun initializeDirectory(dir: File) {
        synchronized(ioLock) {
            storageDir = dir
            memoryCache.clear()
        }
    }

    private fun resolveDirectory(context: Context): File {
        val dir = File(context.filesDir, DIRECTORY_NAME)
        if (!dir.isDirectory) dir.mkdirs()
        // Clean up legacy global snapshot file once if present to prevent cross-account leaks
        val legacyFile = File(context.filesDir, "history_data_snapshot.json")
        if (legacyFile.exists()) {
            runCatching { legacyFile.delete() }
        }
        return dir
    }

    fun readSnapshot(authorization: Authorization): List<HistoryNovel>? {
        val scope = scopeFor(authorization) ?: return null
        memoryCache[scope]?.let {
            CacheDiagnostics.record(CacheEventType.HIT, CacheLayer.HISTORY, CacheEventReason.MEMORY, "history|$scope")
            return it
        }

        val dir = storageDir ?: runCatching {
            resolveDirectory(EsjzoneApplication.instance)
        }.getOrNull() ?: return null

        return synchronized(ioLock) {
            val inMemory = memoryCache[scope]
            val cached = inMemory ?: loadFromDisk(dir, scope)?.also { memoryCache[scope] = it }
            if (cached != null) {
                CacheDiagnostics.record(CacheEventType.HIT, CacheLayer.HISTORY,
                    if (inMemory != null) CacheEventReason.MEMORY else CacheEventReason.DISK, "history|$scope")
            }
            cached
        }
    }

    fun writeSnapshot(authorization: Authorization, histories: List<HistoryNovel>, expectedEpoch: Long = PageCache.currentEpoch) {
        val scope = scopeFor(authorization) ?: return
        val dir = storageDir ?: runCatching {
            resolveDirectory(EsjzoneApplication.instance)
        }.getOrNull() ?: return

        synchronized(ioLock) {
            if (expectedEpoch != PageCache.currentEpoch) {
                CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.HISTORY, CacheEventReason.SUPERSEDED, "history|$scope")
                return
            }
            memoryCache[scope] = histories
            CacheDiagnostics.record(CacheEventType.UPDATE, CacheLayer.HISTORY, CacheEventReason.MEMORY, "history|$scope")
            try {
                val json = gson.toJson(HistorySnapshot(histories.map(::toSnapshot)))
                val fileName = "history_snapshot_$scope.json"
                val targetFile = File(dir, fileName)
                val tempFile = File(dir, "$fileName.tmp")
                tempFile.writeText(json, StandardCharsets.UTF_8)
                try {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: Exception) {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
                CacheDiagnostics.record(CacheEventType.UPDATE, CacheLayer.HISTORY, CacheEventReason.STORED,
                    "history|$scope", bytes = targetFile.length())
            } catch (e: Exception) {
                CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.HISTORY, CacheEventReason.WRITE_FAILED, "history|$scope")
                AppLogger.w("HistoryDataCache", "Failed to write history snapshot for scope $scope", e)
            }
        }
    }

    fun clearSnapshot(authorization: Authorization) {
        val scope = scopeFor(authorization) ?: return
        if (memoryCache.remove(scope) != null) {
            CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.HISTORY, CacheEventReason.INVALIDATED, "history|$scope")
        }
        val dir = storageDir ?: runCatching {
            resolveDirectory(EsjzoneApplication.instance)
        }.getOrNull() ?: return

        synchronized(ioLock) {
            val targetFile = File(dir, "history_snapshot_$scope.json")
            val length = targetFile.length()
            if (runCatching { targetFile.delete() }.getOrDefault(false)) {
                CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.HISTORY, CacheEventReason.INVALIDATED,
                    "history|$scope", bytes = length)
            } else if (targetFile.exists()) {
                CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.HISTORY, CacheEventReason.DELETE_FAILED, "history|$scope")
            }
        }
    }

    fun clearMemory() = synchronized(ioLock) {
        memoryCache.keys.forEach { scope ->
            CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.HISTORY, CacheEventReason.CLEARED, "history|$scope")
        }
        memoryCache.clear()
    }

    fun clearAll() {
        val dir = storageDir ?: runCatching {
            resolveDirectory(EsjzoneApplication.instance)
        }.getOrNull() ?: return

        synchronized(ioLock) {
            clearMemory()
            if (!dir.exists()) return
            val files = dir.listFiles() ?: throw java.io.IOException("Unable to list history snapshots")
            var failed = false
            files.forEach { file ->
                val scope = file.name.removeSuffix(".tmp").removeSuffix(".json").removePrefix("history_snapshot_")
                val length = file.length()
                if (file.delete()) {
                    CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.HISTORY, CacheEventReason.CLEARED,
                        "history|$scope", bytes = length)
                } else if (file.exists()) {
                    failed = true
                    CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.HISTORY, CacheEventReason.DELETE_FAILED, "history|$scope")
                }
            }
            if (failed) throw java.io.IOException("Unable to delete all history snapshots")
        }
    }

    private fun loadFromDisk(dir: File, scope: String): List<HistoryNovel>? {
        val file = File(dir, "history_snapshot_$scope.json")
        if (!file.isFile) return null
        return try {
            val snapshot = gson.fromJson(file.readText(StandardCharsets.UTF_8), HistorySnapshot::class.java)
            snapshot?.histories.orEmpty().map { it.toHistoryNovel() }
        } catch (e: Exception) {
            CacheDiagnostics.record(CacheEventType.FAILURE, CacheLayer.HISTORY, CacheEventReason.READ_FAILED, "history|$scope")
            AppLogger.w("HistoryDataCache", "Failed to read history snapshot for scope $scope", e)
            if (runCatching { file.delete() }.getOrDefault(false)) {
                CacheDiagnostics.record(CacheEventType.EVICT, CacheLayer.HISTORY, CacheEventReason.INVALID_DATA,
                    "history|$scope")
            }
            null
        }
    }

    @Deprecated("Use readSnapshot(authorization) for account-scoped isolation")
    fun readSnapshot(): List<HistoryNovel>? = null

    @Deprecated("Use writeSnapshot(authorization, histories) for account-scoped isolation")
    fun writeSnapshot(histories: List<HistoryNovel>) {}

    private fun toSnapshot(history: HistoryNovel): HistoryNovelSnapshot = HistoryNovelSnapshot(
        name = history.name,
        url = history.url,
        vid = history.vid,
        chapterName = history.chapter.name,
        chapterUrl = history.chapter.url,
        chapterIsHistory = history.chapter.isHistory
    )
}

internal data class HistorySnapshot(
    @SerializedName("histories")
    val histories: List<HistoryNovelSnapshot>? = emptyList()
)

internal data class HistoryNovelSnapshot(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("url")
    val url: String = "",
    // Legacy "vid" stored a novel ID. Keep the row readable, but require a refresh before deletion.
    @SerializedName("viewRecordId")
    val vid: String = "",
    @SerializedName("chapterName")
    val chapterName: String = "",
    @SerializedName("chapterUrl")
    val chapterUrl: String = "",
    @SerializedName("chapterIsHistory")
    val chapterIsHistory: Boolean = false
) {
    fun toHistoryNovel(): HistoryNovel = HistoryNovel(
        name = name,
        url = url,
        vid = vid,
        chapter = Chapter(chapterName, chapterUrl, chapterIsHistory)
    )
}
