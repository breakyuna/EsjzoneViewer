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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.ui.component.QuietNovelCover
import com.breakyuna.esjzone.ui.navigation.BooleanStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.theme.QuietEditorial

/**
 * Local-first bookshelf presentation.
 *
 * The Room flow exposed by [BookshelfRepository] is the only collection source
 * rendered here. Synchronization and deletion remain in [FavoritePageModel]
 * and never run from Compose recomposition.
 */
object FavoritePage : Screen {
    private fun readResolve(): Any = FavoritePage

    @Composable
    override fun Content() = Content(showBack = true)

    @Composable
    fun Content(showBack: Boolean) {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel { FavoritePageModel(authorization) }
        val entries by model.entries.collectAsState(initial = emptyList())
        val syncState by model.state.collectAsState()
        val deleteState by model.deleteState.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        val gridState = rememberLazyGridState()
        val adult by remember { GlobalSettings.adult }
        val syncAddedText = stringResource(R.string.bookshelf_sync_added)
        val syncDoneText = stringResource(R.string.bookshelf_sync_done)
        val syncFailedText = stringResource(R.string.bookshelf_sync_failed)
        val networkFailedText = stringResource(R.string.load_network_error)
        val clientFailedText = stringResource(R.string.load_client_error)
        val deleteDoneText = stringResource(R.string.bookshelf_delete_done)
        val deleteFailedText = stringResource(R.string.bookshelf_delete_failed)

        var refreshing by rememberSaveable { mutableStateOf(false) }
        var editing by rememberSaveable { mutableStateOf(false) }
        var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingDelete by remember { mutableStateOf<List<BookshelfEntry>>(emptyList()) }
        var showDeleteDialog by remember { mutableStateOf(false) }

        val visibleEntries = remember(entries, adult) {
            entries.filter { adult || !it.isAdultHint() }
        }
        val visibleKeys = remember(visibleEntries) {
            visibleEntries.mapTo(LinkedHashSet<String>()) { it.bookKey }
        }
        val selectedCount = selectedKeys.size
        val deleting = deleteState is FavoritePageModel.DeleteState.Deleting
        val syncing = refreshing || syncState is FavoritePageModel.State.Syncing
        val adultFiltered = entries.isNotEmpty() && visibleEntries.isEmpty()

        fun refresh() {
            if (!syncing) {
                refreshing = true
                model.sync()
            }
        }

        fun leaveEditMode() {
            if (deleting) return
            editing = false
            selectedKeys = emptySet()
            pendingDelete = emptyList()
            showDeleteDialog = false
        }

        // A cloud import or adult-content preference change can alter the
        // visible set. Never keep an invisible item selected.
        LaunchedEffect(visibleKeys) {
            selectedKeys = selectedKeys.intersect(visibleKeys)
        }

        LaunchedEffect(Unit) {
            model.scheduleMetadataSupplement()
        }

        LaunchedEffect(syncState) {
            when (val state = syncState) {
                is FavoritePageModel.State.Completed -> {
                    refreshing = false
                    snackbar.showSnackbar(
                        if (state.result.added > 0) {
                            syncAddedText.format(state.result.added)
                        } else {
                            syncDoneText
                        }
                    )
                }

                is FavoritePageModel.State.Failed -> {
                    refreshing = false
                    snackbar.showSnackbar(
                        when (state.failure) {
                            LoadFailureKind.NETWORK -> networkFailedText
                            LoadFailureKind.CLIENT -> clientFailedText
                            null -> syncFailedText
                        }
                    )
                }

                else -> Unit
            }
        }

        LaunchedEffect(deleteState) {
            when (val state = deleteState) {
                is FavoritePageModel.DeleteState.Completed -> {
                    editing = false
                    selectedKeys = emptySet()
                    pendingDelete = emptyList()
                    showDeleteDialog = false
                    snackbar.showSnackbar(
                        deleteDoneText.format(state.count)
                    )
                }

                FavoritePageModel.DeleteState.Failed -> {
                    showDeleteDialog = false
                    pendingDelete = emptyList()
                    snackbar.showSnackbar(deleteFailedText)
                }

                else -> Unit
            }
        }

        BackHandler(enabled = editing && !showDeleteDialog) {
            leaveEditMode()
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // MainScreen owns system-bar insets for tab content. Keeping the
            // nested page inset-free prevents the outer navigation bar from
            // being counted twice.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                BookshelfHeader(
                    showBack = showBack,
                    editing = editing,
                    deleting = deleting,
                    selectedCount = selectedCount,
                    totalCount = visibleEntries.size,
                    allSelected = visibleKeys.isNotEmpty() && selectedKeys == visibleKeys,
                    syncing = syncing,
                    onBack = {
                        if (editing) leaveEditMode() else navigator?.pop()
                    },
                    onRefresh = ::refresh,
                    onEdit = {
                        if (!deleting) {
                            editing = true
                            selectedKeys = emptySet()
                        }
                    },
                    onSelectAll = {
                        if (!deleting) {
                            selectedKeys = if (selectedKeys == visibleKeys) {
                                emptySet()
                            } else {
                                visibleKeys
                            }
                        }
                    },
                    onDelete = {
                        if (!deleting && selectedKeys.isNotEmpty()) {
                            // Capture the exact visible rows now. A later sync
                            // cannot change what this confirmation removes.
                            pendingDelete = visibleEntries.filter {
                                it.bookKey in selectedKeys
                            }
                            showDeleteDialog = pendingDelete.isNotEmpty()
                        }
                    },
                    onDone = ::leaveEditMode
                )
            },
            bottomBar = {
                if (editing) {
                    BookshelfBatchBar(
                        selectedCount = selectedCount,
                        deleting = deleting,
                        onCancel = ::leaveEditMode,
                        onDelete = {
                            if (!deleting && selectedKeys.isNotEmpty()) {
                                pendingDelete = visibleEntries.filter {
                                    it.bookKey in selectedKeys
                                }
                                showDeleteDialog = pendingDelete.isNotEmpty()
                            }
                        }
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbar) }
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .bookshelfPullToRefresh(
                        gridState = gridState,
                        enabled = !syncing && !editing,
                        onRefresh = ::refresh
                    ),
                contentPadding = PaddingValues(
                    start = QuietEditorial.pagePadding,
                    end = QuietEditorial.pagePadding,
                    top = 12.dp,
                    // Scaffold padding already accounts for the optional
                    // batch bar and the root tab navigation. Keep only a
                    // small visual breathing space here.
                    bottom = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (editing) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BookshelfSelectionHint(deleting = deleting)
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BookshelfCollectionSummary()
                    }
                }

