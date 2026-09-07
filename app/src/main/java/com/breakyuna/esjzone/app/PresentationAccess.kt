package com.breakyuna.esjzone.app

import androidx.compose.runtime.State
import coil3.ImageLoader
import com.breakyuna.esjzone.AppLanguage
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.ui.designsystem.AppThemeVariant
import kotlinx.coroutines.flow.StateFlow

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

    val imageLoader: ImageLoader
        get() = container.imageLoader

    val client: EsjzoneClient
        get() = EsjzoneClient

    val downloads: NovelDownloadStore
        get() = NovelDownloadStore

    val settings: SettingsStateBoundary = SettingsStateBoundary
}

/** Compatibility projection for Compose screens during the repository migration. */
object SettingsStateBoundary {
    const val READER_AUTO_SAVE_KEY: String = GlobalSettings.READER_AUTO_SAVE_KEY
    val DOMAINS: List<String> get() = GlobalSettings.DOMAINS

    val adult: State<Boolean> get() = GlobalSettings.adult
    val adultFlow: StateFlow<Boolean> get() = GlobalSettings.adultFlow
    val theme: State<AppThemeVariant> get() = GlobalSettings.theme
    val themeFlow: StateFlow<AppThemeVariant> get() = GlobalSettings.themeFlow
    val domain: State<String> get() = GlobalSettings.domain
    val domainFlow: StateFlow<String> get() = GlobalSettings.domainFlow
    val language: State<AppLanguage> get() = GlobalSettings.language
    val languageFlow: StateFlow<AppLanguage> get() = GlobalSettings.languageFlow
    val readerAutoSave: State<Boolean> get() = GlobalSettings.readerAutoSave
    val readerAutoSaveFlow: StateFlow<Boolean> get() = GlobalSettings.readerAutoSaveFlow

    fun setAdult(value: Boolean) = GlobalSettings.setAdult(value)
    fun setTheme(value: AppThemeVariant) = GlobalSettings.setTheme(value)
    fun setDomain(value: String) = GlobalSettings.setDomain(value)
    fun setLanguage(value: AppLanguage) = GlobalSettings.setLanguage(value)
    fun setReaderAutoSave(value: Boolean) = GlobalSettings.setReaderAutoSave(value)
}
