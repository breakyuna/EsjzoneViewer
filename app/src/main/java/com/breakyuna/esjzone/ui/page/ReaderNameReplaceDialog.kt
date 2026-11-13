package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceRule
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceSettings
import kotlinx.coroutines.launch

@Composable
internal fun ReaderNameReplaceDialog(
    settings: ReaderNameReplaceSettings,
    onChange: (ReaderNameReplaceSettings) -> Unit,
    onDismiss: () -> Unit
) {
    // Keep partially typed patterns local; save the complete draft when the dialog closes.
    var draft by remember { mutableStateOf(settings) }
    var tab by remember { mutableStateOf(if (settings.activeBookKey.isNullOrBlank()) 1 else 0) }
    val bookKey = if (tab == 0) settings.activeBookKey else null
    val rules = draft.rules.filter { it.bookKey == bookKey }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun update(rule: ReaderNameReplaceRule) {
        draft = draft.copy(rules = draft.rules.map { if (it.id == rule.id) rule else it })
    }
    fun close() {
        onChange(draft)
        onDismiss()
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(12.dp).widthIn(max = 640.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TabRow(selectedTabIndex = tab, modifier = Modifier.weight(1f)) {
                        listOf(R.string.reader_name_replace_book, R.string.reader_name_replace_all).forEachIndexed { index, label ->
                            Tab(selected = tab == index, enabled = index == 1 || !settings.activeBookKey.isNullOrBlank(),
                                onClick = { tab = index }, text = { Text(stringResource(label)) })
                        }
                    }
                    IconButton(onClick = {
                        draft = draft.copy(rules = draft.rules + ReaderNameReplaceRule(source = "", target = "", bookKey = bookKey))
                        scope.launch { listState.animateScrollToItem(rules.size) }
                    }) {
                        Icon(Icons.Outlined.AddCircleOutline, stringResource(R.string.reader_name_replace_add))
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                    if (rules.isEmpty()) item {
                        Text(stringResource(R.string.reader_name_replace_empty),
                            modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(rules, key = { it.id }) { rule ->
                        val invalidPattern = rule.regex && rule.source.isNotEmpty() &&
                            remember(rule.source) { runCatching { java.util.regex.Pattern.compile(rule.source) }.isFailure }
                        Column(Modifier.padding(horizontal = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RuleField(rule.source, stringResource(R.string.reader_name_replace_source), invalidPattern,
                                    Modifier.weight(1f)) { update(rule.copy(source = it)) }
                                Text("→", style = MaterialTheme.typography.bodyLarge)
                                RuleField(rule.target, stringResource(R.string.reader_name_replace_target), false,
                                    Modifier.weight(1f)) { update(rule.copy(target = it)) }
                                IconButton(onClick = { draft = draft.copy(rules = draft.rules.filterNot { it.id == rule.id }) }) {
                                    Icon(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.reader_name_replace_delete))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = rule.enabled, onCheckedChange = { update(rule.copy(enabled = it)) })
                                Text(stringResource(R.string.reader_name_replace_rule_enabled), style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f))
                                FilterChip(selected = rule.regex, onClick = { update(rule.copy(regex = !rule.regex)) },
                                    label = { Text(stringResource(R.string.reader_name_replace_regex)) })
                            }
                            if (invalidPattern) Text(stringResource(R.string.reader_name_replace_invalid_regex),
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            HorizontalDivider()
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::close) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}

@Composable
private fun RuleField(value: String, label: String, error: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = label },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = color),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        decorationBox = { input ->
            Column(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                if (value.isEmpty()) {
                    androidx.compose.foundation.layout.Box {
                        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        input()
                    }
                } else input()
                HorizontalDivider(color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
            }
        }
    )
}
