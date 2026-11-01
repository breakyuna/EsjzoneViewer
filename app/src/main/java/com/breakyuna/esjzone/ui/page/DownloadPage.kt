package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.ui.designsystem.wideListColumns
import com.breakyuna.esjzone.ui.designsystem.PreserveAdaptiveListAnchor
import com.breakyuna.esjzone.ui.designsystem.adaptiveItems
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle

import androidx.lifecycle.viewModelScope

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Switch
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.breakyuna.esjzone.ui.designsystem.AppSelectionReveal
import com.breakyuna.esjzone.ui.designsystem.AppAnimatedTitle
import com.breakyuna.esjzone.ui.designsystem.contentArrival
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.breakyuna.esjzone.ui.designsystem.AppMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.offline.DownloadedNovelSummary
import com.breakyuna.esjzone.novellibrary.novel.CoverState
import com.breakyuna.esjzone.ui.component.AppNovelCover
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
        val scanModel = rememberAppViewModel { StorageLocationModel() }
        val scanState by scanModel.state.collectAsState()
        val transfer by PresentationAccess.storage.transfer.collectAsState()
        val activeLocation by PresentationAccess.storage.activeLocation.collectAsState()
        val migrationLocked by PresentationAccess.downloads.migrating.collectAsState()
        val snackbarHost = remember { SnackbarHostState() }
        val scanResult = scanState.result?.let { stringResource(it) }
        LaunchedEffect(scanState.result) {
            if (scanState.result == R.string.storage_reindex_done) model.refresh()
            if (scanResult != null) {
                snackbarHost.showSnackbar(scanResult)
                scanModel.dismissResult()
            }
        }
        val adult by PresentationAccess.settings.adult
        val autoSave by PresentationAccess.settings.readerAutoSave
        var editing by rememberSaveable { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingDelete by remember { mutableStateOf<List<String>>(emptyList()) }
        var showDelete by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { com.breakyuna.esjzone.database.NovelTitleRepository.requestRepair() }
        val titleChanges by com.breakyuna.esjzone.database.NovelTitleRepository.titleChanges.collectAsState()
        val downloadChanges by PresentationAccess.downloads.changes.collectAsState()
        val entries = (state as? DownloadPageModel.State.Content)?.novels.orEmpty().filter { adult || !it.manifest.isAdult }
        val deleting = (state as? DownloadPageModel.State.Content)?.deleting == true
        LaunchedEffect(activeLocation, titleChanges, downloadChanges, deleting) {
            if (!deleting) model.refresh()
        }
        LaunchedEffect(entries) { selected = selected.intersect(entries.mapTo(LinkedHashSet()) { it.novelUrl }) }
        BackHandler(enabled = editing && !showDelete) {
            if (!deleting) { editing = false; selected = emptySet() }
        }

        fun requestDelete(urls: Collection<String>) {
            pendingDelete = urls.distinct().filter(String::isNotBlank)
            showDelete = pendingDelete.isNotEmpty()
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                TopAppBar(
                    title = { AppAnimatedTitle(if (editing) "${stringResource(R.string.download_edit)} (${selected.size})" else stringResource(R.string.downloads), style = AppTypography.titleLarge) },
                    navigationIcon = { BackIconButton { if (editing) { editing = false; selected = emptySet() } else navigator?.pop() } },
                    actions = {
                        AnimatedContent(
                            targetState = editing,
                            transitionSpec = { fadeIn(tween(AppMotion.standard)) togetherWith fadeOut(tween(AppMotion.quick)) },
                            label = "SelectionActions"
                        ) { displayedEditing ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (displayedEditing) {
                                    IconButton(onClick = { selected = if (selected.size == entries.size) emptySet() else entries.mapTo(LinkedHashSet()) { it.novelUrl } }, enabled = entries.isNotEmpty() && !deleting && !migrationLocked) {
                                        Icon(Icons.Filled.SelectAll, stringResource(R.string.download_select_all))
                                    }
                                    IconButton(onClick = { requestDelete(selected) }, enabled = selected.isNotEmpty() && !deleting && !migrationLocked) {
                                        Icon(Icons.Filled.DeleteOutline, stringResource(R.string.download_delete_selected), tint = MaterialTheme.colorScheme.error)
                                    }
                                    IconButton(onClick = { editing = false; selected = emptySet() }, enabled = !deleting) {
                                        Icon(Icons.Filled.Done, stringResource(R.string.download_edit_done))
                                    }
                                } else {
                                    TooltipBox(
                                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                        tooltip = { PlainTooltip { Text(stringResource(R.string.storage_reindex)) } },
                                        state = rememberTooltipState()
                                    ) {
                                        IconButton(onClick = scanModel::reindex, enabled = !scanState.busy && !deleting && !transfer.running) {
                                            if (scanState.busy) {
                                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Filled.FolderOpen, stringResource(R.string.storage_reindex))
                                            }
                                        }
                                    }
                                    IconButton(onClick = { editing = true }, enabled = entries.isNotEmpty() && !scanState.busy && !migrationLocked) { Icon(Icons.Filled.Edit, stringResource(R.string.download_edit)) }
                                    Box {
                                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, stringResource(R.string.download_settings)) }
                                        DropdownMenu(expanded = showSettings, onDismissRequest = { showSettings = false }) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.download_auto_save), style = AppTypography.labelLarge) },
                                                trailingIcon = { Switch(checked = autoSave, onCheckedChange = { model.setAutoSave(it) }) },
                                                onClick = { model.setAutoSave(!autoSave) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                StorageTransferStatus(transfer, onRetry = scanModel::retryTransfer)
                when (val current = state) {
                    DownloadPageModel.State.Loading -> LoadingSkeleton(Modifier.fillMaxWidth(), stringResource(R.string.downloads))
                    is DownloadPageModel.State.Error -> ErrorState(
                        title = stringResource(R.string.load_client_error),
                        message = current.message.ifBlank { stringResource(R.string.load_client_error) },
                        retryLabel = stringResource(R.string.retry),
                        onRetry = model::refresh,
                        modifier = Modifier.fillMaxSize().accountContentWidth()
                    )
                    is DownloadPageModel.State.Content -> if (entries.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.download_empty),
                            message = stringResource(R.string.download_empty_hint),
                            modifier = Modifier.fillMaxSize().accountContentWidth()
                        )
                    } else {
                        DownloadList(
                            novels = entries,
                            selected = selected,
                            editing = editing,
                            deleting = deleting || migrationLocked,
                            onToggle = { url -> selected = if (url in selected) selected - url else selected + url },
                            onDelete = { requestDelete(listOf(it)) },
                            onOpen = { navigator?.pushIfNotCurrent(DownloadedNovelPage(it)) },
                            modifier = Modifier.fillMaxSize().accountContentWidth()
                        )
                    }
                }
            }
        }

        if (showDelete) {
            AlertDialog(
                onDismissRequest = { if (!deleting) showDelete = false },
                title = { Text(stringResource(R.string.download_delete_title, pendingDelete.size)) },
                text = { Text(stringResource(R.string.download_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = { model.delete(pendingDelete); showDelete = false; pendingDelete = emptyList() }, enabled = !deleting && !migrationLocked) {
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
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = wideListColumns()
    val totalBytes = remember(novels) { novels.sumOf { it.storageBytes } }
    val listState = rememberLazyListState()
    PreserveAdaptiveListAnchor(listState, novels, columns, leadingItemCount = 1, key = { it.novelUrl })
    LazyColumn(
        state = listState,
        modifier = modifier.contentArrival(),
        contentPadding = PaddingValues(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        item(key = "download_summary", contentType = "download_summary") {
            Text(
                stringResource(R.string.download_total_summary, novels.size, if (novels.any { it.storageBytes < 0 }) stringResource(R.string.storage_size_calculating) else formatBytes(totalBytes)),
                style = AppTypography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        adaptiveItems(novels, columns = columns, key = { it.novelUrl }, contentType = { "download" }) { summary ->
            DownloadCard(summary, editing, summary.novelUrl in selected, deleting, { onToggle(summary.novelUrl) }, { onDelete(summary.novelUrl) }, { onOpen(summary.novelUrl) }, Modifier.animateItem())
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
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.standard)
            .then(
                if (selected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), AppShapes.standard)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.62f), AppShapes.standard)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surface, AppShapes.standard)
                }
            )
            .clickable(enabled = !deleting) { if (editing) onToggle() else onOpen() }
            .semantics { if (editing) role = Role.Button }
            .padding(AppSpacing.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        AppSelectionReveal(editing) { Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = !deleting) }
        AppNovelCover(
            coverUrl = summary.coverUrl,
            coverState = if (summary.coverUrl.isBlank()) CoverState.Unknown else null,
            title = validNovelTitle(summary.novelName, novelUrl = summary.novelUrl),
            modifier = Modifier.size(width = 64.dp, height = 88.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(validNovelTitle(summary.novelName, novelUrl = summary.novelUrl).ifBlank { stringResource(R.string.download_unknown_novel) }, style = AppTypography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (summary.manifest.pendingImageChapterCount > 0) Text(
                stringResource(R.string.novel_download_pending_images_count, summary.manifest.pendingImageChapterCount),
                style = AppTypography.bodyMedium)
            Text(if (summary.manifest.catalogComplete) stringResource(R.string.local_download_count, summary.downloadedChapterCount, summary.manifest.chapters.size)
                else stringResource(R.string.download_chapters_count, summary.downloadedChapterCount), style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(if (summary.storageBytes < 0) stringResource(R.string.storage_size_calculating) else formatBytes(summary.storageBytes), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (editing) IconButton(onClick = onDelete, enabled = !deleting) { Icon(Icons.Filled.DeleteOutline, stringResource(R.string.download_delete), tint = MaterialTheme.colorScheme.error) }
    }
}

private class DownloadPageModel : AppStateViewModel<DownloadPageModel.State>(State.Loading) {
    sealed class State {
        data object Loading : State()
        data class Content(val novels: List<DownloadedNovelSummary>, val deleting: Boolean = false) : State()
        data class Error(val message: String) : State()
    }

    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        if (mutableState.value !is State.Content) mutableState.value = State.Loading
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val novels = PresentationAccess.downloads.listDownloadedNovels(onMetadata = {
                    ensureActive()
                    mutableState.value = State.Content(it)
                })
                ensureActive()
                mutableState.value = State.Content(novels)
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
        val current = mutableState.value as? State.Content ?: return
        if (current.deleting) return
        refreshJob?.cancel()
        mutableState.value = current.copy(deleting = true)
        viewModelScope.launch(Dispatchers.IO) {
            try { PresentationAccess.downloads.deleteAll(targets) } catch (e: CancellationException) { throw e } catch (e: Exception) { AppLogger.e("DownloadPageModel", "Failed to delete downloaded novels", e) }
            refresh()
        }
    }

    fun setAutoSave(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PresentationAccess.settings.setReaderAutoSave(enabled)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { AppLogger.e("DownloadPageModel", "Failed to persist reader auto-save preference", e) }
        }
    }
}
