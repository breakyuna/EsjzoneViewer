package com.breakyuna.esjzone.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.breakyuna.esjzone.AppLanguage
import com.breakyuna.esjzone.AppThemeMode
import com.breakyuna.esjzone.domain.repository.SettingsRepository
import com.breakyuna.esjzone.database.GeneralDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import com.breakyuna.esjzone.util.AppLogger

/** Stable application setting keys and values shared by the DataStore boundary and UI adapter. */
object SettingsDefaults {
    const val READER_AUTO_SAVE_KEY = "reader_auto_save"
    const val DOWNLOAD_CONCURRENCY_KEY = "download_concurrency"
    const val DEFAULT_DOWNLOAD_CONCURRENCY = 5
    const val MIN_DOWNLOAD_CONCURRENCY = 1
    const val MAX_DOWNLOAD_CONCURRENCY = 8
    val DOMAINS: List<String> = listOf("www.esjzone.cc", "www.esjzone.one")
    val NAVIGATION_ORDER: List<String> = listOf("HOME", "HISTORY", "BOOKSHELF", "PROFILE")
    const val START_TAB_KEY = "start_tab"
    const val START_TAB_FOLLOW_NAV = "FOLLOW_NAV"
    const val DEFAULT_START_TAB = "FOLLOW_NAV"
    val VALID_START_TABS: List<String> = listOf("FOLLOW_NAV", "HOME", "BOOKSHELF", "HISTORY", "PROFILE")
    const val DEFAULT_WENKU_LOGIN_DURATION = "315360000"
    val WENKU_LOGIN_DURATIONS: List<String> = listOf("0", "86400", "2592000", "315360000")
}

