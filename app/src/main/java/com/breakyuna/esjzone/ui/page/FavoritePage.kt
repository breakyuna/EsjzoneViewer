package com.breakyuna.esjzone.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
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
import androidx.compose.runtime.key
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
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.navigation.LocalFloatingNavPadding
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.component.AppBookshelfRecentReads
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.BooleanStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.product.EmptyState
import com.breakyuna.esjzone.ui.product.OfflineState

/** Local-first bookshelf. Room is the only rendered source; sync is additive. */
object FavoritePage : AppDestination {
    private fun readResolve(): Any = FavoritePage

    @Composable
    override fun Content() = Content(showBack = true)

    @Composable
    fun Content(showBack: Boolean) {
        val navigator = LocalBaseNavigator.current
        val focusManager = LocalFocusManager.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { FavoritePageModel(authorization) }
        val entries by model.entries.collectAsState(initial = emptyList())
        val readingIndex by model.readingIndex.collectAsState(initial = FavoritePageModel.ReadingIndex())
        val downloaded by model.downloadedBookKeys.collectAsState()
        val syncState by model.state.collectAsState()
        val deleteState by model.deleteState.collectAsState()
        val adult by PresentationAccess.settings.adult
        val snackbar = remember { SnackbarHostState() }
        val listState = rememberLazyListState()
        var editing by rememberSaveable { mutableStateOf(false) }
        var downloadedOnly by rememberSaveable { mutableStateOf(false) }
        var listView by rememberSaveable { mutableStateOf(false) }
        var updatesOnly by rememberSaveable { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingDelete by remember { mutableStateOf<List<BookshelfEntry>>(emptyList()) }
        var showDeleteDialog by remember { mutableStateOf(false) }

        val visible = remember(entries, adult) { entries.filter { adult || !it.isAdult } }
        val shown = remember(visible, downloaded, downloadedOnly, updatesOnly) {
            visible.filter { (!downloadedOnly || it.bookKey in downloaded) && (!updatesOnly || it.hasUpdate) }
        }
        val visibleKeys = remember(shown) { shown.mapTo(LinkedHashSet()) { it.bookKey } }
        // Filter before taking four so hidden/adult/filtered books do not leave empty slots.
        val recentReads = remember(shown, readingIndex) {
            shown.asSequence().filter { it in readingIndex }.take(4).toList()
        }
        val syncing = syncState is FavoritePageModel.State.Syncing
        val deleting = deleteState is FavoritePageModel.DeleteState.Deleting
        val syncAddedMessage = stringResource(R.string.bookshelf_sync_added)
        val syncDoneMessage = stringResource(R.string.bookshelf_sync_done)
        val networkErrorMessage = stringResource(R.string.load_network_error)
        val syncFailedMessage = stringResource(R.string.bookshelf_sync_failed)
        val deleteDoneMessage = stringResource(R.string.bookshelf_delete_done)
        val deleteFailedMessage = stringResource(R.string.bookshelf_delete_failed)

        fun openBook(entry: BookshelfEntry) {
            // Release any focus-pinned lazy item before beginning a page transition.
            focusManager.clearFocus(force = true)
            navigator?.pushIfNotCurrent(NovelPage(entry.asCoveredNovel(), favorite = BooleanStateHolder(true)))
        }

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
            model.autoCheck()
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
                    listView = listView,
                    onToggleView = { focusManager.clearFocus(force = true); listView = !listView },
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
            val navPadding = LocalFloatingNavPadding.current
            val layoutDirection = LocalLayoutDirection.current
            // Use one lazy axis. Dynamic full-span headers and per-item spans in a
            // LazyGrid can place pinned items twice during navigation/lookahead.
            // Each visible row owns its covers; the 2-1-3-4 showcase stays in the header.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val startPadding = AppSpacing.lg + navPadding.calculateStartPadding(layoutDirection)
                val endPadding = AppSpacing.lg
                val columns = if (listView && !editing) 1 else bookshelfColumnCount(
                    (maxWidth - startPadding - endPadding).value,
                    AppSpacing.md.value
                )
                val rows = remember(shown, columns) { shown.chunked(columns) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = padding.calculateTopPadding(),
                            bottom = if (editing) padding.calculateBottomPadding() else 0.dp
                        )
                        .topPullToSync(listState, !syncing && !editing) { model.sync() },
                    contentPadding = PaddingValues(
                        start = startPadding,
                        end = AppSpacing.lg,
                        top = AppSpacing.lg,
                        bottom = AppSpacing.lg + (if (editing) 0.dp else navPadding.calculateBottomPadding())
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (listView && !editing) AppSpacing.sm else AppSpacing.lg)
                ) {
                    // Keep the showcase inside the first stable item. Inserting a new item above
                    // the header after Room loads would preserve the header anchor and hide it.
                    item(key = "bookshelf_collection_header", contentType = "bookshelf_header") {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            if (!editing && recentReads.isNotEmpty()) {
                                AppBookshelfRecentReads(
                                    books = recentReads,
                                    modifier = Modifier.padding(bottom = AppSpacing.md),
                                    onBookClick = { entry ->
                                        openBook(entry)
                                    }
                                )
                            }
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
                                FilterChip(
                                    selected = updatesOnly,
                                    onClick = { updatesOnly = !updatesOnly },
                                    label = { Text("有更新") },
                                    modifier = Modifier.padding(start = AppSpacing.sm)
                                )
                            }
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
                        item(key = "bookshelf_empty", contentType = "bookshelf_empty") {
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
                        items(
                            rows,
                            key = { "bookshelf_row:${it.first().bookKey}" },
                            contentType = { if (listView && !editing) "bookshelf_list" else "bookshelf_row:$columns" }
                        ) { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                            ) {
                                row.forEach { entry ->
                                    key(entry.bookKey) {
                                        Box(Modifier.weight(1f)) {
                                            if (listView && !editing) ShelfListItem(
                                                entry = entry, enabled = !deleting,
                                                onClick = { openBook(entry) }
                                            ) else ShelfCard(
                                                entry = entry,
                                                selected = entry.bookKey in selected,
                                                editing = editing,
                                                enabled = !deleting,
                                                onClick = {
                                                    if (editing) {
                                                        selected = if (entry.bookKey in selected) selected - entry.bookKey else selected + entry.bookKey
                                                    } else {
                                                        openBook(entry)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
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
    listView: Boolean,
    onToggleView: () -> Unit,
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
                IconButton(onClick = onToggleView) {
                    Icon(if (listView) Icons.Filled.GridView else Icons.Filled.ViewList, "切换书架展示方式")
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
            AppNovelCover(
                coverUrl = entry.coverUrl,
                title = entry.title,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
            if (entry.hasUpdate) Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(AppSpacing.xs),
                shape = CircleShape, color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
            ) { Box(Modifier.size(10.dp)) }
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

@Composable
private fun ShelfListItem(entry: BookshelfEntry, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 100.dp, height = 140.dp)) {
            AppNovelCover(entry.coverUrl, entry.title, Modifier.fillMaxSize().clip(AppShapes.standard))
            if (entry.hasUpdate) Surface(Modifier.align(Alignment.TopEnd).padding(AppSpacing.xs), CircleShape, color = androidx.compose.ui.graphics.Color(0xFF4CAF50)) { Box(Modifier.size(10.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(entry.title, style = AppTypography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            entry.latestChapterTitle.takeIf { it.isNotBlank() }?.let { Text("最新：$it", style = AppTypography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            entry.remoteUpdatedAt.takeIf { it.isNotBlank() }?.let { Text("更新日期：$it", style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            entry.remoteLastViewedTitle.takeIf { it.isNotBlank() }?.let { Text("最后观看：$it", style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
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
    listState: LazyListState,
    enabled: Boolean,
    onRefresh: () -> Unit
): Modifier = pointerInput(enabled) {
    awaitPointerEventScope {
        var distance = 0f
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: continue
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) distance = 0f
            if (!change.pressed) {
                if (enabled && listState.firstVisibleItemIndex == 0 && distance > 72f) onRefresh()
                distance = 0f
            } else if (enabled && listState.firstVisibleItemIndex == 0) {
                distance += change.positionChange().y.coerceAtLeast(0f)
            }
        }
    }
}
