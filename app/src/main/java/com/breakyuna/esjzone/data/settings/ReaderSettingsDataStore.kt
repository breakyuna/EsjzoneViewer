package com.breakyuna.esjzone.data.settings

import android.content.Context
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
import com.breakyuna.esjzone.ui.reader.ReaderSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.IOException

/** DataStore foundation for reader appearance settings; existing callers remain untouched. */
class ReaderSettingsDataStore(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) }
    )

    val settings: Flow<ReaderSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { it.toReaderSettings() }

    suspend fun save(settings: ReaderSettings) {
        dataStore.edit { preferences ->
            val safe = settings.sanitized()
            preferences[BACKGROUND] = safe.background.name
            preferences[FONT] = safe.font.name
            preferences[FONT_SIZE] = safe.fontSizeSp
            preferences[LETTER_SPACING] = safe.letterSpacingSp
            preferences[LINE_SPACING] = safe.lineSpacingSp
            preferences[PARAGRAPH_SPACING] = safe.paragraphSpacingDp
            preferences[PAGE_SPACING] = safe.pageSpacingDp
            preferences[HORIZONTAL_PADDING] = safe.horizontalPaddingDp
            preferences[SCRIPT] = safe.script.name
        }
    }

    fun saveInBackground(settings: ReaderSettings) {
        scope.launch { save(settings) }
    }

    /** Copies the legacy synchronous preference store once, keeping the Reader API compatible. */
    suspend fun migrateFromLegacy(context: Context) {
        val current = dataStore.data.first()
        if (current[MIGRATION_COMPLETE] == true) return
        if (current.asMap().keys.none { it.name != MIGRATION_COMPLETE.name }) {
            save(ReaderSettingsStore.load(context))
        }
        dataStore.edit { it[MIGRATION_COMPLETE] = true }
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

    private fun Float?.safeValue(default: Float, min: Float, max: Float): Float =
        this?.takeIf { it.isFinite() }?.coerceIn(min, max) ?: default

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        const val FILE_NAME = "reader_settings.preferences_pb"
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
