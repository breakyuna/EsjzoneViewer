package com.breakyuna.esjzone.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.breakyuna.esjzone.ui.reader.ReaderBackground
import com.breakyuna.esjzone.ui.reader.ReaderFont
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderPageAnimation
import com.breakyuna.esjzone.ui.reader.ReaderTool
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import com.breakyuna.esjzone.util.AppLogger

/** Preferences-backed reader appearance settings and one-time legacy SharedPreferences importer. */
class ReaderSettingsDataStore(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** Allows isolated stores in tests; the production default is stable for migration compatibility. */
    private val dataStoreFileName: String = FILE_NAME
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.applicationContext.preferencesDataStoreFile(dataStoreFileName) }
    )

    private val migrationMutex = Mutex()
    private var pendingReaderSave: Job? = null

    val settings: StateFlow<ReaderSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toReaderSettings() }
        .stateIn(scope, SharingStarted.Eagerly, ReaderSettings())

    suspend fun save(settings: ReaderSettings) {
        dataStore.edit { preferences ->
            settings.sanitized().writeTo(preferences)
        }
    }

    fun saveInBackground(settings: ReaderSettings) {
        scope.launch {
            try {
                save(settings)
            } catch (e: IOException) {
                AppLogger.e("ReaderSettingsDataStore", "Failed to save reader settings in background", e)
            }
        }
    }

    /** Debounces slider changes in the store's scope so leaving the reader does not discard them. */
    fun saveDebounced(settings: ReaderSettings) {
        synchronized(this) {
            pendingReaderSave?.cancel()
            pendingReaderSave = scope.launch {
                delay(250)
                try {
                    save(settings)
                } catch (e: IOException) {
                    AppLogger.e("ReaderSettingsDataStore", "Failed to save reader settings", e)
                }
            }
        }
    }

    /** Copies the legacy synchronous preference store once, keeping the Reader API compatible. */
    suspend fun migrateFromLegacy(context: Context) {
        migrationMutex.withLock {
            val current = runCatching { dataStore.data.first() }.getOrElse { emptyPreferences() }
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
                SCRIPT,
                PAGE_ANIMATION,
                VOLUME_KEY_PAGING,
                AUTO_RESUME_LAST_READING,
                TAP_PAGING,
                SCROLL_SIDE_GESTURES,
                PAGED_BOOKMARK_GESTURES,
                TOOLBAR_TOOLS,
                BRIGHTNESS
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
            script = enumOrDefault(preferences.readString(LEGACY_SCRIPT), defaults.script),
            pageAnimation = defaults.pageAnimation,
            volumeKeyPaging = defaults.volumeKeyPaging
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
            script = enumOrDefault(this[SCRIPT], defaults.script),
            pageAnimation = enumOrDefault(this[PAGE_ANIMATION], defaults.pageAnimation),
            volumeKeyPaging = this[VOLUME_KEY_PAGING] ?: defaults.volumeKeyPaging,
            tapPagingEnabled = this[TAP_PAGING] ?: defaults.tapPagingEnabled,
            scrollSideGesturesEnabled = this[SCROLL_SIDE_GESTURES] ?: defaults.scrollSideGesturesEnabled,
            pagedBookmarkGesturesEnabled = this[PAGED_BOOKMARK_GESTURES] ?: defaults.pagedBookmarkGesturesEnabled,
            toolbarTools = ReaderTool.decode(this[TOOLBAR_TOOLS]),
            brightness = this[BRIGHTNESS].safeBrightness(),
            autoResumeLastReading = this[AUTO_RESUME_LAST_READING] ?: defaults.autoResumeLastReading,
            leftTapForward = this[LEFT_TAP_FORWARD] ?: defaults.leftTapForward,
            eyeProtectionEnabled = this[EYE_PROTECTION] ?: defaults.eyeProtectionEnabled,
            showSystemStatusBar = this[SHOW_SYSTEM_STATUS_BAR] ?: defaults.showSystemStatusBar,
            showSystemNavigationBar = this[SHOW_SYSTEM_NAVIGATION_BAR] ?: defaults.showSystemNavigationBar,
            showChapterName = this[SHOW_CHAPTER_NAME] ?: defaults.showChapterName,
            showTimeBattery = this[SHOW_TIME_BATTERY] ?: defaults.showTimeBattery
        )
    }

    private fun ReaderSettings.sanitized() = copy(
        toolbarTools = toolbarTools.distinct().take(ReaderTool.MAX_VISIBLE),
        brightness = brightness.safeBrightness(),
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
        preferences[PAGE_ANIMATION] = pageAnimation.name
        preferences[VOLUME_KEY_PAGING] = volumeKeyPaging
        preferences[TAP_PAGING] = tapPagingEnabled
        preferences[SCROLL_SIDE_GESTURES] = scrollSideGesturesEnabled
        preferences[PAGED_BOOKMARK_GESTURES] = pagedBookmarkGesturesEnabled
        preferences[TOOLBAR_TOOLS] = toolbarTools.joinToString(",") { it.name }
        preferences[BRIGHTNESS] = brightness
        preferences[AUTO_RESUME_LAST_READING] = autoResumeLastReading
        preferences[LEFT_TAP_FORWARD] = leftTapForward
        preferences[EYE_PROTECTION] = eyeProtectionEnabled
        preferences[SHOW_SYSTEM_STATUS_BAR] = showSystemStatusBar
        preferences[SHOW_SYSTEM_NAVIGATION_BAR] = showSystemNavigationBar
        preferences[SHOW_CHAPTER_NAME] = showChapterName
        preferences[SHOW_TIME_BATTERY] = showTimeBattery
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

    private fun Float?.safeBrightness(): Float =
        if (this == null || !isFinite() || this < 0f) -1f else coerceIn(0.01f, 1f)

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
        val PAGE_ANIMATION = stringPreferencesKey("page_animation")
        val TAP_PAGING = booleanPreferencesKey("tap_paging")
        val SCROLL_SIDE_GESTURES = booleanPreferencesKey("scroll_side_gestures")
        val PAGED_BOOKMARK_GESTURES = booleanPreferencesKey("paged_bookmark_gestures")
        val TOOLBAR_TOOLS = stringPreferencesKey("toolbar_tools")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val VOLUME_KEY_PAGING = booleanPreferencesKey("volume_key_paging")
        val AUTO_RESUME_LAST_READING = booleanPreferencesKey("auto_resume_last_reading")
        val LEFT_TAP_FORWARD = booleanPreferencesKey("left_tap_forward")
        val EYE_PROTECTION = booleanPreferencesKey("eye_protection")
        val SHOW_SYSTEM_STATUS_BAR = booleanPreferencesKey("show_system_status_bar")
        val SHOW_SYSTEM_NAVIGATION_BAR = booleanPreferencesKey("show_system_navigation_bar")
        val SHOW_CHAPTER_NAME = booleanPreferencesKey("show_chapter_name")
        val SHOW_TIME_BATTERY = booleanPreferencesKey("show_time_battery")
        val MIGRATION_COMPLETE = androidx.datastore.preferences.core.booleanPreferencesKey(
            "legacy_reader_settings_migration_complete"
        )
    }
}
