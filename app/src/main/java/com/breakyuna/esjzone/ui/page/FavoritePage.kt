package com.breakyuna.esjzone.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.ui.designsystem.AppImage
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.BooleanStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.product.EmptyState
import com.breakyuna.esjzone.ui.product.LoadingSkeleton
import com.breakyuna.esjzone.ui.product.OfflineState

/** Local-first bookshelf. Room is the only rendered source; sync is additive. */
object FavoritePage : AppDestination {
    private fun readResolve(): Any = FavoritePage

    @Composable
    override fun Content() = Content(showBack = true)

    @Composable
    fun Content(showBack: Boolean) {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { FavoritePageModel(authorization) }
        val entries by model.entries.collectAsState(initial = emptyList())
        val downloaded by model.downloadedBookKeys.collectAsState()
        val syncState by model.state.collectAsState()
        val deleteState by model.deleteState.collectAsState()
        val adult by PresentationAccess.settings.adult
        val snackbar = remember { SnackbarHostState() }
        val gridState = rememberLazyGridState()
        var editing by rememberSaveable { mutableStateOf(false) }
        var downloadedOnly by rememberSaveable { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingDelete by remember { mutableStateOf<List<BookshelfEntry>>(emptyList()) }
        var showDeleteDialog by remember { mutableStateOf(false) }

        val visible = remember(entries, adult) { entries.filter { adult || !it.isAdult } }
        val shown = remember(visible, downloaded, downloadedOnly) {
            if (downloadedOnly) visible.filter { it.bookKey in downloaded } else visible
        }
        val visibleKeys = remember(shown) { shown.mapTo(LinkedHashSet()) { it.bookKey } }
        val syncing = syncState is FavoritePageModel.State.Syncing
        val deleting = deleteState is FavoritePageModel.DeleteState.Deleting
        val syncAddedMessage = stringResource(R.string.bookshelf_sync_added)
        val syncDoneMessage = stringResource(R.string.bookshelf_sync_done)
        val networkErrorMessage = stringResource(R.string.load_network_error)
        val syncFailedMessage = stringResource(R.string.bookshelf_sync_failed)
        val deleteDoneMessage = stringResource(R.string.bookshelf_delete_done)
        val deleteFailedMessage = stringResource(R.string.bookshelf_delete_failed)

        fun requestDelete() {
            pendingDelete = shown.filter { it.bookKey in selected }
            showDeleteDialog = pendingDelete.isNotEmpty()
        }

        fun exitEdit() {
            if (deleting) return
            editing = false
            selected = emptySet()
            pendingDelete = emptyList()
            showDeleteDialog = false
        }

        LaunchedEffect(Unit) {
            model.scheduleMetadataSupplement()
            model.refreshDownloaded()
        }
        LaunchedEffect(entries) { model.refreshDownloaded() }
        LaunchedEffect(visibleKeys) { selected = selected.intersect(visibleKeys) }
        LaunchedEffect(syncState) {
            when (val state = syncState) {
                is FavoritePageModel.State.Completed -> snackbar.showSnackbar(
                    if (state.result.added > 0) syncAddedMessage.format(state.result.added)
                    else syncDoneMessage
                )
                is FavoritePageModel.State.Failed -> snackbar.showSnackbar(
                    when (state.failure) {
                        LoadFailureKind.NETWORK -> networkErrorMessage
                        else -> syncFailedMessage
                    }
                )
                else -> Unit
            }
        }
        LaunchedEffect(deleteState) {
            when (val state = deleteState) {
                is FavoritePageModel.DeleteState.Completed -> {
                    editing = false
                    selected = emptySet()
                    pendingDelete = emptyList()
                    showDeleteDialog = false
                    snackbar.showSnackbar(deleteDoneMessage.format(state.count))
                }
                FavoritePageModel.DeleteState.Failed -> snackbar.showSnackbar(deleteFailedMessage)
                else -> Unit
            }
        }
        BackHandler(enabled = editing && !showDeleteDialog) { exitEdit() }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                BookshelfTopBar(
                    showBack = showBack,
                    editing = editing,
                    selectedCount = selected.size,
                    totalCount = shown.size,
                    syncing = syncing,
                    deleting = deleting,
                    onBack = { if (editing) exitEdit() else navigator?.pop() },
                    onRefresh = { if (!syncing) model.sync() },
                    onEdit = { editing = true; selected = emptySet() },
                    onDone = ::exitEdit,
                    onSelectAll = { selected = if (selected == visibleKeys) emptySet() else visibleKeys },
                    onDelete = ::requestDelete
                )
            },
            bottomBar = {
                if (editing) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(
                            Modifier.fillMaxWidth().padding(AppSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.bookshelf_selected, selected.size),
                                style = AppTypography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = ::exitEdit, enabled = !deleting) {
                                Text(stringResource(android.R.string.cancel))
                            }
                            Button(
                                onClick = ::requestDelete,
                                enabled = selected.isNotEmpty() && !deleting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                if (deleting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Filled.Delete, null)
                                Text(stringResource(R.string.bookshelf_delete_selected), modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize().padding(padding).topPullToSync(gridState, !syncing && !editing) { model.sync() },
                contentPadding = PaddingValues(AppSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(stringResource(R.string.bookshelf_collection), style = AppTypography.displayMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.bookshelf_count, visible.size),
                                style = AppTypography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = downloadedOnly,
                                onClick = { downloadedOnly = !downloadedOnly },
                                label = { Text(stringResource(R.string.bookshelf_filter_downloaded)) }
                            )
                        }
                        if (syncing) LoadingSkeleton(label = stringResource(R.string.bookshelf_sync_running_short))
                        if (syncState is FavoritePageModel.State.Failed && shown.isNotEmpty()) {
                            Text(
                                stringResource(R.string.bookshelf_sync_failed),
                                style = AppTypography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (syncState is FavoritePageModel.State.Failed && entries.isEmpty()) {
                            OfflineState(
                                title = stringResource(R.string.bookshelf_sync_failed),
                                message = stringResource(R.string.bookshelf_sync_failed),
                                onRetry = { if (!syncing) model.sync() },
                                modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxxl)
                            )
                        } else EmptyState(
                            title = stringResource(
                                when {
                                    downloadedOnly -> R.string.bookshelf_empty_filtered
                                    visible.isEmpty() && entries.isNotEmpty() -> R.string.bookshelf_empty_filtered
                                    else -> R.string.bookshelf_empty
                                }
                            ),
                            message = stringResource(
                                when {
                                    downloadedOnly -> R.string.download_empty_hint
                                    visible.isEmpty() && entries.isNotEmpty() -> R.string.bookshelf_empty_filtered_hint
                                    else -> R.string.bookshelf_empty_hint
                                }
                            ),
                            actionLabel = stringResource(R.string.sync_bookshelf),
                            onAction = { if (!syncing) model.sync() },
                            modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxxl)
                        )
                    }
                } else {
                    items(shown, key = { it.bookKey }, contentType = { "bookshelf" }) { entry ->
                        ShelfCard(
                            entry = entry,
                            selected = entry.bookKey in selected,
                            editing = editing,
                            enabled = !deleting,
                            onClick = {
                                if (editing) {
                                    selected = if (entry.bookKey in selected) selected - entry.bookKey else selected + entry.bookKey
                                } else {
                                    navigator?.pushIfNotCurrent(
                                        NovelPage(entry.asCoveredNovel(), favorite = BooleanStateHolder(true))
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { if (!deleting) showDeleteDialog = false },
                title = { Text(stringResource(R.string.bookshelf_delete_title, pendingDelete.size)) },
                text = {
                    Text(
                        stringResource(R.string.bookshelf_delete_confirm, pendingDelete.size) +
                            "\n\n" + stringResource(R.string.bookshelf_delete_sync_notice)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { model.delete(pendingDelete) }, enabled = !deleting) {
                        Text(stringResource(R.string.bookshelf_delete_confirm_action, pendingDelete.size), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }, enabled = !deleting) { Text(stringResource(android.R.string.cancel)) } }
            )
        }
    }
}

@Composable
private fun BookshelfTopBar(
    showBack: Boolean,
    editing: Boolean,
    selectedCount: Int,
    totalCount: Int,
    syncing: Boolean,
    deleting: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reader_back)) }
            Column(Modifier.weight(1f).padding(horizontal = AppSpacing.sm)) {
                Text(
                    if (editing) stringResource(R.string.bookshelf_selected_header, selectedCount)
                    else stringResource(R.string.bookshelf),
                    style = AppTypography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (editing) Text(
                    stringResource(R.string.bookshelf_total_header, totalCount),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (editing) {
                IconButton(onClick = onSelectAll, enabled = totalCount > 0 && !deleting) {
                    Icon(Icons.Filled.Check, stringResource(R.string.bookshelf_select_all))
                }
                IconButton(onClick = onDelete, enabled = selectedCount > 0 && !deleting) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.bookshelf_delete_selected), tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onDone, enabled = !deleting) { Icon(Icons.Filled.Done, stringResource(R.string.bookshelf_edit_done)) }
            } else {
                IconButton(onClick = onRefresh, enabled = !syncing) {
                    if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, stringResource(R.string.sync_bookshelf))
                }
                IconButton(onClick = onEdit, enabled = totalCount > 0) { Icon(Icons.Filled.Edit, stringResource(R.string.bookshelf_edit)) }
            }
        }
    }
}