                if (visibleEntries.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BookshelfEmptyState(adultFiltered = adultFiltered)
                    }
                } else {
                    items(
                        items = visibleEntries,
                        key = { entry -> entry.bookKey }
                    ) { entry ->
                        BookshelfGridItem(
                            entry = entry,
                            editing = editing,
                            selected = entry.bookKey in selectedKeys,
                            enabled = !deleting,
                            onClick = {
                                if (editing) {
                                    selectedKeys = if (entry.bookKey in selectedKeys) {
                                        selectedKeys - entry.bookKey
                                    } else {
                                        selectedKeys + entry.bookKey
                                    }
                                } else {
                                    navigator?.pushIfNotCurrent(
                                        NovelPage(
                                            entry.asCoveredNovel(),
                                            favorite = BooleanStateHolder(true)
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showDeleteDialog && pendingDelete.isNotEmpty()) {
            BookshelfRemovalDialog(
                entries = pendingDelete,
                deleting = deleting,
                onDismiss = {
                    if (!deleting) {
                        showDeleteDialog = false
                        pendingDelete = emptyList()
                    }
                },
                onConfirm = {
                    if (!deleting) {
                        // The list remains captured while the model persists
                        // each removal intent and reconciles it remotely.
                        model.delete(pendingDelete)
                    }
                }
            )
        }
    }
}

@Composable
private fun BookshelfHeader(
    showBack: Boolean,
    editing: Boolean,
    deleting: Boolean,
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    syncing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (showBack) 4.dp else QuietEditorial.pagePadding,
                        end = 8.dp,
                        top = 16.dp,
                        bottom = 10.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBack) {
                    IconButton(
                        onClick = onBack,
                        enabled = !deleting,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.reader_back)
                        )
                    }
                }

                if (editing) {
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.bookshelf_selected_header)
                                    .format(selectedCount),
                                style = QuietEditorial.title.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.bookshelf_total_header)
                                    .format(totalCount),
                                style = QuietEditorial.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    TextButton(
                        onClick = onSelectAll,
                        enabled = totalCount > 0 && !deleting,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 48.dp, max = 88.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (allSelected) {
                                    R.string.bookshelf_selection_clear
                                } else {
                                    R.string.bookshelf_select_all
                                }
                            ),
                            style = QuietEditorial.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = selectedCount > 0 && !deleting,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.bookshelf_delete_selected),
                            tint = if (selectedCount > 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = onDone,
                        enabled = !deleting,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = stringResource(R.string.bookshelf_edit_done)
                        )
                    }
                } else {
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.bookshelf),
                                style = QuietEditorial.display,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.bookshelf_count)
                                    .format(totalCount),
                                style = QuietEditorial.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    ShelfSyncButton(syncing = syncing, onClick = onRefresh)
                    IconButton(
                        onClick = onEdit,
                        enabled = !deleting,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.bookshelf_edit)
                        )
                    }
                }
            }
            HorizontalDivider(
                thickness = QuietEditorial.hairline,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ShelfSyncButton(syncing: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = !syncing, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = stringResource(
                    if (syncing) {
                        R.string.bookshelf_sync_running_short
                    } else {
                        R.string.sync_bookshelf
                    }
                ),
                style = QuietEditorial.smallLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BookshelfCollectionSummary() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.bookshelf_collection),
                style = QuietEditorial.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.bookshelf_order_automatic),
                style = QuietEditorial.smallLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BookshelfSelectionHint(deleting: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = QuietEditorial.controlShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(
                    if (deleting) {
                        R.string.bookshelf_removing_selection
                    } else {
                        R.string.bookshelf_selection_hint
                    }
                ),
                style = QuietEditorial.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BookshelfGridItem(
    entry: BookshelfEntry,
    editing: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val itemShape = QuietEditorial.bookshelfCardShape
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected && editing) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, itemShape)
                } else {
                    Modifier
                }
            )
            .clip(itemShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(if (selected && editing) 4.dp else 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            QuietNovelCover(
                coverUrl = entry.coverUrl,
                title = entry.title,
                isAdult = entry.isAdultHint(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.73f)
            )
            if (editing) {
                BookshelfSelectionOverlay(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                )
            }
        }
        Text(
            text = entry.title,
            style = QuietEditorial.body.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp)
        )
    }
}

