package com.breakyuna.esjzone.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.breakyuna.esjzone.AppLanguage
import com.breakyuna.esjzone.domain.repository.SettingsRepository
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.ui.designsystem.AppThemeVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** Stable application setting keys and values shared by the DataStore boundary and UI adapter. */
object SettingsDefaults {
    const val READER_AUTO_SAVE_KEY = "reader_auto_save"
    val DOMAINS: List<String> = listOf("www.esjzone.cc", "www.esjzone.one")
}

/** Preferences-backed settings boundary; callers can migrate independently of legacy storage. */
class SettingsDataStore(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : SettingsRepository {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) }
    )

    private val defaults = SettingsValues()
    private val values: StateFlow<SettingsValues> = dataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { it.toSettingsValues() }
        .stateIn(scope, SharingStarted.Eagerly, defaults)

    override val adult: StateFlow<Boolean> = values.map { it.adult }
        .stateIn(scope, SharingStarted.Eagerly, defaults.adult)
    override val theme: StateFlow<AppThemeVariant> = values.map { it.theme }
        .stateIn(scope, SharingStarted.Eagerly, defaults.theme)
    override val domain: StateFlow<String> = values.map { it.domain }
        .stateIn(scope, SharingStarted.Eagerly, defaults.domain)
    override val language: StateFlow<AppLanguage> = values.map { it.language }
        .stateIn(scope, SharingStarted.Eagerly, defaults.language)
    override val readerAutoSave: StateFlow<Boolean> = values.map { it.readerAutoSave }
        .stateIn(scope, SharingStarted.Eagerly, defaults.readerAutoSave)

    override fun setAdult(value: Boolean) = write { it[ADULT] = value }
    override fun setTheme(value: AppThemeVariant) = write { it[THEME] = value.name }
    override fun setDomain(value: String) {
        write { it[DOMAIN] = value.takeIf { candidate -> candidate in SettingsDefaults.DOMAINS } ?: defaults.domain }
    }
    override fun setLanguage(value: AppLanguage) = write { it[LANGUAGE] = value.code }
    override fun setReaderAutoSave(value: Boolean) = write { it[READER_AUTO_SAVE] = value }

    /** Copies legacy Room preferences once; authentication/session keys are intentionally excluded. */
    suspend fun migrateFromLegacy(database: GeneralDatabase) {
        dataStore.edit { preferences ->
            if (preferences[MIGRATION_COMPLETE] == true) return@edit
            val cache = database.cacheDao()
            preferences[ADULT] = cache.findByKey("show_adult")?.value?.toBooleanStrictOrNull()
                ?: cache.findByKey("adult")?.value?.toBooleanStrictOrNull()
                ?: defaults.adult
            preferences[THEME] = cache.findByKey("theme")?.value ?: defaults.theme.name
            preferences[DOMAIN] = cache.findByKey("domain")?.value
                ?.takeIf { it in SettingsDefaults.DOMAINS } ?: defaults.domain
            preferences[LANGUAGE] = cache.findByKey("language")?.value ?: defaults.language.code
            preferences[READER_AUTO_SAVE] = cache.findByKey(READER_AUTO_SAVE_KEY)
                ?.value?.toBooleanStrictOrNull() ?: defaults.readerAutoSave
            preferences[MIGRATION_COMPLETE] = true
        }

        // Remove only the settings rows after the DataStore marker is durable.
        // Session/cookie, profile, search and other cache rows are deliberately
        // left untouched. If the process dies before cleanup, the next startup
        // reaches this same branch because the marker is already complete.
        if (dataStore.data.first()[MIGRATION_COMPLETE] == true) {
            database.cacheDao().deleteByKey("show_adult")
            database.cacheDao().deleteByKey("adult")
            database.cacheDao().deleteByKey("theme")
            database.cacheDao().deleteByKey("domain")
            database.cacheDao().deleteByKey("language")
            database.cacheDao().deleteByKey(READER_AUTO_SAVE_KEY)
        }
    }

    private fun write(update: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { dataStore.edit { preferences -> update(preferences) } }
    }

    private data class SettingsValues(
        val adult: Boolean = true,
        val theme: AppThemeVariant = AppThemeVariant.DEFAULT,
        val domain: String = SettingsDefaults.DOMAINS.first(),
        val language: AppLanguage = AppLanguage.SYSTEM,
        val readerAutoSave: Boolean = false
    )

    private fun Preferences.toSettingsValues(): SettingsValues = SettingsValues(
        adult = this[ADULT] ?: defaults.adult,
        theme = this[THEME]?.let { value -> AppThemeVariant.entries.firstOrNull { it.name == value } }
            ?: defaults.theme,
        domain = this[DOMAIN]?.takeIf { it in SettingsDefaults.DOMAINS } ?: defaults.domain,
        language = AppLanguage.fromCode(this[LANGUAGE]),
        readerAutoSave = this[READER_AUTO_SAVE] ?: defaults.readerAutoSave
    )

    private companion object {
        const val FILE_NAME = "settings.preferences_pb"
        const val READER_AUTO_SAVE_KEY = SettingsDefaults.READER_AUTO_SAVE_KEY
        val ADULT = booleanPreferencesKey("adult")
        val THEME = stringPreferencesKey("theme")
        val DOMAIN = stringPreferencesKey("domain")
        val LANGUAGE = stringPreferencesKey("language")
        val READER_AUTO_SAVE = booleanPreferencesKey("reader_auto_save")
        val MIGRATION_COMPLETE = booleanPreferencesKey("legacy_room_migration_complete")
    }
}
