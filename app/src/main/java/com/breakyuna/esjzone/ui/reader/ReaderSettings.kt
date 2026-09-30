package com.breakyuna.esjzone.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
    DARK,
    MINT,
    LAVENDER,
    SLATE,
    OLED;

    @Composable
    fun containerColor(): Color = when (this) {
        SYSTEM -> MaterialTheme.colorScheme.background
        // Reader surfaces use stable low-glare palettes while controls remain
        // connected to the active Material color scheme.
        PAPER -> Color(0xFFFBF7F0)
        SEPIA -> Color(0xFFF4ECD8)
        DARK -> Color(0xFF12161A)
        MINT -> Color(0xFFEAF4EB)
        LAVENDER -> Color(0xFFF1EFF8)
        SLATE -> Color(0xFF232C35)
        OLED -> Color.Black
    }

    @Composable
    fun contentColor(): Color = when (this) {
        SYSTEM -> MaterialTheme.colorScheme.onBackground
        PAPER -> Color(0xFF2C2523)
        SEPIA -> Color(0xFF433422)
        DARK -> Color(0xFFD5DBDB)
        MINT -> Color(0xFF24382C)
        LAVENDER -> Color(0xFF353149)
        SLATE -> Color(0xFFE0E7EC)
        OLED -> Color(0xFFE5E5E5)
    }
}

enum class ReaderFont {
    SYSTEM,
    SERIF,
    MONOSPACE,
    SANS_SERIF,
    CURSIVE,
    SOURCE_HAN_SERIF,
    SOURCE_HAN_SANS,
    LXGW_WENKAI;

    @Composable
    fun family(): FontFamily = when (this) {
            SYSTEM -> FontFamily.Default
            SERIF -> FontFamily.Serif
            MONOSPACE -> FontFamily.Monospace
            SANS_SERIF -> FontFamily.SansSerif
            CURSIVE -> FontFamily.Cursive
            SOURCE_HAN_SERIF, SOURCE_HAN_SANS, LXGW_WENKAI -> ReaderFontStore.family(this)
        }
}

enum class ReaderPageAnimation {
    VERTICAL_SCROLL,
    HORIZONTAL_SLIDE,
    FADE,
    COVER
}

@Immutable
data class ReaderSettings(
    val background: ReaderBackground = ReaderBackground.SYSTEM,
    val font: ReaderFont = ReaderFont.SYSTEM,
    val fontSizeSp: Float = 18f,
    val letterSpacingSp: Float = 0.3f,
    val lineSpacingSp: Float = 10f,
    val paragraphSpacingDp: Float = 10f,
    val pageSpacingDp: Float = 32f,
    val horizontalPaddingDp: Float = 20f,
    val script: ReaderScript = ReaderScript.ORIGINAL,
    val pageAnimation: ReaderPageAnimation = ReaderPageAnimation.VERTICAL_SCROLL,
    val volumeKeyPaging: Boolean = false,
    val tapPagingEnabled: Boolean = true,
    val horizontalSwipePagingEnabled: Boolean = true,
    val autoResumeLastReading: Boolean = false,
    val leftTapForward: Boolean = false,
    val eyeProtectionEnabled: Boolean = false,
    val showSystemStatusBar: Boolean = false,
    val showSystemNavigationBar: Boolean = true,
    val showChapterName: Boolean = true,
    val showTimeBattery: Boolean = false
) {
    val lineHeightSp: Float
        get() = fontSizeSp + lineSpacingSp
}
