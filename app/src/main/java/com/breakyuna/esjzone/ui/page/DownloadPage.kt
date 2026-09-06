package com.breakyuna.esjzone.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.offline.DownloadedNovelSummary
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.ui.component.QuietBackHeader
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.component.QuietNovelCover
import com.breakyuna.esjzone.ui.component.QuietSectionHeader
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.theme.quietEditorialColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Local-first management screen for complete and partially downloaded novels. */
object DownloadPage : Screen {

    private fun readResolve(): Any = DownloadPage

    override val key: ScreenKey = "DownloadPage"

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val model = rememberScreenModel { DownloadPageModel() }
        val state by model.state.collectAsState()
        val autoSave by GlobalSettings.readerAutoSave
        var editing by rememberSaveable { mutableStateOf(false) }
        var selectedUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingDeleteUrls by remember { mutableStateOf<List<String>>(emptyList()) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var settingsExpanded by remember { mutableStateOf(false) }

        val novels = (state as? DownloadPageModel.State.Content)?.novels.orEmpty()
        val visibleUrls = remember(novels) { novels.mapTo(LinkedHashSet()) { it.novelUrl } }
        val deleting = (state as? DownloadPageModel.State.Content)?.deleting == true

        fun leaveEditMode() {
            if (deleting) return
            editing = false
            selectedUrls = emptySet()
            pendingDeleteUrls = emptyList()
            showDeleteDialog = false
        }

        fun requestDelete(urls: Collection<String>) {
            val distinct = urls.distinct().filter { it.isNotBlank() }
            if (distinct.isNotEmpty() && !deleting) {
                pendingDeleteUrls = distinct
                showDeleteDialog = true
            }
        }

        LaunchedEffect(Unit) { model.refresh() }
        LaunchedEffect(visibleUrls) {
            selectedUrls = selectedUrls.intersect(visibleUrls)
        }

        BackHandler(enabled = editing && !showDeleteDialog) { leaveEditMode() }

        if (showDeleteDialog) {
            DownloadDeleteDialog(
                count = pendingDeleteUrls.size,
                onDismiss = {
                    showDeleteDialog = false
                    pendingDeleteUrls = emptyList()
                },
                onConfirm = {
                    val urls = pendingDeleteUrls
                    showDeleteDialog = false
                    pendingDeleteUrls = emptyList()
                    selectedUrls = selectedUrls - urls.toSet()
                    model.delete(urls)
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            QuietBackHeader(
                title = stringResource(R.string.downloads),
                onBack = {
                    if (editing) leaveEditMode() else navigator?.pop()
                },
                actions = {
                    if (editing && selectedUrls.isNotEmpty()) {
                        IconButton(
                            onClick = { requestDelete(selectedUrls) },
                            enabled = !deleting
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.download_delete_selected)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (editing) leaveEditMode() else editing = true
                        },
                        enabled = !deleting
                    ) {
                        Icon(
                            imageVector = if (editing) Icons.Filled.Done else Icons.Filled.Edit,
                            contentDescription = stringResource(
                                if (editing) R.string.download_edit_done else R.string.download_edit
                            )
                        )
                    }
                    Box {
                        IconButton(onClick = { settingsExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.download_settings)
                            )
                        }
                        DropdownMenu(
                            expanded = settingsExpanded,
                            onDismissRequest = { settingsExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.download_auto_save),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = stringResource(R.string.download_auto_save_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingIcon = {
                                    Switch(
                                        checked = autoSave,
                                        onCheckedChange = { model.setAutoSave(it) }
                                    )
                                },
                                onClick = { model.setAutoSave(!autoSave) }
                            )
                        }
                    }
                },
                belowContent = {
                    if (editing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    selectedUrls = if (selectedUrls == visibleUrls) {
                                        emptySet()
                                    } else {
                                        visibleUrls
                                    }
                                },
                                enabled = visibleUrls.isNotEmpty() && !deleting
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SelectAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.download_select_all))
                            }
                            Text(
                                text = stringResource(
                                    R.string.download_selected_count,
                                    selectedUrls.size
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )

            when (val current = state) {
                DownloadPageModel.State.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.5.dp)
                    }
                }