/** Preferences-backed settings boundary; callers can migrate independently of legacy storage. */
class SettingsDataStore(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** Allows isolated stores in tests; the production default is stable for migration compatibility. */
    private val dataStoreFileName: String = FILE_NAME
) : SettingsRepository {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.applicationContext.preferencesDataStoreFile(dataStoreFileName) }
    )

    private val defaults = SettingsValues()
    private val values: StateFlow<SettingsValues> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                AppLogger.e("SettingsDataStore", "Failed to read settings; using defaults", error)
                emit(emptyPreferences())
            } else throw error
        }
        .map { it.toSettingsValues() }
        .onEach { AppLogger.setDebugMode(it.debugLogging) }
        .stateIn(scope, SharingStarted.Eagerly, defaults)

    override val adult: StateFlow<Boolean> = values.map { it.adult }
        .stateIn(scope, SharingStarted.Eagerly, defaults.adult)
    override val themeMode: StateFlow<AppThemeMode> = values.map { it.themeMode }
        .stateIn(scope, SharingStarted.Eagerly, defaults.themeMode)
    override val hideHomeRecommendations: StateFlow<Boolean> = values.map { it.hideHomeRecommendations }
        .stateIn(scope, SharingStarted.Eagerly, defaults.hideHomeRecommendations)
    override val domain: StateFlow<String> = values.map { it.domain }
        .stateIn(scope, SharingStarted.Eagerly, defaults.domain)
    override val language: StateFlow<AppLanguage> = values.map { it.language }
        .stateIn(scope, SharingStarted.Eagerly, defaults.language)
    override val readingStatisticsIncognito: StateFlow<Boolean> = values.map { it.readingStatisticsIncognito }
        .stateIn(scope, SharingStarted.Eagerly, defaults.readingStatisticsIncognito)
    override val readerAutoSave: StateFlow<Boolean> = values.map { it.readerAutoSave }
        .stateIn(scope, SharingStarted.Eagerly, defaults.readerAutoSave)
    override val downloadConcurrency: StateFlow<Int> = values.map { it.downloadConcurrency }
        .stateIn(scope, SharingStarted.Eagerly, defaults.downloadConcurrency)
    override val novelListGridView: StateFlow<Boolean> = values.map { it.novelListGridView }
        .stateIn(scope, SharingStarted.Eagerly, defaults.novelListGridView)
    override val novelListAdultOnly: StateFlow<Boolean> = values.map { it.novelListAdultOnly }
        .stateIn(scope, SharingStarted.Eagerly, defaults.novelListAdultOnly)
    override val navigationOrder: StateFlow<List<String>> = values.map { it.navigationOrder }
        .stateIn(scope, SharingStarted.Eagerly, defaults.navigationOrder)
    override val startTab: StateFlow<String> = values.map { it.startTab }
        .stateIn(scope, SharingStarted.Eagerly, defaults.startTab)
    override val wenkuLoginDuration: StateFlow<String> = values.map { it.wenkuLoginDuration }
        .stateIn(scope, SharingStarted.Eagerly, defaults.wenkuLoginDuration)
    override val debugLogging: StateFlow<Boolean> = values.map { it.debugLogging }
        .stateIn(scope, SharingStarted.Eagerly, defaults.debugLogging)

    suspend fun restoreLoggingMode() {
        try {
            AppLogger.setDebugMode(dataStore.data.first()[DEBUG_LOGGING] ?: false)
        } catch (error: IOException) {
            AppLogger.e("SettingsDataStore", "Failed to restore logging mode", error)
        }
    }

    override fun setDebugLogging(value: Boolean) {
        AppLogger.setDebugMode(value)
        write("debug_logging", onFailure = { AppLogger.setDebugMode(debugLogging.value) }) { it[DEBUG_LOGGING] = value }
    }

    override fun setAdult(value: Boolean) = write("adult") { it[ADULT] = value }
    override fun setThemeMode(value: AppThemeMode) = write("theme_mode") { it[THEME_MODE] = value.name }
    override fun setHideHomeRecommendations(value: Boolean) = write("hide_home_recommendations") { it[HIDE_HOME_RECOMMENDATIONS] = value }
    override fun setDomain(value: String) {
        write("domain") { it[DOMAIN] = value.takeIf { candidate -> candidate in SettingsDefaults.DOMAINS } ?: defaults.domain }
    }
    override fun setLanguage(value: AppLanguage) = write("language") { it[LANGUAGE] = value.code }
    override fun setReadingStatisticsIncognito(value: Boolean) = write("reading_statistics_incognito") { it[READING_STATISTICS_INCOGNITO] = value }
    override fun setReaderAutoSave(value: Boolean) = write("reader_auto_save") { it[READER_AUTO_SAVE] = value }
    override fun setDownloadConcurrency(value: Int) = write("download_concurrency") {
        it[DOWNLOAD_CONCURRENCY] = value.coerceIn(
            SettingsDefaults.MIN_DOWNLOAD_CONCURRENCY,
            SettingsDefaults.MAX_DOWNLOAD_CONCURRENCY
        )
    }
    override fun setNovelListGridView(value: Boolean) = write("novel_list_grid_view") { it[NOVEL_LIST_GRID_VIEW] = value }
    override fun setNovelListAdultOnly(value: Boolean) = write("novel_list_adult_only") { it[NOVEL_LIST_ADULT_ONLY] = value }
    override fun setNavigationOrder(value: List<String>) = write("navigation_order") {
        it[NAVIGATION_ORDER] = normalizeNavigationOrder(value).joinToString(",")
    }
    override fun setStartTab(value: String) = write("start_tab") {
        it[START_TAB] = value.takeIf { candidate -> candidate in SettingsDefaults.VALID_START_TABS } ?: defaults.startTab
    }
    override fun setWenkuLoginDuration(value: String) = write("wenku_login_duration") {
        it[WENKU_LOGIN_DURATION] = value.takeIf { candidate -> candidate in SettingsDefaults.WENKU_LOGIN_DURATIONS }
            ?: defaults.wenkuLoginDuration
    }

    /** Copies legacy Room preferences once; authentication/session keys are intentionally excluded. */
    suspend fun migrateFromLegacy(database: GeneralDatabase) {
        dataStore.edit { preferences ->
            if (preferences[MIGRATION_COMPLETE] == true) return@edit
            val cache = database.cacheDao()
            preferences[ADULT] = cache.findByKey("show_adult")?.value?.toBooleanStrictOrNull()
                ?: cache.findByKey("adult")?.value?.toBooleanStrictOrNull()
                ?: defaults.adult
            preferences[DOMAIN] = cache.findByKey("domain")?.value
                ?.takeIf { it in SettingsDefaults.DOMAINS } ?: defaults.domain
            preferences[LANGUAGE] = cache.findByKey("language")?.value ?: defaults.language.code
            preferences[READER_AUTO_SAVE] = cache.findByKey(READER_AUTO_SAVE_KEY)
                ?.value?.toBooleanStrictOrNull() ?: defaults.readerAutoSave
            preferences[DOWNLOAD_CONCURRENCY] = cache.findByKey(SettingsDefaults.DOWNLOAD_CONCURRENCY_KEY)
                ?.value?.toIntOrNull()
                ?.coerceIn(SettingsDefaults.MIN_DOWNLOAD_CONCURRENCY, SettingsDefaults.MAX_DOWNLOAD_CONCURRENCY)
                ?: defaults.downloadConcurrency
            preferences[START_TAB] = cache.findByKey(SettingsDefaults.START_TAB_KEY)?.value
                ?.takeIf { it in SettingsDefaults.VALID_START_TABS } ?: defaults.startTab
            preferences[MIGRATION_COMPLETE] = true
        }

        // Remove only the settings rows after the DataStore marker is durable.
        // Session/cookie, profile, search and other cache rows are deliberately
        // left untouched. If the process dies before cleanup, the next startup
        // reaches this same branch because the marker is already complete.
        val isMigrated = runCatching { dataStore.data.first()[MIGRATION_COMPLETE] == true }.getOrDefault(false)
        if (isMigrated) {
            database.cacheDao().deleteByKey("show_adult")
            database.cacheDao().deleteByKey("adult")
            database.cacheDao().deleteByKey("theme")
            database.cacheDao().deleteByKey("domain")
            database.cacheDao().deleteByKey("language")
            database.cacheDao().deleteByKey(READER_AUTO_SAVE_KEY)
            database.cacheDao().deleteByKey(SettingsDefaults.DOWNLOAD_CONCURRENCY_KEY)
            database.cacheDao().deleteByKey(SettingsDefaults.START_TAB_KEY)
        }
    }

    private fun write(setting: String, onFailure: () -> Unit = {}, update: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch {
            val id = AppLogger.newTraceId("settings")
            val started = System.nanoTime()
            try {
                AppLogger.trace("SettingsDataStore") { "id=$id, setting=$setting, stage=write-start" }
                dataStore.edit { preferences -> update(preferences) }
                AppLogger.trace("SettingsDataStore") { "id=$id, setting=$setting, stage=write-end, elapsedMs=${(System.nanoTime() - started) / 1_000_000}" }
            } catch (e: IOException) {
                AppLogger.e("SettingsDataStore", "Failed to persist setting update: id=$id, setting=$setting", e)
                onFailure()
            }
        }
    }

    private data class SettingsValues(
        val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
        val adult: Boolean = true,
        val hideHomeRecommendations: Boolean = true,
        val domain: String = SettingsDefaults.DOMAINS.first(),
        val language: AppLanguage = AppLanguage.SYSTEM,
        val readingStatisticsIncognito: Boolean = false,
        val readerAutoSave: Boolean = true,
        val downloadConcurrency: Int = SettingsDefaults.DEFAULT_DOWNLOAD_CONCURRENCY,
        val novelListGridView: Boolean = false,
        val novelListAdultOnly: Boolean = false,
        val navigationOrder: List<String> = SettingsDefaults.NAVIGATION_ORDER,
        val startTab: String = SettingsDefaults.DEFAULT_START_TAB,
        val wenkuLoginDuration: String = SettingsDefaults.DEFAULT_WENKU_LOGIN_DURATION,
        val debugLogging: Boolean = false
    )

    private fun Preferences.toSettingsValues(): SettingsValues = SettingsValues(
        themeMode = AppThemeMode.fromName(this[THEME_MODE]),
        adult = this[ADULT] ?: defaults.adult,
        hideHomeRecommendations = this[HIDE_HOME_RECOMMENDATIONS] ?: defaults.hideHomeRecommendations,
        domain = this[DOMAIN]?.takeIf { it in SettingsDefaults.DOMAINS } ?: defaults.domain,
        language = AppLanguage.fromCode(this[LANGUAGE]),
        readingStatisticsIncognito = this[READING_STATISTICS_INCOGNITO] ?: defaults.readingStatisticsIncognito,
        readerAutoSave = this[READER_AUTO_SAVE] ?: defaults.readerAutoSave,
        downloadConcurrency = this[DOWNLOAD_CONCURRENCY]
            ?.coerceIn(SettingsDefaults.MIN_DOWNLOAD_CONCURRENCY, SettingsDefaults.MAX_DOWNLOAD_CONCURRENCY)
            ?: defaults.downloadConcurrency,
        novelListGridView = this[NOVEL_LIST_GRID_VIEW] ?: defaults.novelListGridView,
        novelListAdultOnly = this[NOVEL_LIST_ADULT_ONLY] ?: defaults.novelListAdultOnly,
        navigationOrder = normalizeNavigationOrder(
            this[NAVIGATION_ORDER]?.split(',').orEmpty()
        ),
        startTab = this[START_TAB]?.takeIf { it in SettingsDefaults.VALID_START_TABS } ?: defaults.startTab,
        wenkuLoginDuration = this[WENKU_LOGIN_DURATION]?.takeIf { it in SettingsDefaults.WENKU_LOGIN_DURATIONS }
            ?: defaults.wenkuLoginDuration,
        debugLogging = this[DEBUG_LOGGING] ?: defaults.debugLogging
    )

    private fun normalizeNavigationOrder(value: List<String>): List<String> {
        val known = value.filter { it in SettingsDefaults.NAVIGATION_ORDER }.distinct()
        return known + SettingsDefaults.NAVIGATION_ORDER.filterNot { it in known }
    }

    private companion object {
        const val FILE_NAME = "settings.preferences_pb"
        const val READER_AUTO_SAVE_KEY = SettingsDefaults.READER_AUTO_SAVE_KEY
        val ADULT = booleanPreferencesKey("adult")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HIDE_HOME_RECOMMENDATIONS = booleanPreferencesKey("hide_home_recommendations")
        val DOMAIN = stringPreferencesKey("domain")
        val LANGUAGE = stringPreferencesKey("language")
        val READING_STATISTICS_INCOGNITO = booleanPreferencesKey("reading_statistics_incognito")
        val READER_AUTO_SAVE = booleanPreferencesKey("reader_auto_save")
        val DOWNLOAD_CONCURRENCY = intPreferencesKey("download_concurrency")
        val NOVEL_LIST_GRID_VIEW = booleanPreferencesKey("novel_list_grid_view")
        val NOVEL_LIST_ADULT_ONLY = booleanPreferencesKey("novel_list_adult_only")
        val NAVIGATION_ORDER = stringPreferencesKey("navigation_order")
        val START_TAB = stringPreferencesKey("start_tab")
        val WENKU_LOGIN_DURATION = stringPreferencesKey("wenku_login_duration")
        val DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
        val MIGRATION_COMPLETE = booleanPreferencesKey("legacy_room_migration_complete")
    }
}
