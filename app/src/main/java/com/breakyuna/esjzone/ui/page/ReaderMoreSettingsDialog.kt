package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.reader_more_controls), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        MoreReaderToggle(stringResource(R.string.reader_tap_paging), draft.tapPagingEnabled) {
            commit(draft.copy(tapPagingEnabled = it))
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(stringResource(R.string.reader_left_tap_action), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !draft.leftTapForward,
                    onClick = { commit(draft.copy(leftTapForward = false)) },
                    label = { Text(stringResource(R.string.reader_left_tap_previous)) }
                )
                FilterChip(
                    selected = draft.leftTapForward,
                    onClick = { commit(draft.copy(leftTapForward = true)) },
                    label = { Text(stringResource(R.string.reader_left_tap_next)) }
                )
            }
        }
        MoreReaderToggle(stringResource(R.string.settings_volume_key_paging), draft.volumeKeyPaging) {
            commit(draft.copy(volumeKeyPaging = it))
        }
        MoreReaderToggle(stringResource(R.string.settings_auto_resume_reading), draft.autoResumeLastReading) {
            commit(draft.copy(autoResumeLastReading = it))
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.reader_more_display), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
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
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

