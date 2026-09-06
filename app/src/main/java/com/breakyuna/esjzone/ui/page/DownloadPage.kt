package com.breakyuna.esjzone.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.offline.DownloadedNovelSummary
import com.breakyuna.esjzone.ui.designsystem.AppImage
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.product.EmptyState
import com.breakyuna.esjzone.ui.product.ErrorState
import com.breakyuna.esjzone.ui.product.LoadingSkeleton
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Local download inventory. Deleting it never touches bookshelf/history/bookmarks. */
object DownloadPage : AppDestination {
    private fun readResolve(): Any = DownloadPage
    override val key: String = "DownloadPage"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val model = rememberAppViewModel { DownloadPageModel() }
        val state by model.state.collectAsState()
        val autoSave by PresentationAccess.settings.readerAutoSave
        var editing by rememberSaveable { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingDelete by remember { mutableStateOf<List<String>>(emptyList()) }
        var showDelete by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { model.refresh() }
        val entries = (state as? DownloadPageModel.State.Content)?.novels.orEmpty()
        val deleting = (state as? DownloadPageModel.State.Content)?.deleting == true
        LaunchedEffect(entries) { selected = selected.intersect(entries.mapTo(LinkedHashSet()) { it.novelUrl }) }
        BackHandler(enabled = editing && !showDelete) {
            if (!deleting) { editing = false; selected = emptySet() }
        }

        fun requestDelete(urls: Collection<String>) {
            pendingDelete = urls.distinct().filter(String::isNotBlank)
            showDelete = pendingDelete.isNotEmpty()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (editing) "${stringResource(R.string.download_edit)} (${selected.size})" else stringResource(R.string.downloads), style = AppTypography.titleLarge) },
                    navigationIcon = { BackIconButton { if (editing) { editing = false; selected = emptySet() } else navigator?.pop() } },
                    actions = {
                        if (editing) {
                            IconButton(onClick = { selected = if (selected.size == entries.size) emptySet() else entries.mapTo(LinkedHashSet()) { it.novelUrl } }, enabled = entries.isNotEmpty() && !deleting) {
                                Icon(Icons.Filled.SelectAll, stringResource(R.string.download_select_all))
                            }
                            IconButton(onClick = { requestDelete(selected) }, enabled = selected.isNotEmpty() && !deleting) {
                                Icon(Icons.Filled.DeleteOutline, stringResource(R.string.download_delete_selected), tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { editing = false; selected = emptySet() }, enabled = !deleting) {
                                Icon(Icons.Filled.Done, stringResource(R.string.download_edit_done))
                            }
                        } else {
                            IconButton(onClick = { editing = true }, enabled = entries.isNotEmpty()) { Icon(Icons.Filled.Edit, stringResource(R.string.download_edit)) }
                            Box {
                                IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, stringResource(R.string.download_settings)) }
                                DropdownMenu(expanded = showSettings, onDismissRequest = { showSettings = false }) {
                                    DropdownMenuItem(
                                        text = { Column {
                                            Text(stringResource(R.string.download_auto_save), style = AppTypography.labelLarge)
                                            Text(stringResource(R.string.download_auto_save_description), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } },
                                        trailingIcon = { Switch(checked = autoSave, onCheckedChange = { model.setAutoSave(it) }) },
                                        onClick = { model.setAutoSave(!autoSave) }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { padding ->
            when (val current = state) {
                DownloadPageModel.State.Loading -> LoadingSkeleton(Modifier.fillMaxWidth().padding(padding), stringResource(R.string.downloads))
                is DownloadPageModel.State.Error -> ErrorState(
                    title = stringResource(R.string.load_client_error),
                    message = current.message.ifBlank { stringResource(R.string.load_client_error) },
                    retryLabel = stringResource(R.string.retry),
                    onRetry = model::refresh,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                is DownloadPageModel.State.Content -> if (current.novels.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.download_empty),
                        message = stringResource(R.string.download_empty_hint),
                        modifier = Modifier.fillMaxSize().padding(padding)
                    )
                } else {
                    DownloadList(
                        novels = current.novels,
                        selected = selected,
                        editing = editing,
                        deleting = deleting,
                        onToggle = { url -> selected = if (url in selected) selected - url else selected + url },
                        onDelete = { requestDelete(listOf(it)) },
                        modifier = Modifier.fillMaxSize().padding(padding)
                    )
                }
            }
        }

        if (showDelete) {
            AlertDialog(
                onDismissRequest = { if (!deleting) showDelete = false },
                title = { Text(stringResource(R.string.download_delete_title, pendingDelete.size)) },
                text = { Text(stringResource(R.string.download_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = { model.delete(pendingDelete) }, enabled = !deleting) {
                        Text(stringResource(R.string.download_delete_confirm_action), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { showDelete = false }, enabled = !deleting) { Text(stringResource(android.R.string.cancel)) } }
            )
        }
    }
}

@Composable
private fun DownloadList(
    novels: List<DownloadedNovelSummary>,
    selected: Set<String>,
    editing: Boolean,
    deleting: Boolean,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalBytes = novels.sumOf { it.storageBytes }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        item(key = "download-summary") {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(stringResource(R.string.download_library), style = AppTypography.displayMedium)
                Text(stringResource(R.string.download_total_summary, novels.size, formatStorageSize(totalBytes)), style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(novels, key = { it.novelUrl }, contentType = { "download" }) { summary ->
            DownloadCard(summary, editing, summary.novelUrl in selected, deleting, { onToggle(summary.novelUrl) }, { onDelete(summary.novelUrl) })
        }
    }
}

@Composable
private fun DownloadCard(
    summary: DownloadedNovelSummary,
    editing: Boolean,
    selected: Boolean,
    deleting: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = { if (editing) onToggle() },
        enabled = editing && !deleting,
        modifier = Modifier.fillMaxWidth().semantics { if (editing) role = Role.Button },
        shape = AppShapes.standard,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(Modifier.fillMaxWidth().padding(AppSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            if (editing) Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = !deleting)
            AppImage(summary.coverUrl.takeIf(String::isNotBlank) ?: R.drawable.missing_cover, summary.novelName, Modifier.size(width = 64.dp, height = 88.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(summary.novelName.ifBlank { stringResource(R.string.download_unknown_novel) }, style = AppTypography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.download_chapters_count, summary.downloadedChapterCount), style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(formatStorageSize(summary.storageBytes), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, enabled = !deleting) { Icon(Icons.Filled.DeleteOutline, stringResource(R.string.download_delete), tint = MaterialTheme.colorScheme.error) }
        }
    }
}

private class DownloadPageModel : AppStateViewModel<DownloadPageModel.State>(State.Loading) {
    sealed class State {
        data object Loading : State()
        data class Content(val novels: List<DownloadedNovelSummary>, val deleting: Boolean = false) : State()
        data class Error(val message: String) : State()
    }

    fun refresh() {
        mutableState.value = State.Loading
        screenModelScope.launch(Dispatchers.IO) {
            try {
                mutableState.value = State.Content(PresentationAccess.downloads.listDownloadedNovels())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("DownloadPageModel", "Failed to list downloaded novels", e)
                mutableState.value = State.Error(e.message.orEmpty())
            }
        }
    }

    fun delete(urls: Iterable<String>) {
        val targets = urls.distinct().filter(String::isNotBlank)
        if (targets.isEmpty()) return
        val current = mutableState.value as? State.Content
        mutableState.value = current?.copy(deleting = true) ?: State.Content(emptyList(), true)
        screenModelScope.launch(Dispatchers.IO) {
            try { PresentationAccess.downloads.deleteAll(targets) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { AppLogger.e("DownloadPageModel", "Failed to delete downloaded novels", e) }
            refresh()
        }
    }

    fun setAutoSave(enabled: Boolean) {
        PresentationAccess.settings.setReaderAutoSave(enabled)
        screenModelScope.launch(Dispatchers.IO) {
            try {
                PresentationAccess.database.cacheDao().put(PresentationAccess.settings.READER_AUTO_SAVE_KEY, enabled.toString())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { AppLogger.e("DownloadPageModel", "Failed to persist reader auto-save preference", e) }
        }
    }
}

private fun formatStorageSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
