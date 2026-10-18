package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess

import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.AppLanguage
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.features.logout
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CacheOperation { PAGES, IMAGES }

/** Coordinates settings persistence and cache/session maintenance off the UI. */
class SettingsPageModel : AppStateViewModel<SettingsPageModel.State>(State()) {

    data class State(
        val cacheStats: LocalCacheStats? = null,
        val cacheOperation: CacheOperation? = null,
        val cacheStatsError: Boolean = false,
        val downloadStats: LocalDownloadStats? = null,
        val downloadStatsError: Boolean = false,
        val storageUsage: List<StorageUsage>? = null,
        val storageStatsError: Boolean = false,
        val cacheClearError: Boolean = false,
        val logoutInProgress: Boolean = false,
        val logoutCompleted: Boolean = false,
        val logoutFailed: Boolean = false,
        val autoBackupFailed: Boolean = false
    )

    fun setAutoBackup(enabled: Boolean, scope: String) {
        viewModelScope.launch {
            try {
                PresentationAccess.autoBackup.setEnabled(enabled, scope)
                mutableState.update { it.copy(autoBackupFailed = false) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(autoBackupFailed = true) }
            }
        }
    }

    fun persist(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (key) {
                    "adult", "show_adult" -> PresentationAccess.settings.setAdult(value.toBooleanStrictOrNull() ?: return@launch)
                    "domain" -> {
                        PresentationAccess.settings.setDomain(value)
                        com.breakyuna.esjzone.network.features.HistoryDataCache.clearMemory()
                    }
                    "language" -> PresentationAccess.settings.setLanguage(AppLanguage.fromCode(value))
                    PresentationAccess.settings.READER_AUTO_SAVE_KEY ->
                        PresentationAccess.settings.setReaderAutoSave(value.toBooleanStrictOrNull() ?: return@launch)
                    PresentationAccess.settings.DOWNLOAD_CONCURRENCY_KEY ->
                        PresentationAccess.settings.setDownloadConcurrency(value.toIntOrNull() ?: return@launch)
                    PresentationAccess.settings.START_TAB_KEY ->
                        PresentationAccess.settings.setStartTab(value)
                    else -> PresentationAccess.database.cacheDao().put(key, value)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsPageModel", "Failed to persist setting: $key", e)
            }
        }
    }

    private val cacheStatsRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (ignored in cacheStatsRequests) {
                mutableState.update { it.copy(
                    cacheStatsError = false, downloadStatsError = false, storageStatsError = false
                ) }
                ensureActive()
                try {
                    val novels = PresentationAccess.downloads.listDownloadedNovels()
                    mutableState.update { it.copy(
                        downloadStats = LocalDownloadStats(novels.size, novels.sumOf { it.storageBytes }),
                        downloadStatsError = false
                    ) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("SettingsPageModel", "Failed to calculate download statistics", e)
                    mutableState.update { it.copy(downloadStatsError = true) }
                }
                ensureActive()
                try {
                    val pageStats = PresentationAccess.client.pageCacheStats()
                    val filesDir = com.breakyuna.esjzone.EsjzoneApplication.instance.filesDir
                    val historyDir = java.io.File(filesDir, "history_cache")
                    val snapshots = (listOf(java.io.File(filesDir, "home_data_snapshot.json")) +
                        if (historyDir.exists()) {
                            historyDir.listFiles()?.toList()
                                ?: throw java.io.IOException("Unable to list history snapshots")
                        } else emptyList()).filter { it.isFile }
                    val imageBytes = PresentationAccess.imageCacheSizeBytes()
                    mutableState.update { it.copy(
                        cacheStats = LocalCacheStats(pageStats.sizeBytes + snapshots.sumOf { it.length() },
                            pageStats.entryCount + snapshots.size, imageBytes),
                        cacheStatsError = false
                    ) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("SettingsPageModel", "Failed to calculate cache statistics", e)
                    mutableState.update { it.copy(cacheStatsError = true) }
                }
                ensureActive()
                try {
                    val usage = readStorageUsage(com.breakyuna.esjzone.EsjzoneApplication.instance)
                    mutableState.update { it.copy(
                        storageUsage = usage,
                        storageStatsError = false
                    ) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("SettingsPageModel", "Failed to calculate storage usage", e)
                    mutableState.update { it.copy(storageStatsError = true) }
                }
            }
        }
    }

    fun refreshCacheStats() {
        cacheStatsRequests.trySend(Unit)
    }

    fun clearPageCache() {
        clear(CacheOperation.PAGES) { PresentationAccess.client.clearPageCache() }
    }

    fun clearImageCache() {
        clear(CacheOperation.IMAGES) {
            PresentationAccess.clearImageCaches()
        }
    }

    private fun clear(operation: CacheOperation, action: suspend () -> Unit) {
        mutableState.update { it.copy(
            cacheOperation = operation,
            cacheClearError = false
        ) }
        viewModelScope.launch(Dispatchers.IO) {
            var failed = false
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                AppLogger.e("SettingsPageModel", "Failed to clear $operation cache", e)
            }
            mutableState.update { it.copy(
                cacheOperation = null,
                cacheClearError = failed
            ) }
            refreshCacheStats()
        }
    }

    fun clearLogoutFailure() {
        mutableState.update { it.copy(logoutFailed = false) }
    }

    fun logout(authorization: Authorization) {
        if (mutableState.value.logoutInProgress) return
        mutableState.update { it.copy(
            logoutInProgress = true, logoutCompleted = false, logoutFailed = false
        ) }
        viewModelScope.launch(Dispatchers.IO) {
            var cancelled = false
            var completed = false
            try {
                com.breakyuna.esjzone.network.features.HistoryDataCache.clearSnapshot(authorization)
                try {
                    PresentationAccess.settings.DOMAINS.forEach { domain ->
                        val session = PresentationAccess.client.restoreAuthorization(domain)
                            ?.takeIf { PresentationAccess.client.hasSiteSession(domain) }
                        if (session != null) PresentationAccess.client.logout(session)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w("SettingsPageModel", "Server logout failed; clearing local session", e)
                }
                PresentationAccess.client.clearSession()
                val dao = PresentationAccess.database.cacheDao()
                dao.deleteByKey("ews_key")
                dao.deleteByKey("ews_token")
                dao.deleteByKey("session_domain")
                dao.getAll()
                    .filter { it.key.startsWith("profile:") }
                    .forEach { dao.delete(it) }
                com.breakyuna.esjzone.network.features.HistoryDataCache.clearMemory()
                completed = true
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsPageModel", "Failed to clear local session", e)
            } finally {
                if (!cancelled) {
                    mutableState.update { it.copy(
                        logoutInProgress = false,
                        logoutCompleted = completed,
                        logoutFailed = !completed
                    ) }
                }
            }
        }
    }
}
