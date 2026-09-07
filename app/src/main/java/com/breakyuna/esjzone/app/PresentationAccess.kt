package com.breakyuna.esjzone.app

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import coil3.ImageLoader
import com.breakyuna.esjzone.AppLanguage
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.data.settings.ReaderSettingsDataStore
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.domain.repository.SettingsRepository
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.ui.designsystem.AppThemeVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Transitional presentation boundary for the legacy UI.
 *
 * Screens consume application-owned services through this access point while the concrete
 * network client, download store, cache and Room database remain implementation details of
 * the application container. The boundary is intentionally compatibility-only: it does not
 * alter request, persistence, download, or reader semantics.
 */
object PresentationAccess {
    private val container: AppContainer
        get() = EsjzoneApplication.instance.container

    val database: GeneralDatabase
        get() = container.database

    /**
     * Internal implementation detail consumed only by the design-system image
     * facade. Feature pages use AppImage/AppCoverImage/AppAvatarImage/
     * AppReaderImage and never receive the loader directly.
     */
    internal val imageLoader: ImageLoader
        get() = container.imageLoader

    fun imageCacheSizeBytes(): Long = container.imageLoader.diskCache?.size ?: 0L

    fun clearImageCaches() {
        container.imageLoader.memoryCache?.clear()
        container.imageLoader.diskCache?.clear()
    }

    val client: EsjzoneClient
        get() = EsjzoneClient

    val downloads: NovelDownloadStore
        get() = NovelDownloadStore

    val readerSettings: ReaderSettingsDataStore
        get() = container.readerSettingsDataStore

    val settings: SettingsStateBoundary = SettingsStateBoundary
}

/** Compose-compatible projection over the application-owned SettingsRepository. */
object SettingsStateBoundary {
    const val READER_AUTO_SAVE_KEY: String = SettingsDefaults.READER_AUTO_SAVE_KEY
    val DOMAINS: List<String> get() = SettingsDefaults.DOMAINS

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bindingLock = Any()
    private var boundRepository: SettingsRepository? = null

    private val _adult = mutableStateOf(true)
    private val _theme = mutableStateOf(AppThemeVariant.DEFAULT)
    private val _domain = mutableStateOf(SettingsDefaults.DOMAINS.first())
    private val _language = mutableStateOf(AppLanguage.SYSTEM)
    private val _readerAutoSave = mutableStateOf(false)

    private fun repository(): SettingsRepository {
        val repository = EsjzoneApplication.instance.container.settings
        if (boundRepository === repository) return repository
        synchronized(bindingLock) {
            if (boundRepository === repository) return repository
            boundRepository = repository
            _adult.value = repository.adult.value
            _theme.value = repository.theme.value
            _domain.value = repository.domain.value
            _language.value = repository.language.value
            _readerAutoSave.value = repository.readerAutoSave.value
            scope.launch { repository.adult.collect { _adult.value = it } }
            scope.launch { repository.theme.collect { _theme.value = it } }
            scope.launch { repository.domain.collect { _domain.value = it } }
            scope.launch { repository.language.collect { _language.value = it } }
            scope.launch { repository.readerAutoSave.collect { _readerAutoSave.value = it } }
        }
        return repository
    }

    val adult: State<Boolean> get() { repository(); return _adult }
    val adultFlow: StateFlow<Boolean> get() = repository().adult
    val theme: State<AppThemeVariant> get() { repository(); return _theme }
    val themeFlow: StateFlow<AppThemeVariant> get() = repository().theme
    val domain: State<String> get() { repository(); return _domain }
    val domainFlow: StateFlow<String> get() = repository().domain
    val language: State<AppLanguage> get() { repository(); return _language }
    val languageFlow: StateFlow<AppLanguage> get() = repository().language
    val readerAutoSave: State<Boolean> get() { repository(); return _readerAutoSave }
    val readerAutoSaveFlow: StateFlow<Boolean> get() = repository().readerAutoSave

    fun setAdult(value: Boolean) = repository().setAdult(value)
    fun setTheme(value: AppThemeVariant) = repository().setTheme(value)
    fun setDomain(value: String) = repository().setDomain(value)
    fun setLanguage(value: AppLanguage) = repository().setLanguage(value)
    fun setReaderAutoSave(value: Boolean) = repository().setReaderAutoSave(value)
}
