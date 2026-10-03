package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.offline.DownloadedChapterRecord
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ExportChapterSelectionDialog(
    novelUrl: String,
    title: String,
    initialSelection: Set<String>?,
    onDismiss: () -> Unit,
    onExport: (Set<String>) -> Unit
) {
    var failed by remember(novelUrl) { mutableStateOf(false) }
    val chapters by produceState<List<DownloadedChapterRecord>?>(null, novelUrl) {
        try {
            value = withContext(Dispatchers.IO) {
                PresentationAccess.downloads.manifest(novelUrl)?.chapters.orEmpty().filter { it.downloaded }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
            value = emptyList()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight(0.9f),
            shape = AppShapes.standard) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = AppTypography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                }
                val available = chapters
                if (available == null) {
                    CircularProgressIndicator(Modifier.padding(16.dp))
                } else if (available.isEmpty()) {
                    Text(stringResource(if (failed) R.string.novel_export_failed else R.string.novel_export_no_chapters))
                } else {
                    val urls = remember(available) { available.map { it.url }.toSet() }
                    var selected by rememberSaveable(novelUrl, stateSaver = DownloadChapterSelectionSaver) {
                        mutableStateOf(initialSelection?.intersect(urls) ?: urls)
                    }
                    var start by rememberSaveable(novelUrl) { mutableStateOf("") }
                    var end by rememberSaveable(novelUrl) { mutableStateOf("") }
                    val maxOrdinal = remember(available) { available.maxOf { it.index + 1 } }
                    val first = start.toIntOrNull()
                    val last = end.toIntOrNull()
                    val validRange = first != null && last != null && first >= 1 && last >= first && last <= maxOrdinal
                    val invalidRange = start.isNotBlank() && end.isNotBlank() && !validRange
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(start, { start = it.filter(Char::isDigit).take(6) },
                            modifier = Modifier.weight(1f), singleLine = true, isError = invalidRange,
                            label = { Text(stringResource(R.string.novel_download_range_start)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text("–")
                        OutlinedTextField(end, { end = it.filter(Char::isDigit).take(6) },
                            modifier = Modifier.weight(1f), singleLine = true, isError = invalidRange,
                            label = { Text(stringResource(R.string.novel_download_range_end)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        TextButton(enabled = validRange, onClick = {
                            if (first != null && last != null) {
                                selected = selected + available.filter { it.index + 1 in first..last }.map { it.url }
                            }
                        }) { Text(stringResource(R.string.novel_download_range_confirm)) }
                    }
                    if (invalidRange) {
                        Text(stringResource(R.string.novel_download_range_error, maxOrdinal),
                            color = MaterialTheme.colorScheme.error, style = AppTypography.bodySmall)
                    }
                    Row {
                        TextButton(onClick = { selected = urls }) { Text(stringResource(R.string.download_select_all)) }
                        TextButton(onClick = { selected = emptySet() }) {
                            Text(stringResource(R.string.novel_download_clear_selection))
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(available, key = { it.url }) { chapter ->
                            val checked = chapter.url in selected
                            Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) {
                                selected = if (it) selected + chapter.url else selected - chapter.url
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, onCheckedChange = null)
                                Text("${chapter.index + 1}. ${chapter.name}",
                                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                                    style = AppTypography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    HorizontalDivider()
                    Button(onClick = { onExport(selected) }, enabled = selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.novel_export_selected_action, selected.size))
                    }
                }
            }
        }
    }
}