@Composable
private fun BookshelfSelectionOverlay(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                },
                shape = QuietEditorial.selectionShape
            )
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                shape = QuietEditorial.selectionShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (selected) {
                Icons.Filled.Check
            } else {
                Icons.Filled.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun BookshelfEmptyState(adultFiltered: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Text(
            text = stringResource(
                if (adultFiltered) {
                    R.string.bookshelf_empty_filtered
                } else {
                    R.string.bookshelf_empty
                }
            ),
            style = QuietEditorial.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            text = stringResource(
                if (adultFiltered) {
                    R.string.bookshelf_empty_filtered_hint
                } else {
                    R.string.bookshelf_empty_hint
                }
            ),
            style = QuietEditorial.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun BookshelfBatchBar(
    selectedCount: Int,
    deleting: Boolean,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = QuietEditorial.pagePadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 1.dp).size(18.dp)
                )
                Text(
                    text = stringResource(R.string.bookshelf_remove_consequence),
                    style = QuietEditorial.smallLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !deleting,
                    modifier = Modifier.weight(0.38f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(stringResource(android.R.string.cancel), style = QuietEditorial.label)
                }
                Button(
                    onClick = onDelete,
                    enabled = selectedCount > 0 && !deleting,
                    modifier = Modifier.weight(0.62f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    if (deleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.size(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(
                        text = stringResource(
                            if (deleting) {
                                R.string.bookshelf_removing
                            } else {
                                R.string.bookshelf_remove_selected_count
                            }
                        ).format(selectedCount),
                        style = QuietEditorial.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BookshelfRemovalDialog(
    entries: List<BookshelfEntry>,
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !deleting,
            dismissOnClickOutside = !deleting,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 16.dp),
            shape = QuietEditorial.dialogShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(56.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.bookshelf_delete_title).format(entries.size),
                    style = QuietEditorial.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    shape = QuietEditorial.controlShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        entries.take(4).forEach { entry ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = entry.title,
                                    style = QuietEditorial.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (entries.size > 4) {
                            Text(
                                text = stringResource(R.string.bookshelf_more_selected)
                                    .format(entries.size - 4),
                                style = QuietEditorial.smallLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                RemovalConsequence(
                    text = stringResource(R.string.bookshelf_delete_local_notice),
                    modifier = Modifier.padding(top = 16.dp)
                )
                RemovalConsequence(
                    text = stringResource(R.string.bookshelf_delete_data_notice),
                    modifier = Modifier.padding(top = 7.dp)
                )
                RemovalConsequence(
                    text = stringResource(R.string.bookshelf_delete_sync_notice),
                    modifier = Modifier.padding(top = 7.dp)
                )
                if (deleting) {
                    Text(
                        text = stringResource(R.string.bookshelf_removing_detail),
                        style = QuietEditorial.smallLabel,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !deleting) {
                        Text(stringResource(android.R.string.cancel), style = QuietEditorial.label)
                    }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = !deleting,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = stringResource(
                                if (deleting) {
                                    R.string.bookshelf_removing
                                } else {
                                    R.string.bookshelf_delete_confirm_action
                                }
                            ).format(entries.size),
                            style = QuietEditorial.label
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemovalConsequence(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            style = QuietEditorial.body,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = text,
            style = QuietEditorial.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun BookshelfEntry.asCoveredNovel() = CoveredNovelImpl(
    coverUrl = coverUrl,
    name = title,
    url = url,
    views = 0,
    likes = 0,
    isAdult = isAdultHint()
)

/** Remote favorite rows do not expose adult metadata; only explicit local hints are hidden. */
private fun BookshelfEntry.isAdultHint(): Boolean = isAdult

/**
 * Pull-to-sync is deliberately limited to the absolute top. It observes the
 * grid without changing scroll consumption, so normal fling performance stays
 * in LazyVerticalGrid and no duplicate sync request can be started.
 */
private fun Modifier.bookshelfPullToRefresh(
    gridState: LazyGridState,
    enabled: Boolean,
    onRefresh: () -> Unit
): Modifier = pointerInput(enabled) {
    awaitPointerEventScope {
        var distance = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
                distance = 0f
            }
            if (!change.pressed) {
                if (
                    enabled &&
                    gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0 &&
                    distance > 72f
                ) {
                    onRefresh()
                }
                distance = 0f
            } else if (enabled && gridState.firstVisibleItemIndex == 0) {
                val delta = change.positionChange().y
                if (delta > 0f) distance += delta
            }
        }
    }
}
