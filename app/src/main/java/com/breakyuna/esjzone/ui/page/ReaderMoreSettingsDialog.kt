package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography

@Composable
internal fun ReaderMoreSettingsContent(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    fun commit(value: ReaderSettings) {
        draft = value
        onSettingsChange(value)
    }
    SettingsSection(title = stringResource(R.string.reader_more_controls)) {
        MoreReaderToggle(stringResource(R.string.reader_long_press_underline), draft.longPressUnderline) {
            commit(draft.copy(longPressUnderline = it))
        }
        MoreReaderToggle(stringResource(R.string.reader_scroll_side_gestures), draft.scrollSideGesturesEnabled) {
            commit(draft.copy(scrollSideGesturesEnabled = it))
        }
        MoreReaderToggle(stringResource(R.string.reader_paged_bookmark_gestures), draft.pagedBookmarkGesturesEnabled) {
            commit(draft.copy(pagedBookmarkGesturesEnabled = it))
        }
        MoreReaderToggle(stringResource(R.string.reader_tap_paging), draft.tapPagingEnabled) {
            commit(draft.copy(tapPagingEnabled = it))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)) {
            Text(stringResource(R.string.reader_left_tap_action), style = AppTypography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                FilterChip(
                    selected = !draft.leftTapForward,
                    onClick = { commit(draft.copy(leftTapForward = false)) },
                    shape = AppShapes.compact,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    label = { Text(stringResource(R.string.reader_left_tap_previous), style = AppTypography.labelMedium) }
                )
                FilterChip(
                    selected = draft.leftTapForward,
                    onClick = { commit(draft.copy(leftTapForward = true)) },
                    shape = AppShapes.compact,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    label = { Text(stringResource(R.string.reader_left_tap_next), style = AppTypography.labelMedium) }
                )
            }
        }
        MoreReaderToggle(stringResource(R.string.settings_volume_key_paging), draft.volumeKeyPaging) {
            commit(draft.copy(volumeKeyPaging = it))
        }
        MoreReaderToggle(stringResource(R.string.settings_auto_resume_reading), draft.autoResumeLastReading) {
            commit(draft.copy(autoResumeLastReading = it))
        }
    }
    SettingsSection(title = stringResource(R.string.reader_more_display)) {
        MoreReaderToggle(stringResource(R.string.reader_eye_protection), draft.eyeProtectionEnabled) {
            commit(draft.copy(eyeProtectionEnabled = it))
        }
        MoreReaderToggle(stringResource(R.string.reader_show_system_status), draft.showSystemStatusBar) {
            commit(draft.copy(showSystemStatusBar = it))
        }
        MoreReaderToggle(stringResource(R.string.reader_show_system_navigation), draft.showSystemNavigationBar) {
            commit(draft.copy(showSystemNavigationBar = it))
        }
        MoreReaderToggle(stringResource(R.string.reader_show_chapter_name), draft.showChapterName) {
            commit(draft.copy(showChapterName = it))
        }
        MoreReaderToggle(stringResource(R.string.reader_show_time_battery), draft.showTimeBattery) {
            commit(draft.copy(showTimeBattery = it))
        }
    }
}

@Composable
private fun MoreReaderToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onCheckedChange(!checked) }
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = AppTypography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