@Composable
private fun ShelfCard(
    entry: BookshelfEntry,
    selected: Boolean,
    editing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = AppShapes.standard
    Column(
        Modifier.fillMaxWidth().clip(shape).then(
            if (selected && editing) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
        ).clickable(enabled = enabled, onClick = onClick).padding(if (selected && editing) AppSpacing.xs else AppSpacing.zero),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
            AppImage(
                model = entry.coverUrl.takeIf { it.isNotBlank() } ?: R.drawable.missing_cover,
                contentDescription = entry.title,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
            if (editing) {
                Surface(
                    modifier = Modifier.padding(AppSpacing.sm),
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ) {
                    Icon(
                        if (selected) Icons.Filled.Check else Icons.Filled.Bookmark,
                        contentDescription = if (selected) stringResource(R.string.bookshelf_edit_done) else stringResource(R.string.bookshelf_edit),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(AppSpacing.sm).size(18.dp)
                    )
                }
            }
        }
        Text(
            entry.title.ifBlank { stringResource(R.string.download_unknown_novel) },
            style = AppTypography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.sm, start = AppSpacing.xs, end = AppSpacing.xs)
        )
    }
}

private fun BookshelfEntry.asCoveredNovel() = CoveredNovelImpl(
    coverUrl = coverUrl,
    name = title,
    url = url,
    views = 0,
    likes = 0,
    isAdult = isAdult
)

private fun Modifier.topPullToSync(
    gridState: LazyGridState,
    enabled: Boolean,
    onRefresh: () -> Unit
): Modifier = pointerInput(enabled) {
    awaitPointerEventScope {
        var distance = 0f
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: continue
            if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) distance = 0f
            if (!change.pressed) {
                if (enabled && gridState.firstVisibleItemIndex == 0 && distance > 72f) onRefresh()
                distance = 0f
            } else if (enabled && gridState.firstVisibleItemIndex == 0) {
                distance += change.positionChange().y.coerceAtLeast(0f)
            }
        }
    }
}
