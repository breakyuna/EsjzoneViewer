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
import kotlinx.coroutines.launch

enum class CacheOperation { PAGES, IMAGES }

/** Coordinates settings persistence and cache/session maintenance off the UI. */
class SettingsPageModel : AppStateViewModel<SettingsPageModel.State>(State()) {

    data class State(
        val cacheStats: LocalCacheStats? = null,
        val cacheOperation: CacheOperation? = null,
        val cacheStatsError: Boolean = false,
        val cacheClearError: Boolean = false,
        val logoutInProgress: Boolean = false,
        val logoutCompleted: Boolean = false,
        val logoutFailed: Boolean = false
    )

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

    fun refreshCacheStats() {
        mutableState.value = mutableState.value.copy(cacheStatsError = false)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pageStats = PresentationAccess.client.pageCacheStats()
                val imageBytes = PresentationAccess.imageCacheSizeBytes()
                mutableState.value = mutableState.value.copy(
                    cacheStats = LocalCacheStats(pageStats.sizeBytes, pageStats.entryCount, imageBytes),
                    cacheStatsError = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsPageModel", "Failed to calculate cache statistics", e)
                mutableState.value = mutableState.value.copy(cacheStatsError = true)
            }
        }
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
        mutableState.value = mutableState.value.copy(
            cacheOperation = operation,
            cacheClearError = false
        )
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
            mutableState.value = mutableState.value.copy(
                cacheOperation = null,
                cacheClearError = failed
            )
            refreshCacheStats()
        }
    }

    fun clearLogoutFailure() {
        mutableState.value = mutableState.value.copy(logoutFailed = false)
    }

    fun logout(authorization: Authorization) {
        if (mutableState.value.logoutInProgress) return
        mutableState.value = mutableState.value.copy(
            logoutInProgress = true, logoutCompleted = false, logoutFailed = false
        )
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
                    mutableState.value = mutableState.value.copy(
                        logoutInProgress = false,
                        logoutCompleted = completed,
                        logoutFailed = !completed
                    )
                }
            }
        }
    }
}
