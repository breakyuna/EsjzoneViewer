package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.offline.ChapterSelectionCodec
import com.breakyuna.esjzone.offline.DownloadedNovelManifest
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DownloadChapterSelectionDialog(
    novel: DetailedNovel,
    downloaded: DownloadedNovelManifest?,
    selectedUrls: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onDownload: (Set<String>?) -> Unit
) {
    val prepared by produceState<Pair<DownloadChapterSelection, Set<String>>?>(
        null, novel.chapterList, downloaded
    ) {
        value = withContext(Dispatchers.Default) {
            val catalog = DownloadChapterSelection(novel.chapterList)
            val savedKeys = downloaded?.chapters.orEmpty().filter { it.downloaded }
                .map { NovelDownloadStore.chapterKey(it.url) }.toSet()
            catalog to catalog.chapters.filter { NovelDownloadStore.chapterKey(it.url) in savedKeys }
                .map { it.url }.toSet()
        }
    }
    var expandedOverride by rememberSaveable(novel.url) { mutableStateOf<Set<String>?>(null) }
    var showRange by rememberSaveable(novel.url) { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    var selectionTooLarge by remember(selectedUrls) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val catalog = prepared?.first
    val saved = prepared?.second.orEmpty()
    val selected = remember(selectedUrls, catalog, saved) {
        catalog?.pendingSelection(selectedUrls, saved).orEmpty()
    }
    val checked = remember(selected, saved) { selected + saved }
    val available = remember(catalog, saved) { catalog?.urls.orEmpty() - saved }
    val expanded = expandedOverride ?: catalog?.defaultExpandedIds.orEmpty()
    val currentSelection by rememberUpdatedState(selected)
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val rows = remember(catalog, expanded) { catalog?.visibleRows(expanded).orEmpty() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(if (compactHeight) 8.dp else 16.dp).widthIn(max = 640.dp)
                .fillMaxWidth().fillMaxHeight(if (compactHeight) 1f else 0.9f),
            shape = AppShapes.standard
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.novel_download_select_chapters),
                        style = AppTypography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onSelectionChange(available) }, enabled = available.isNotEmpty(),
                        modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.novel_download_select_missing), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { showRange = true }, enabled = available.isNotEmpty(),
                        modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.novel_download_range), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { onSelectionChange(emptySet()) }, enabled = selected.isNotEmpty()) {
                        Text(stringResource(R.string.novel_download_clear_selection))
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (catalog == null) {
                        item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                    } else if (catalog.urls.isEmpty()) {
                        item { Text(stringResource(R.string.novel_download_no_chapters), Modifier.padding(16.dp)) }
                    }
                    items(rows, key = { it.id }, contentType = { if (it is DownloadSelectionRow.Group) "group" else "chapter" }) { row ->
                        when (row) {
                            is DownloadSelectionRow.Group -> {
                                val count = remember(row.urls, checked) { row.urls.count { it in checked } }
                                val pendingUrls = remember(row.urls, saved) { row.urls - saved }
                                val state = when (count) {
                                    0 -> ToggleableState.Off
                                    row.urls.size -> ToggleableState.On
                                    else -> ToggleableState.Indeterminate
                                }
                                val countLabel = stringResource(R.string.novel_download_group_progress, count, row.urls.size)
                                Row(Modifier.fillMaxWidth().padding(start = (row.depth.coerceAtMost(3) * 12).dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    TriStateCheckbox(state = state, enabled = pendingUrls.isNotEmpty(), onClick = {
                                        onSelectionChange(if (state == ToggleableState.On) selected - pendingUrls else selected + pendingUrls)
                                    }, modifier = Modifier.semantics { contentDescription = "${row.title}, $countLabel" })
                                    Row(Modifier.weight(1f).clickable {
                                        expandedOverride = if (row.id in expanded) expanded - row.id else expanded + row.id
                                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(row.title, style = AppTypography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(if (row.id in expanded) countLabel else
                                                stringResource(R.string.novel_download_group_collapsed_progress, count, row.urls.size),
                                                style = AppTypography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(if (row.id in expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            stringResource(if (row.id in expanded) R.string.novel_download_collapse_group else R.string.novel_download_expand_group))
                                    }
                                }
                            }
                            is DownloadSelectionRow.Entry -> {
                                val isSaved = row.chapter.url in saved
                                val isSelected = row.chapter.url in selected
                                val rowModifier = Modifier.fillMaxWidth()
                                    .padding(start = (row.depth.coerceAtMost(3) * 12).dp)
                                    .clip(AppShapes.compact)
                                    .background(when {
                                        isSaved -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                        else -> MaterialTheme.colorScheme.surface
                                    })
                                Row(if (isSaved) rowModifier else rowModifier.toggleable(isSelected, role = Role.Checkbox, onValueChange = {
                                    onSelectionChange(if (it) selected + row.chapter.url else selected - row.chapter.url)
                                }), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(isSaved || isSelected, onCheckedChange = null, enabled = !isSaved)
                                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                                        Text("${row.ordinal}. ${row.chapter.name}", style = AppTypography.bodyMedium,
                                            color = if (isSaved) MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (isSaved) {
                                            Text(stringResource(R.string.novel_download_already_saved), style = AppTypography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                if (selectionTooLarge) {
                    Text(stringResource(R.string.novel_download_selection_too_large),
                        style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(0.9f)) {
                        Text(stringResource(R.string.novel_download_selection_total, checked.size, catalog?.chapters?.size ?: 0),
                            style = AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.novel_download_pending_count, selected.size),
                            style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                Button(onClick = {
                    val target = selected
                    if (target == available) {
                        // Full-book requests need no chapter list in WorkManager input.
                        onDownload(null)
                    } else {
                        validating = true
                        scope.launch {
                            val fits = withContext(Dispatchers.Default) {
                                try {
                                    ChapterSelectionCodec.encode(target)
                                    true
                                } catch (_: IllegalArgumentException) {
                                    false
                                }
                            }
                            validating = false
                            if (target == currentSelection) {
                                if (fits) onDownload(target) else selectionTooLarge = true
                            }
                        }
                    }
                }, enabled = selected.isNotEmpty() && !validating, modifier = Modifier.weight(1.1f)) {
                    if (validating) {
                        CircularProgressIndicator(strokeWidth = 2.dp,
                            modifier = Modifier.padding(end = 8.dp).size(18.dp))
                    }
                    Text(stringResource(R.string.novel_download_selected_action), maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                }
            }
        }
    }
    if (showRange && catalog != null) {
        DownloadRangeDialog(catalog, onDismiss = { showRange = false }, onApply = { range, add ->
            onSelectionChange(catalog.applyRange(selected, saved, range, add))
            showRange = false
        })
    }
}

@Composable
private fun DownloadRangeDialog(
    catalog: DownloadChapterSelection,
    onDismiss: () -> Unit,
    onApply: (Set<String>, Boolean) -> Unit
) {
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var addToSelection by rememberSaveable { mutableStateOf(true) }
    val range = remember(catalog, start, end) { catalog.range(start, end) }
    val invalid = start.isNotBlank() && end.isNotBlank() && range == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.novel_download_range)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.novel_download_range_hint, catalog.chapters.size))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = addToSelection, onClick = { addToSelection = true },
                        label = { Text(stringResource(R.string.novel_download_range_add)) })
                    FilterChip(selected = !addToSelection, onClick = { addToSelection = false },
                        label = { Text(stringResource(R.string.novel_download_range_remove)) })
                }
                OutlinedTextField(value = start, onValueChange = { start = it },
                    label = { Text(stringResource(R.string.novel_download_range_start)) }, singleLine = true,
                    isError = invalid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = end, onValueChange = { end = it },
                    label = { Text(stringResource(R.string.novel_download_range_end)) }, singleLine = true,
                    isError = invalid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (invalid) Text(stringResource(R.string.novel_download_range_error, catalog.chapters.size),
                    color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { range?.let { onApply(it, addToSelection) } }, enabled = range != null) {
                Text(stringResource(if (addToSelection) R.string.novel_download_range_add else R.string.novel_download_range_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
