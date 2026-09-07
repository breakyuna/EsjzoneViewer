package com.breakyuna.esjzone.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.breakyuna.esjzone.ui.reader.ReaderBackground
import com.breakyuna.esjzone.ui.reader.ReaderFont
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import java.io.IOException

/** Preferences-backed reader appearance settings and one-time legacy SharedPreferences importer. */
class ReaderSettingsDataStore(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** Allows isolated stores in tests; the production default is stable for migration compatibility. */
    private val dataStoreFileName: String = FILE_NAME
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(dataStoreFileName) }
    )

    private val migrationMutex = Mutex()

    val settings: StateFlow<ReaderSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { it.toReaderSettings() }
        .stateIn(scope, SharingStarted.Eagerly, ReaderSettings())

    suspend fun save(settings: ReaderSettings) {
        dataStore.edit { preferences ->
            settings.sanitized().writeTo(preferences)
        }
    }

    fun saveInBackground(settings: ReaderSettings) {
        scope.launch { save(settings) }
    }

    /** Copies the legacy synchronous preference store once, keeping the Reader API compatible. */
    suspend fun migrateFromLegacy(context: Context) {
        migrationMutex.withLock {
            val current = dataStore.data.first()
            if (current[MIGRATION_COMPLETE] == true) return
            val hasCurrentSettings = listOf(
                BACKGROUND,
                FONT,
                FONT_SIZE,
                LETTER_SPACING,
                LINE_SPACING,
                PARAGRAPH_SPACING,
                PAGE_SPACING,
                HORIZONTAL_PADDING,
                SCRIPT
            ).any { current.contains(it) }
            if (!hasCurrentSettings) {
                dataStore.edit { preferences ->
                    readLegacy(context).sanitized().writeTo(preferences)
                }
            }
            dataStore.edit { it[MIGRATION_COMPLETE] = true }
        }
    }

    private fun readLegacy(context: Context): ReaderSettings {
        val defaults = ReaderSettings()
        val preferences = context.getSharedPreferences(LEGACY_FILE_NAME, Context.MODE_PRIVATE)
        return ReaderSettings(
            background = enumOrDefault(preferences.readString(LEGACY_BACKGROUND), defaults.background),
            font = enumOrDefault(preferences.readString(LEGACY_FONT), defaults.font),
            fontSizeSp = preferences.readFloat(LEGACY_FONT_SIZE, defaults.fontSizeSp, 14f, 30f),
            letterSpacingSp = preferences.readFloat(LEGACY_LETTER_SPACING, defaults.letterSpacingSp, 0f, 2f),
            lineSpacingSp = preferences.readFloat(LEGACY_LINE_SPACING, defaults.lineSpacingSp, 4f, 24f),
            paragraphSpacingDp = preferences.readFloat(LEGACY_PARAGRAPH_SPACING, defaults.paragraphSpacingDp, 0f, 32f),
            pageSpacingDp = preferences.readFloat(LEGACY_PAGE_SPACING, defaults.pageSpacingDp, 16f, 80f),
            horizontalPaddingDp = preferences.readFloat(LEGACY_HORIZONTAL_PADDING, defaults.horizontalPaddingDp, 12f, 48f),
            script = enumOrDefault(preferences.readString(LEGACY_SCRIPT), defaults.script)
        )
    }

    private fun Preferences.toReaderSettings(): ReaderSettings {
        val defaults = ReaderSettings()
        return ReaderSettings(
            background = enumOrDefault(this[BACKGROUND], defaults.background),
            font = enumOrDefault(this[FONT], defaults.font),
            fontSizeSp = this[FONT_SIZE].safeValue(defaults.fontSizeSp, 14f, 30f),
            letterSpacingSp = this[LETTER_SPACING].safeValue(defaults.letterSpacingSp, 0f, 2f),
            lineSpacingSp = this[LINE_SPACING].safeValue(defaults.lineSpacingSp, 4f, 24f),
            paragraphSpacingDp = this[PARAGRAPH_SPACING].safeValue(defaults.paragraphSpacingDp, 0f, 32f),
            pageSpacingDp = this[PAGE_SPACING].safeValue(defaults.pageSpacingDp, 16f, 80f),
            horizontalPaddingDp = this[HORIZONTAL_PADDING].safeValue(defaults.horizontalPaddingDp, 12f, 48f),
            script = enumOrDefault(this[SCRIPT], defaults.script)
        )
    }

    private fun ReaderSettings.sanitized() = copy(
        fontSizeSp = fontSizeSp.safeValue(18f, 14f, 30f),
        letterSpacingSp = letterSpacingSp.safeValue(0.3f, 0f, 2f),
        lineSpacingSp = lineSpacingSp.safeValue(10f, 4f, 24f),
        paragraphSpacingDp = paragraphSpacingDp.safeValue(10f, 0f, 32f),
        pageSpacingDp = pageSpacingDp.safeValue(32f, 16f, 80f),
        horizontalPaddingDp = horizontalPaddingDp.safeValue(20f, 12f, 48f)
    )

    private fun ReaderSettings.writeTo(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        preferences[BACKGROUND] = background.name
        preferences[FONT] = font.name
        preferences[FONT_SIZE] = fontSizeSp
        preferences[LETTER_SPACING] = letterSpacingSp
        preferences[LINE_SPACING] = lineSpacingSp
        preferences[PARAGRAPH_SPACING] = paragraphSpacingDp
        preferences[PAGE_SPACING] = pageSpacingDp
        preferences[HORIZONTAL_PADDING] = horizontalPaddingDp
        preferences[SCRIPT] = script.name
    }

    private fun SharedPreferences.readString(key: String): String? =
        runCatching { getString(key, null) }.getOrNull()

    private fun SharedPreferences.readFloat(
        key: String,
        default: Float,
        min: Float,
        max: Float
    ): Float = runCatching { getFloat(key, default) }
        .getOrNull()
        ?.takeIf { it.isFinite() }
        ?.coerceIn(min, max)
        ?: default

    private fun Float?.safeValue(default: Float, min: Float, max: Float): Float =
        this?.takeIf { it.isFinite() }?.coerceIn(min, max) ?: default

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        const val FILE_NAME = "reader_settings.preferences_pb"
        const val LEGACY_FILE_NAME = "reader_settings"
        const val LEGACY_BACKGROUND = "background"
        const val LEGACY_FONT = "font"
        const val LEGACY_FONT_SIZE = "font_size"
        const val LEGACY_LETTER_SPACING = "letter_spacing"
        const val LEGACY_LINE_SPACING = "line_spacing"
        const val LEGACY_PARAGRAPH_SPACING = "paragraph_spacing"
        const val LEGACY_PAGE_SPACING = "page_spacing"
        const val LEGACY_HORIZONTAL_PADDING = "horizontal_padding"
        const val LEGACY_SCRIPT = "script"
        val BACKGROUND = stringPreferencesKey("background")
        val FONT = stringPreferencesKey("font")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LETTER_SPACING = floatPreferencesKey("letter_spacing")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val PARAGRAPH_SPACING = floatPreferencesKey("paragraph_spacing")
        val PAGE_SPACING = floatPreferencesKey("page_spacing")
        val HORIZONTAL_PADDING = floatPreferencesKey("horizontal_padding")
        val SCRIPT = stringPreferencesKey("script")
        val MIGRATION_COMPLETE = androidx.datastore.preferences.core.booleanPreferencesKey(
            "legacy_reader_settings_migration_complete"
        )
    }
}
