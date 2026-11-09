package com.breakyuna.esjzone.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceSettings
import com.breakyuna.esjzone.ui.reader.ReaderSettings

@Composable
internal fun ChapterSettingsPanels(
    showBrightness: Boolean,
    showSettings: Boolean,
    settings: ReaderSettings,
    customImageAvailable: Boolean?,
    systemThemeSelected: Boolean,
    onFollowSystemTheme: () -> Unit,
    onConfigureImage: () -> Unit,
    onReflow: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismissSettings: () -> Unit,
    onDismissBrightness: () -> Unit,
    nameReplaceSettings: ReaderNameReplaceSettings,
    onNameReplaceChange: (ReaderNameReplaceSettings) -> Unit,
    modifier: Modifier
) {
    ReaderBrightnessSheet(
        visible = showBrightness,
        brightness = settings.brightness,
        onChange = { onSettingsChange(settings.copy(brightness = it)) },
        onDismiss = onDismissBrightness,
        modifier = modifier
    )
    ReaderSettingsSheet(
        visible = showSettings,
        settings = settings,
        customImageAvailable = customImageAvailable,
        systemThemeSelected = systemThemeSelected,
        onFollowSystemTheme = onFollowSystemTheme,
        onConfigureImage = onConfigureImage,
        onSettingsChange = { updated ->
            if (updated.pageAnimation != settings.pageAnimation ||
                updated.font != settings.font ||
                updated.firstLineIndentChars != settings.firstLineIndentChars ||
                updated.fontSizeSp != settings.fontSizeSp ||
                updated.letterSpacingSp != settings.letterSpacingSp ||
                updated.lineSpacingSp != settings.lineSpacingSp ||
                updated.paragraphSpacingDp != settings.paragraphSpacingDp ||
                updated.horizontalPaddingDp != settings.horizontalPaddingDp ||
                updated.script != settings.script
            ) {
                onReflow()
            }
            onSettingsChange(updated)
        },
        onDismiss = onDismissSettings,
        nameReplaceSettings = nameReplaceSettings,
        onNameReplaceChange = onNameReplaceChange,
        modifier = modifier
    )
}
