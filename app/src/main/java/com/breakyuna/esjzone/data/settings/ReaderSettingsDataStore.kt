package com.breakyuna.esjzone.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
import com.breakyuna.esjzone.ui.reader.ReaderImageBackground
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceRule
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceSettings
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.reflect.TypeToken
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
    private val dataStoreFileName: String = FILE_NAME,
    private val appSettings: SettingsDataStore? = null
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.applicationContext.preferencesDataStoreFile(dataStoreFileName) }
    )

    private val migrationMutex = Mutex()
    private var pendingReaderSave: Job? = null
    private val gson = Gson()

    val settings: StateFlow<ReaderSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toReaderSettings() }
        .stateIn(scope, SharingStarted.Eagerly, ReaderSettings())

    val imageBackground: StateFlow<ReaderImageBackground> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toImageBackground() }
        .stateIn(scope, SharingStarted.Eagerly, ReaderImageBackground())

    val nameReplaceSettings: StateFlow<ReaderNameReplaceSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toNameReplaceSettings() }
        .stateIn(scope, SharingStarted.Eagerly, ReaderNameReplaceSettings())

    /** Reader table-of-contents order; false keeps the site's ascending order. */
    val tocDescending: StateFlow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[TOC_DESCENDING] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun saveTocDescending(descending: Boolean) {
        dataStore.edit { it[TOC_DESCENDING] = descending }
    }

    suspend fun readImageBackground(): ReaderImageBackground = dataStore.data.first().toImageBackground()

    suspend fun saveNameReplaceSettings(settings: ReaderNameReplaceSettings) {
        val normalized = settings.copy(
            rules = settings.rules
                .filter { it.source.isNotBlank() }
                .distinctBy { it.id }
        )
        dataStore.edit {
            it[NAME_REPLACE_ENABLED] = normalized.enabled
            it[NAME_REPLACE_RULES] = gson.toJson(normalized.rules.map { rule ->
                NameReplaceRuleJson(rule.id, rule.source, rule.target, rule.enabled)
            })
        }
    }

    /** Archive only reader preferences, using stable key names instead of reflective field names. */
    internal suspend fun backupSnapshot(): Pair<JsonObject, ReaderImageBackground> {
        synchronized(this) { pendingReaderSave }?.join()
        val preferences = dataStore.data.first()
        val json = JsonObject()
        for (key in BACKUP_STRING_KEYS) preferences[key]?.let { json.addProperty(key.name, it) }
        json.addProperty(FIRST_LINE_INDENT_CHARS.name, preferences.readIndentChars())
        for (key in BACKUP_FLOAT_KEYS) preferences[key]?.let { json.addProperty(key.name, it) }
        for (key in BACKUP_LONG_KEYS) preferences[key]?.let { json.addProperty(key.name, it) }
        for (key in BACKUP_BOOLEAN_KEYS) preferences[key]?.let { json.addProperty(key.name, it) }
        appSettings?.readUpdateWebsiteHistory()?.let { json.addProperty(WEBSITE_HISTORY.name, it) }
        return json to preferences.toImageBackground()
    }

    internal fun decodeBackup(json: JsonObject): Preferences = mutablePreferencesOf().apply {
        fun <T> read(keys: List<Preferences.Key<T>>, decode: (JsonPrimitive) -> T) {
            keys.forEach { key -> json[key.name]?.let { this[key] = decode(it.asJsonPrimitive) } }
        }
        read(BACKUP_STRING_KEYS) { require(it.isString); it.asString }
        read(BACKUP_FLOAT_KEYS) { require(it.isNumber); it.asFloat.also { number -> require(number.isFinite()) } }
        read(BACKUP_LONG_KEYS) { require(it.isNumber); it.asString.toLong() }
        read(BACKUP_BOOLEAN_KEYS + WEBSITE_HISTORY) { require(it.isBoolean); it.asBoolean }
        read(listOf(FIRST_LINE_INDENT_CHARS)) { require(it.isNumber); it.asString.toInt().coerceIn(0, 2) }
        if (!contains(FIRST_LINE_INDENT_CHARS)) {
            // Early v3 ZIP archives used the same boolean key as the old DataStore.
            read(listOf(LEGACY_FIRST_LINE_INDENT)) { require(it.isBoolean); it.asBoolean }
        }
    }

    internal suspend fun restoreBackup(backup: Preferences, photoName: String?) {
        synchronized(this) { pendingReaderSave }?.join()
        dataStore.edit { preferences ->
            fun <T> replace(keys: List<Preferences.Key<T>>) {
                keys.forEach { key ->
                    val value = backup[key]
                    if (value == null) preferences.remove(key) else preferences[key] = value
                }
            }
            replace(BACKUP_STRING_KEYS)
            replace(BACKUP_FLOAT_KEYS)
            replace(BACKUP_LONG_KEYS)
            replace(BACKUP_BOOLEAN_KEYS)
            preferences[FIRST_LINE_INDENT_CHARS] = backup.readIndentChars()
            preferences.remove(LEGACY_FIRST_LINE_INDENT)
            if (photoName != null) {
                preferences[IMAGE_FILE] = photoName
            } else {
                preferences.remove(IMAGE_FILE)
                if (preferences[BACKGROUND] == ReaderBackground.CUSTOM_IMAGE.name) {
                    preferences[BACKGROUND] = ReaderBackground.SYSTEM.name
                }
            }
        }
    }

    internal suspend fun restoreWebsiteHistorySetting(backup: Preferences) {
        backup[WEBSITE_HISTORY]?.let { appSettings?.restoreUpdateWebsiteHistory(it) }
    }

    /** Only these fields change, so edits made elsewhere in reading settings survive applying a photo. */
    suspend fun saveImageBackground(background: ReaderImageBackground) {
        val pending = synchronized(this) { pendingReaderSave }
        pending?.join()
        val value = background.sanitized()
        dataStore.edit {
            it[IMAGE_FILE] = value.fileName
            it[IMAGE_BLUR] = value.blur
            it[IMAGE_TRANSPARENCY] = value.transparency
            it[IMAGE_CROP_X] = value.cropX
            it[IMAGE_CROP_Y] = value.cropY
            it[IMAGE_GLOW_MODE] = value.glowMode.name
            it[IMAGE_GLOW_LIGHT_L] = value.lightTextGlowL
            it[IMAGE_GLOW_DARK_L] = value.darkTextGlowL
            it[IMAGE_GLOW_CHROMA] = value.glowChromaRetention
            it[IMAGE_GLOW_OUTER_ALPHA] = value.outerGlowAlpha
            value.customGlowColor?.let { color -> it[IMAGE_GLOW_CUSTOM_COLOR] = color }
                ?: it.remove(IMAGE_GLOW_CUSTOM_COLOR)
            it[IMAGE_GLOW_AUTO_CLAMP] = value.autoClampLightness
            it[IMAGE_GLOW_ENABLED] = value.glowEnabled
            it[IMAGE_GLOW_STRENGTH] = value.glowStrength
            it[IMAGE_TEXT_COLOR] = value.textColor
            it[BACKGROUND] = ReaderBackground.CUSTOM_IMAGE.name
            it[THEME_BACKGROUND_IN_DARK] = false
        }
    }

    private fun Preferences.toImageBackground() = ReaderImageBackground(
        fileName = this[IMAGE_FILE].orEmpty(),
        textColor = this[IMAGE_TEXT_COLOR] ?: 0xFFFFFFFFL,
        glowMode = enumOrDefault(this[IMAGE_GLOW_MODE], com.breakyuna.esjzone.ui.reader.ReaderGlowMode.SAME_HUE),
        lightTextGlowL = this[IMAGE_GLOW_LIGHT_L] ?: 18f,
        darkTextGlowL = this[IMAGE_GLOW_DARK_L] ?: 96f,
        glowChromaRetention = this[IMAGE_GLOW_CHROMA] ?: 40f,
        outerGlowAlpha = this[IMAGE_GLOW_OUTER_ALPHA] ?: 0.9f,
        customGlowColor = this[IMAGE_GLOW_CUSTOM_COLOR],
        autoClampLightness = this[IMAGE_GLOW_AUTO_CLAMP] ?: true,
        glowEnabled = this[IMAGE_GLOW_ENABLED] ?: true,
        glowStrength = this[IMAGE_GLOW_STRENGTH] ?: 70f,
        blur = this[IMAGE_BLUR] ?: 0f,
        transparency = this[IMAGE_TRANSPARENCY] ?: 0f,
        cropX = this[IMAGE_CROP_X] ?: 0.5f,
        cropY = this[IMAGE_CROP_Y] ?: 0.5f
    ).sanitized()

    private fun Preferences.toNameReplaceSettings(): ReaderNameReplaceSettings {
        val rules = runCatching<List<NameReplaceRuleJson?>> {
            val raw = this[NAME_REPLACE_RULES] ?: return@runCatching emptyList()
            val type = object : TypeToken<List<NameReplaceRuleJson?>>() {}.type
            gson.fromJson(raw, type)
        }.getOrNull().orEmpty().mapNotNull { entry ->
            val rule = entry ?: return@mapNotNull null
            val source = rule.source ?: return@mapNotNull null
            if (source.isBlank()) return@mapNotNull null
            ReaderNameReplaceRule(
                id = rule.id?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
                source = source,
                target = rule.target.orEmpty(),
                enabled = rule.enabled ?: true
            )
        }
        return ReaderNameReplaceSettings(
            enabled = this[NAME_REPLACE_ENABLED] ?: false,
            rules = rules
        )
    }

    private data class NameReplaceRuleJson(
        val id: String? = null,
        val source: String? = null,
        val target: String? = null,
        val enabled: Boolean? = null
    )

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
            if (current.contains(LEGACY_FIRST_LINE_INDENT)) {
                dataStore.edit { preferences ->
                    preferences[FIRST_LINE_INDENT_CHARS] = preferences.readIndentChars()
                    preferences.remove(LEGACY_FIRST_LINE_INDENT)
                }
            }
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
                FIRST_LINE_INDENT_CHARS,
                LEGACY_FIRST_LINE_INDENT,
                BRIGHTNESS,
                KEEP_SCREEN_ON
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
            useThemeBackgroundInDarkMode = this[THEME_BACKGROUND_IN_DARK] ?: false,
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
            longPressUnderline = this[LONG_PRESS_UNDERLINE] ?: defaults.longPressUnderline,
            firstLineIndentChars = readIndentChars(),
            tapPagingEnabled = this[TAP_PAGING] ?: defaults.tapPagingEnabled,
            scrollSideGesturesEnabled = this[SCROLL_SIDE_GESTURES] ?: defaults.scrollSideGesturesEnabled,
            pagedBookmarkGesturesEnabled = this[PAGED_BOOKMARK_GESTURES] ?: defaults.pagedBookmarkGesturesEnabled,
            toolbarTools = ReaderTool.decode(this[TOOLBAR_TOOLS]),
            toolbarOrder = ReaderTool.decodeOrder(this[TOOLBAR_ORDER], ReaderTool.decode(this[TOOLBAR_TOOLS])),
            brightness = this[BRIGHTNESS].safeBrightness(),
            keepScreenOn = this[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            autoResumeLastReading = this[AUTO_RESUME_LAST_READING] ?: defaults.autoResumeLastReading,
            leftTapForward = this[LEFT_TAP_FORWARD] ?: defaults.leftTapForward,
            eyeProtectionEnabled = this[EYE_PROTECTION] ?: defaults.eyeProtectionEnabled,
            showSystemStatusBar = this[SHOW_SYSTEM_STATUS_BAR] ?: defaults.showSystemStatusBar,
            showSystemNavigationBar = this[SHOW_SYSTEM_NAVIGATION_BAR] ?: defaults.showSystemNavigationBar,
            showChapterName = this[SHOW_CHAPTER_NAME] ?: defaults.showChapterName,
            showTimeBattery = this[SHOW_TIME_BATTERY] ?: defaults.showTimeBattery
        )
    }

    private fun Preferences.readIndentChars(): Int =
        this[FIRST_LINE_INDENT_CHARS]?.coerceIn(0, 2) ?: if (this[LEGACY_FIRST_LINE_INDENT] == true) 2 else 0

    private fun ReaderSettings.sanitized() = copy(
        firstLineIndentChars = firstLineIndentChars.coerceIn(0, 2),
        toolbarTools = toolbarTools.distinct().take(ReaderTool.MAX_VISIBLE),
        toolbarOrder = (toolbarOrder + ReaderTool.entries).distinct(),
        brightness = brightness.safeBrightness(),
        fontSizeSp = fontSizeSp.safeValue(18f, 14f, 30f),
        letterSpacingSp = letterSpacingSp.safeValue(0.3f, 0f, 2f),
        lineSpacingSp = lineSpacingSp.safeValue(10f, 4f, 24f),
        paragraphSpacingDp = paragraphSpacingDp.safeValue(10f, 0f, 32f),
        pageSpacingDp = pageSpacingDp.safeValue(32f, 16f, 80f),
        horizontalPaddingDp = horizontalPaddingDp.safeValue(20f, 12f, 48f)
    )

    private fun ReaderSettings.writeTo(preferences: MutablePreferences) {
        preferences[BACKGROUND] = background.name
        preferences[THEME_BACKGROUND_IN_DARK] = useThemeBackgroundInDarkMode
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
        preferences[LONG_PRESS_UNDERLINE] = longPressUnderline
        preferences[FIRST_LINE_INDENT_CHARS] = firstLineIndentChars
        preferences.remove(LEGACY_FIRST_LINE_INDENT)
        preferences[TAP_PAGING] = tapPagingEnabled
        preferences[SCROLL_SIDE_GESTURES] = scrollSideGesturesEnabled
        preferences[PAGED_BOOKMARK_GESTURES] = pagedBookmarkGesturesEnabled
        preferences[TOOLBAR_TOOLS] = toolbarTools.joinToString(",") { it.name }
        preferences[TOOLBAR_ORDER] = toolbarOrder.joinToString(",") { it.name }
        preferences[BRIGHTNESS] = brightness
        preferences[KEEP_SCREEN_ON] = keepScreenOn
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
        val THEME_BACKGROUND_IN_DARK = booleanPreferencesKey("theme_background_in_dark")
        val BACKGROUND = stringPreferencesKey("background")
        val IMAGE_GLOW_MODE = stringPreferencesKey("background_image_glow_mode")
        val IMAGE_GLOW_LIGHT_L = floatPreferencesKey("background_image_glow_light_l")
        val IMAGE_GLOW_DARK_L = floatPreferencesKey("background_image_glow_dark_l")
        val IMAGE_GLOW_CHROMA = floatPreferencesKey("background_image_glow_chroma")
        val IMAGE_GLOW_OUTER_ALPHA = floatPreferencesKey("background_image_glow_outer_alpha")
        val IMAGE_GLOW_CUSTOM_COLOR = longPreferencesKey("background_image_glow_custom_color")
        val IMAGE_GLOW_AUTO_CLAMP = booleanPreferencesKey("background_image_glow_auto_clamp")
        val IMAGE_GLOW_ENABLED = booleanPreferencesKey("background_image_glow_enabled")
        val IMAGE_GLOW_STRENGTH = floatPreferencesKey("background_image_glow_strength")
        val IMAGE_FILE = stringPreferencesKey("background_image_file")
        val IMAGE_BLUR = floatPreferencesKey("background_image_blur")
        val IMAGE_TRANSPARENCY = floatPreferencesKey("background_image_transparency")
        val IMAGE_CROP_X = floatPreferencesKey("background_image_crop_x")
        val IMAGE_CROP_Y = floatPreferencesKey("background_image_crop_y")
        val IMAGE_TEXT_COLOR = longPreferencesKey("background_image_text_color")
        val FONT = stringPreferencesKey("font")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LETTER_SPACING = floatPreferencesKey("letter_spacing")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val PARAGRAPH_SPACING = floatPreferencesKey("paragraph_spacing")
        val PAGE_SPACING = floatPreferencesKey("page_spacing")
        val HORIZONTAL_PADDING = floatPreferencesKey("horizontal_padding")
        val SCRIPT = stringPreferencesKey("script")
        val PAGE_ANIMATION = stringPreferencesKey("page_animation")
        val FIRST_LINE_INDENT_CHARS = intPreferencesKey("first_line_indent_chars")
        val LEGACY_FIRST_LINE_INDENT = booleanPreferencesKey("first_line_indent")
        val LONG_PRESS_UNDERLINE = booleanPreferencesKey("long_press_underline")
        val TAP_PAGING = booleanPreferencesKey("tap_paging")
        val SCROLL_SIDE_GESTURES = booleanPreferencesKey("scroll_side_gestures")
        val PAGED_BOOKMARK_GESTURES = booleanPreferencesKey("paged_bookmark_gestures")
        val TOOLBAR_ORDER = stringPreferencesKey("toolbar_order")
        val TOOLBAR_TOOLS = stringPreferencesKey("toolbar_tools")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VOLUME_KEY_PAGING = booleanPreferencesKey("volume_key_paging")
        val AUTO_RESUME_LAST_READING = booleanPreferencesKey("auto_resume_last_reading")
        val LEFT_TAP_FORWARD = booleanPreferencesKey("left_tap_forward")
        val EYE_PROTECTION = booleanPreferencesKey("eye_protection")
        val SHOW_SYSTEM_STATUS_BAR = booleanPreferencesKey("show_system_status_bar")
        val SHOW_SYSTEM_NAVIGATION_BAR = booleanPreferencesKey("show_system_navigation_bar")
        val SHOW_CHAPTER_NAME = booleanPreferencesKey("show_chapter_name")
        val SHOW_TIME_BATTERY = booleanPreferencesKey("show_time_battery")
        val NAME_REPLACE_ENABLED = booleanPreferencesKey("name_replace_enabled")
        val NAME_REPLACE_RULES = stringPreferencesKey("name_replace_rules")
        val TOC_DESCENDING = booleanPreferencesKey("toc_descending")
        val MIGRATION_COMPLETE = booleanPreferencesKey("legacy_reader_settings_migration_complete")
        val WEBSITE_HISTORY = booleanPreferencesKey("update_website_history")

        val BACKUP_STRING_KEYS = listOf(
            BACKGROUND, FONT, SCRIPT, PAGE_ANIMATION, TOOLBAR_ORDER, TOOLBAR_TOOLS, IMAGE_GLOW_MODE, NAME_REPLACE_RULES
        )
        val BACKUP_FLOAT_KEYS = listOf(
            FONT_SIZE, LETTER_SPACING, LINE_SPACING, PARAGRAPH_SPACING, PAGE_SPACING, HORIZONTAL_PADDING,
            BRIGHTNESS, IMAGE_GLOW_LIGHT_L, IMAGE_GLOW_DARK_L, IMAGE_GLOW_CHROMA, IMAGE_GLOW_OUTER_ALPHA,
            IMAGE_GLOW_STRENGTH, IMAGE_BLUR, IMAGE_TRANSPARENCY, IMAGE_CROP_X, IMAGE_CROP_Y
        )
        val BACKUP_LONG_KEYS = listOf(IMAGE_GLOW_CUSTOM_COLOR, IMAGE_TEXT_COLOR)
        val BACKUP_BOOLEAN_KEYS = listOf(
            THEME_BACKGROUND_IN_DARK, LONG_PRESS_UNDERLINE, TAP_PAGING, SCROLL_SIDE_GESTURES,
            PAGED_BOOKMARK_GESTURES, KEEP_SCREEN_ON, VOLUME_KEY_PAGING, AUTO_RESUME_LAST_READING, LEFT_TAP_FORWARD,
            EYE_PROTECTION, SHOW_SYSTEM_STATUS_BAR, SHOW_SYSTEM_NAVIGATION_BAR, SHOW_CHAPTER_NAME,
            SHOW_TIME_BATTERY, IMAGE_GLOW_AUTO_CLAMP, IMAGE_GLOW_ENABLED, NAME_REPLACE_ENABLED, TOC_DESCENDING
        )
    }
}
