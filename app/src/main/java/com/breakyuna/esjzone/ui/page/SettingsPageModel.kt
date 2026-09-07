package com.breakyuna.esjzone.ui.page
import com.breakyuna.esjzone.app.PresentationAccess

import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.AppLanguage
import com.breakyuna.esjzone.ui.designsystem.AppThemeVariant
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.features.logout
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Coordinates settings persistence and cache/session maintenance off the UI. */
class SettingsPageModel : AppStateViewModel<SettingsPageModel.State>(State()) {

    data class State(
        val cacheStats: LocalCacheStats? = null,
        val cacheOperation: Operation? = null,
        val cacheStatsError: Boolean = false,
        val cacheClearError: Boolean = false,
        val logoutInProgress: Boolean = false,
        val logoutCompleted: Boolean = false
    )

    enum class Operation { PAGES, IMAGES }

    fun persist(key: String, value: String) {
        screenModelScope.launch(Dispatchers.IO) {
            try {
                when (key) {
                    "adult", "show_adult" -> PresentationAccess.settings.setAdult(value.toBooleanStrictOrNull() ?: return@launch)
                    "theme" -> PresentationAccess.settings.setTheme(AppThemeVariant.fromPersistedName(value))
                    "domain" -> PresentationAccess.settings.setDomain(value)
                    "language" -> PresentationAccess.settings.setLanguage(AppLanguage.fromCode(value))
                    PresentationAccess.settings.READER_AUTO_SAVE_KEY ->
                        PresentationAccess.settings.setReaderAutoSave(value.toBooleanStrictOrNull() ?: return@launch)
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
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val pageStats = PresentationAccess.client.pageCacheStats()
                val imageBytes = PresentationAccess.imageLoader.diskCache?.size ?: 0L
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
        clear(Operation.PAGES) { PresentationAccess.client.clearPageCache() }
    }

    fun clearImageCache() {
        clear(Operation.IMAGES) {
            PresentationAccess.imageLoader.memoryCache?.clear()
            PresentationAccess.imageLoader.diskCache?.clear()
        }
    }

    private fun clear(operation: Operation, action: suspend () -> Unit) {
        mutableState.value = mutableState.value.copy(
            cacheOperation = operation,
            cacheClearError = false
        )
        screenModelScope.launch(Dispatchers.IO) {
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

    fun logout(authorization: Authorization) {
        if (mutableState.value.logoutInProgress) return
        mutableState.value = mutableState.value.copy(logoutInProgress = true)
        screenModelScope.launch(Dispatchers.IO) {
            var cancelled = false
            try {
                try {
                    PresentationAccess.client.logout(authorization)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w("SettingsPageModel", "Server logout failed; clearing local session", e)
                }
                PresentationAccess.client.clearSession(
                    authorization.domain.ifBlank { PresentationAccess.settings.domain.value }
                )
                val dao = PresentationAccess.database.cacheDao()
                dao.deleteByKey("ews_key")
                dao.deleteByKey("ews_token")
                dao.deleteByKey("session_domain")
                dao.getAll()
                    .filter { it.key.startsWith("profile:") }
                    .forEach { dao.delete(it) }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsPageModel", "Failed to clear local session", e)
            } finally {
                if (!cancelled) {
                    mutableState.value = mutableState.value.copy(
                        logoutInProgress = false,
                        logoutCompleted = true
                    )
                }
            }
        }
    }
}
