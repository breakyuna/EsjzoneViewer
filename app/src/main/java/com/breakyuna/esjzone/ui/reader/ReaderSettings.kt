package com.breakyuna.esjzone.ui.reader

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

enum class ReaderScript {
    ORIGINAL,
    SIMPLIFIED,
    TRADITIONAL
}

enum class ReaderBackground {
    SYSTEM,
    PAPER,
    SEPIA,
    DARK;

    @Composable
    fun containerColor(): Color = when (this) {
        SYSTEM -> MaterialTheme.colorScheme.background
        PAPER -> Color(0xFFFFFBF2)
        SEPIA -> Color(0xFFF3E8D0)
        DARK -> Color(0xFF1C1B1F)
    }

    @Composable
    fun contentColor(): Color = when (this) {
        SYSTEM -> MaterialTheme.colorScheme.onBackground
        PAPER -> Color(0xFF302820)
        SEPIA -> Color(0xFF4A3B2A)
        DARK -> Color(0xFFF1F0F4)
    }
}

enum class ReaderFont {
    SYSTEM,
    SERIF,
    MONOSPACE;

    val family: FontFamily
        get() = when (this) {
            SYSTEM -> FontFamily.Default
            SERIF -> FontFamily.Serif
            MONOSPACE -> FontFamily.Monospace
        }
}

data class ReaderSettings(
    val background: ReaderBackground = ReaderBackground.SYSTEM,
    val font: ReaderFont = ReaderFont.SYSTEM,
    val fontSizeSp: Float = 18f,
    val letterSpacingSp: Float = 0.3f,
    val lineSpacingSp: Float = 10f,
    val paragraphSpacingDp: Float = 10f,
    val pageSpacingDp: Float = 32f,
    val horizontalPaddingDp: Float = 20f,
    val script: ReaderScript = ReaderScript.ORIGINAL
) {
    val lineHeightSp: Float
        get() = fontSizeSp + lineSpacingSp
}

/** Persists reader-only appearance preferences without coupling them to account data. */
object ReaderSettingsStore {

    private const val PREFERENCES = "reader_settings"
    private const val BACKGROUND = "background"
    private const val FONT = "font"
    private const val FONT_SIZE = "font_size"
    private const val LETTER_SPACING = "letter_spacing"
    private const val LINE_SPACING = "line_spacing"
    private const val PARAGRAPH_SPACING = "paragraph_spacing"
    private const val PAGE_SPACING = "page_spacing"
    private const val HORIZONTAL_PADDING = "horizontal_padding"
    private const val SCRIPT = "script"

    fun load(context: Context): ReaderSettings {
        val defaults = ReaderSettings()
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return ReaderSettings(
            background = enumValueOrDefault(
                preferences.readString(BACKGROUND),
                defaults.background
            ),
            font = enumValueOrDefault(
                preferences.readString(FONT),
                defaults.font
            ),
            fontSizeSp = preferences.readFloat(FONT_SIZE, defaults.fontSizeSp, 14f, 30f),
            letterSpacingSp = preferences.readFloat(
                LETTER_SPACING,
                defaults.letterSpacingSp,
                0f,
                2f
            ),
            lineSpacingSp = preferences.readFloat(LINE_SPACING, defaults.lineSpacingSp, 4f, 24f),
            paragraphSpacingDp = preferences.readFloat(
                PARAGRAPH_SPACING,
                defaults.paragraphSpacingDp,
                0f,
                32f
            ),
            pageSpacingDp = preferences.readFloat(PAGE_SPACING, defaults.pageSpacingDp, 16f, 80f),
            horizontalPaddingDp = preferences.readFloat(
                HORIZONTAL_PADDING,
                defaults.horizontalPaddingDp,
                12f,
                48f
            ),
            script = enumValueOrDefault(
                preferences.readString(SCRIPT),
                defaults.script
            )
        )
    }

    fun save(context: Context, settings: ReaderSettings) {
        val safeSettings = settings.sanitized()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(BACKGROUND, safeSettings.background.name)
            .putString(FONT, safeSettings.font.name)
            .putFloat(FONT_SIZE, safeSettings.fontSizeSp)
            .putFloat(LETTER_SPACING, safeSettings.letterSpacingSp)
            .putFloat(LINE_SPACING, safeSettings.lineSpacingSp)
            .putFloat(PARAGRAPH_SPACING, safeSettings.paragraphSpacingDp)
            .putFloat(PAGE_SPACING, safeSettings.pageSpacingDp)
            .putFloat(HORIZONTAL_PADDING, safeSettings.horizontalPaddingDp)
            .putString(SCRIPT, safeSettings.script.name)
            .apply()
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

    private fun ReaderSettings.sanitized(): ReaderSettings = copy(
        fontSizeSp = fontSizeSp.safeValue(18f, 14f, 30f),
        letterSpacingSp = letterSpacingSp.safeValue(0.3f, 0f, 2f),
        lineSpacingSp = lineSpacingSp.safeValue(10f, 4f, 24f),
        paragraphSpacingDp = paragraphSpacingDp.safeValue(10f, 0f, 32f),
        pageSpacingDp = pageSpacingDp.safeValue(32f, 16f, 80f),
        horizontalPaddingDp = horizontalPaddingDp.safeValue(20f, 12f, 48f)
    )

    private fun Float.safeValue(default: Float, min: Float, max: Float): Float =
        takeIf { isFinite() }?.coerceIn(min, max) ?: default

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String?,
        default: T
    ): T = value?.let { candidate ->
        runCatching { enumValueOf<T>(candidate) }.getOrNull()
    } ?: default
}
