package com.breakyuna.esjzone.ui.reader

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
        // Reader surfaces use stable low-glare palettes while controls remain
        // connected to the active Material color scheme.
        PAPER -> Color(0xFFFBF7F0)
        SEPIA -> Color(0xFFF4ECD8)
        DARK -> Color(0xFF12161A)
    }

    @Composable
    fun contentColor(): Color = when (this) {
        SYSTEM -> MaterialTheme.colorScheme.onBackground
        PAPER -> Color(0xFF2C2523)
        SEPIA -> Color(0xFF433422)
        DARK -> Color(0xFFD5DBDB)
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
    val script: ReaderScript = ReaderScript.ORIGINAL,
    val volumeKeyPaging: Boolean = false
) {
    val lineHeightSp: Float
        get() = fontSizeSp + lineSpacingSp
}