                is DownloadPageModel.State.Content -> {
                    if (current.novels.isEmpty()) {
                        QuietEmptyState(
                            title = stringResource(R.string.download_empty),
                            message = stringResource(R.string.download_empty_hint),
                            icon = Icons.Filled.Download,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        DownloadedNovelList(
                            novels = current.novels,
                            editing = editing,
                            selectedUrls = selectedUrls,
                            deleting = current.deleting,
                            onToggleSelection = { url ->
                                selectedUrls = if (url in selectedUrls) {
                                    selectedUrls - url
                                } else {
                                    selectedUrls + url
                                }
                            },
                            onDelete = { url -> requestDelete(listOf(url)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedNovelList(
    novels: List<DownloadedNovelSummary>,
    editing: Boolean,
    selectedUrls: Set<String>,
    deleting: Boolean,
    onToggleSelection: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val totalBytes = novels.sumOf { it.storageBytes }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = QuietEditorial.contentMaxWidth),
            contentPadding = PaddingValues(
                start = QuietEditorial.pagePadding,
                end = QuietEditorial.pagePadding,
                top = 14.dp,
                bottom = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "download-summary") {
                QuietSectionHeader(
                    title = stringResource(R.string.download_library),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = stringResource(
                        R.string.download_total_summary,
                        novels.size,
                        formatStorageSize(totalBytes)
                    ),
                    style = QuietEditorial.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = QuietEditorial.pagePadding)
                )
            }
            items(novels, key = { it.novelUrl }) { summary ->
                DownloadedNovelCard(
                    summary = summary,
                    editing = editing,
                    selected = summary.novelUrl in selectedUrls,
                    deleting = deleting,
                    onToggleSelection = { onToggleSelection(summary.novelUrl) },
                    onDelete = { onDelete(summary.novelUrl) }
                )
            }
        }
    }
}

@Composable
private fun DownloadedNovelCard(
    summary: DownloadedNovelSummary,
    editing: Boolean,
    selected: Boolean,
    deleting: Boolean,
    onToggleSelection: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = quietEditorialColors()
    val coverWidth = 76.dp
    val coverHeight = 96.dp
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = editing && !deleting, onClick = onToggleSelection),
        shape = QuietEditorial.cardShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            colors.cardSurface
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (editing) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() },
                    enabled = !deleting,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(top = 27.dp)
                )
            }
            QuietNovelCover(
                coverUrl = summary.coverUrl,
                title = summary.novelName,
                modifier = Modifier.size(width = coverWidth, height = coverHeight),
                isAdult = summary.manifest.isAdult
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(coverHeight),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = summary.novelName.ifBlank { stringResource(R.string.download_unknown_novel) },
                    style = QuietEditorial.cardTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(
                            R.string.download_chapters_count,
                            summary.downloadedChapterCount
                        ),
                        style = QuietEditorial.label,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatStorageSize(summary.storageBytes),
                        style = QuietEditorial.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                enabled = !deleting,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.download_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadDeleteDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = stringResource(R.string.download_delete_title, count),
                style = QuietEditorial.title
            )
        },
        text = {
            Text(
                text = stringResource(R.string.download_delete_confirm, count),
                style = QuietEditorial.body
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.download_delete_confirm_action),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.logout_cancel))
            }
        }
    )
}

private class DownloadPageModel : StateScreenModel<DownloadPageModel.State>(State.Loading) {

    sealed class State {
        data object Loading : State()
        data class Content(
            val novels: List<DownloadedNovelSummary>,
            val deleting: Boolean = false
        ) : State()
    }

    fun refresh() {
        screenModelScope.launch(Dispatchers.IO) {
            try {
                mutableState.value = State.Content(NovelDownloadStore.listDownloadedNovels())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e(
                    "DownloadPageModel",
                    "Failed to list downloaded novels",
                    e
                )
                mutableState.value = State.Content(emptyList())
            }
        }
    }

    fun delete(urls: Iterable<String>) {
        val targets = urls.distinct().filter { it.isNotBlank() }
        if (targets.isEmpty()) return
        val current = mutableState.value as? State.Content
        mutableState.value = current?.copy(deleting = true) ?: State.Content(emptyList(), true)
        screenModelScope.launch(Dispatchers.IO) {
            try {
                NovelDownloadStore.deleteAll(targets)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e(
                    "DownloadPageModel",
                    "Failed to delete downloaded novels",
                    e
                )
            }
            try {
                mutableState.value = State.Content(NovelDownloadStore.listDownloadedNovels())
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e(
                    "DownloadPageModel",
                    "Failed to refresh after deletion",
                    e
                )
                mutableState.value = State.Content(emptyList())
            }
        }
    }

    fun setAutoSave(enabled: Boolean) {
        GlobalSettings.setReaderAutoSave(enabled)
        screenModelScope.launch(Dispatchers.IO) {
            try {
                MainActivity.database.cacheDao().put(
                    GlobalSettings.READER_AUTO_SAVE_KEY,
                    enabled.toString()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e(
                    "DownloadPageModel",
                    "Failed to persist reader auto-save preference",
                    e
                )
            }
        }
    }
}

private fun formatStorageSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
