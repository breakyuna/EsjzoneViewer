@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.page

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.navigation.LocalFloatingNavPadding
import com.breakyuna.esjzone.ui.navigation.LocalFloatingNavSuppression
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.BookshelfCoverStore
import com.breakyuna.esjzone.database.BookshelfSort
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.component.AppBookshelfRecentReads
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppSyncStatusDot
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.BooleanStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.product.EmptyState
import com.breakyuna.esjzone.ui.product.OfflineState
import kotlin.math.roundToInt

private enum class BookshelfFilter {
    ALL,
    DOWNLOADED,
    UPDATES
}

private fun BookshelfSort.Order.labelRes(): Int = when (this) {
    BookshelfSort.Order.RECENT_READ -> R.string.bookshelf_sort_recent_read
    BookshelfSort.Order.RECENT_ADDED -> R.string.bookshelf_sort_recent_added
    BookshelfSort.Order.RECENT_UPDATED -> R.string.bookshelf_sort_recent_updated
}

/** Local-first bookshelf. Room is the only rendered source; sync is additive. */
object FavoritePage : AppDestination {
    private fun readResolve(): Any = FavoritePage

    override val key: String = "FavoritePage"

    @Composable
    override fun Content() = Content(showBack = true)

    @Composable
    fun Content(showBack: Boolean) {
        val navigator = LocalBaseNavigator.current
        val focusManager = LocalFocusManager.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { FavoritePageModel(authorization) }
        val entries by model.entries.collectAsStateWithLifecycle()
        LaunchedEffect(entries) {
            BookshelfCoverStore.schedulePersist(entries)
        }
        val readingIndex by model.readingIndex.collectAsStateWithLifecycle()
        val downloaded by model.downloadedBookKeys.collectAsStateWithLifecycle()
        val syncState by model.state.collectAsStateWithLifecycle()
        val deleteState by model.deleteState.collectAsStateWithLifecycle()
        val legacyBookshelfCount by model.legacyBookshelfCount.collectAsStateWithLifecycle()
        val legacyRecoveryFailed by model.legacyRecoveryFailed.collectAsStateWithLifecycle()
        val adult by PresentationAccess.settings.adult
        val snackbar = remember { SnackbarHostState() }
        val listState = rememberLazyListState()
        var editing by rememberSaveable { mutableStateOf(false) }
        var activeFilter by rememberSaveable { mutableStateOf(BookshelfFilter.ALL) }
        var activeSort by rememberSaveable { mutableStateOf(BookshelfSort.Order.RECENT_READ) }
        var listView by rememberSaveable { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingDelete by remember { mutableStateOf<List<BookshelfEntry>>(emptyList()) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var showLegacyRecoveryDialog by remember { mutableStateOf(false) }
        var showSyncStatusMenu by remember { mutableStateOf(false) }
        var showSortMenu by remember { mutableStateOf(false) }
        var lastHandledSyncEventId by rememberSaveable { mutableStateOf(0L) }
        var lastHandledDeleteEventId by rememberSaveable { mutableStateOf(0L) }
        val suppressFloatingNav = LocalFloatingNavSuppression.current
        DisposableEffect(editing, showDeleteDialog, suppressFloatingNav) {
            suppressFloatingNav(editing || showDeleteDialog)
            onDispose { suppressFloatingNav(false) }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    snackbar.currentSnackbarData?.dismiss()
                    showSyncStatusMenu = false
                    showSortMenu = false
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                snackbar.currentSnackbarData?.dismiss()
                showSyncStatusMenu = false
                showSortMenu = false
            }
        }

        val visible = remember(entries, adult) { entries.filter { adult || !it.isAdult } }
        // The repository stream is intentionally kept in recent-read order so
        // the showcase remains stable when the list order is changed below.
        val recentReads = remember(visible, readingIndex) {
            visible.asSequence().filter { it in readingIndex }.take(4).toList()
        }
        val sortedVisible = remember(visible, readingIndex, activeSort) {
            if (activeSort == BookshelfSort.Order.RECENT_READ) {
                // BookshelfRepository.observe already delivers entries in RECENT_READ order
                visible
            } else {
                BookshelfSort.sortWithReadProvider(
                    entries = visible,
                    order = activeSort,
                    readAtProvider = { entry -> readingIndex.activityFor(entry)?.lastReadAt }
                )
            }
        }
        val shown = remember(sortedVisible, downloaded, activeFilter) {
            when (activeFilter) {
                BookshelfFilter.ALL -> sortedVisible
                BookshelfFilter.DOWNLOADED -> sortedVisible.filter { it.bookKey in downloaded }
                BookshelfFilter.UPDATES -> sortedVisible.filter { it.hasUpdate }
            }
        }
        val visibleKeys = remember(shown) { shown.mapTo(LinkedHashSet()) { it.bookKey } }
        val syncing = syncState is FavoritePageModel.State.Syncing
        val deleting = deleteState is FavoritePageModel.DeleteState.Deleting
        val isSyncSuccess = !syncing && syncState is FavoritePageModel.State.Completed
        val isSyncFailed = syncState is FavoritePageModel.State.Failed
        val syncAddedMessage = stringResource(R.string.bookshelf_sync_added)
        val syncDoneMessage = stringResource(R.string.bookshelf_sync_done)
        val networkErrorMessage = stringResource(R.string.load_network_error)
        val syncFailedMessage = stringResource(R.string.bookshelf_sync_failed)
        val deleteDoneMessage = stringResource(R.string.bookshelf_delete_done)
        val deleteFailedMessage = stringResource(R.string.bookshelf_delete_failed)

        suspend fun showTransientSnackbar(message: String, durationMillis: Long = 2000L) {
            snackbar.currentSnackbarData?.dismiss()
            coroutineScope {
                val showJob = launch {
                    snackbar.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Indefinite
                    )
                }
                val timerJob = launch {
                    delay(durationMillis)
                    snackbar.currentSnackbarData?.dismiss()
                }
                showJob.join()
                timerJob.cancel()
            }
        }

        fun openBook(entry: BookshelfEntry) {
            // Release any focus-pinned lazy item before beginning a page transition.
            focusManager.clearFocus(force = true)
            snackbar.currentSnackbarData?.dismiss()
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
            model.initShelf()
        }
        LaunchedEffect(visibleKeys) { selected = selected.intersect(visibleKeys) }
        LaunchedEffect(syncState) {
            when (val state = syncState) {
                is FavoritePageModel.State.Completed -> {
                    if (state.eventId != 0L && state.eventId != lastHandledSyncEventId) {
                        lastHandledSyncEventId = state.eventId
                        showTransientSnackbar(
                            if (state.result.added > 0) syncAddedMessage.format(state.result.added)
                            else syncDoneMessage
                        )
                    }
                }
                is FavoritePageModel.State.Failed -> {
                    if (state.eventId != 0L && state.eventId != lastHandledSyncEventId) {
                        lastHandledSyncEventId = state.eventId
                        showTransientSnackbar(
                            when (state.failure) {
                                LoadFailureKind.NETWORK -> networkErrorMessage
                                else -> syncFailedMessage
                            }
                        )
                    }
                }
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
                    if (state.eventId != 0L && state.eventId != lastHandledDeleteEventId) {
                        lastHandledDeleteEventId = state.eventId
                        showTransientSnackbar(deleteDoneMessage.format(state.count))
                    }
                }
                is FavoritePageModel.DeleteState.Failed -> {
                    if (state.eventId != 0L && state.eventId != lastHandledDeleteEventId) {
                        lastHandledDeleteEventId = state.eventId
                        showTransientSnackbar(deleteFailedMessage)
                    }
                }
                else -> Unit
            }
        }
        BackHandler(enabled = editing && !showDeleteDialog) { exitEdit() }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // The floating navigation is rendered by the shell rather than Scaffold.
            // Reserve its height here so transient feedback is never obscured by it.
            snackbarHost = {
                SnackbarHost(
                    snackbar,
                    modifier = Modifier.padding(
                        bottom = LocalFloatingNavPadding.current.calculateBottomPadding()
                    )
                )
            },
            topBar = {
                BookshelfTopBar(
                    showBack = showBack,
                    editing = editing,
                    selectedCount = selected.size,
                    totalCount = shown.size,
                    syncing = syncing,
                    isSyncSuccess = isSyncSuccess,
                    isSyncFailed = isSyncFailed,
                    showSyncStatusMenu = showSyncStatusMenu,
                    deleting = deleting,
                    onBack = {
                        snackbar.currentSnackbarData?.dismiss()
                        if (editing) exitEdit() else navigator?.pop()
                    },
                    onSyncStatusMenuChange = { showSyncStatusMenu = it },
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
            PullToRefreshBox(
                isRefreshing = syncing,
                onRefresh = { if (!syncing && !editing) model.sync() },
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
            ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val startPadding = AppSpacing.lg + navPadding.calculateStartPadding(layoutDirection)
                val endPadding = AppSpacing.lg
                val columns = if (listView && !editing) 1 else bookshelfColumnCount(
                    (maxWidth - startPadding - endPadding).value,
                    AppSpacing.xl.value
                )
                val rows = remember(shown, columns) { shown.chunked(columns) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = 0.dp,
                            bottom = if (editing) padding.calculateBottomPadding() else 0.dp
                        ),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                                ) {
                                    Text(
                                        stringResource(R.string.bookshelf_count, visible.size),
                                        style = AppTypography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilterChip(
                                    selected = activeFilter == BookshelfFilter.DOWNLOADED,
                                    onClick = {
                                        activeFilter = if (activeFilter == BookshelfFilter.DOWNLOADED) {
                                            BookshelfFilter.ALL
                                        } else {
                                            BookshelfFilter.DOWNLOADED
                                        }
                                    },
                                    label = { Text(stringResource(R.string.bookshelf_filter_downloaded)) }
                                )
                                FilterChip(
                                    selected = activeFilter == BookshelfFilter.UPDATES,
                                    onClick = {
                                        activeFilter = if (activeFilter == BookshelfFilter.UPDATES) {
                                            BookshelfFilter.ALL
                                        } else {
                                            BookshelfFilter.UPDATES
                                        }
                                    },
                                    label = { Text(stringResource(R.string.bookshelf_filter_updates)) },
                                    modifier = Modifier.padding(start = AppSpacing.sm)
                                )
                                Box {
                                    IconButton(
                                        onClick = { showSortMenu = true },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Sort,
                                            contentDescription = stringResource(R.string.bookshelf_sort)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        BookshelfSort.Order.values().forEach { order ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(order.labelRes())) },
                                                onClick = {
                                                    activeSort = order
                                                    showSortMenu = false
                                                },
                                                leadingIcon = {
                                                    if (activeSort == order) Icon(Icons.Filled.Check, null)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (!editing && legacyBookshelfCount > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = AppShapes.standard
                                ) {
                                    Column(Modifier.padding(AppSpacing.md)) {
                                        Text(
                                            stringResource(R.string.bookshelf_legacy_recovery_message, legacyBookshelfCount),
                                            style = AppTypography.bodyMedium
                                        )
                                        if (legacyRecoveryFailed) {
                                            Text(
                                                stringResource(R.string.bookshelf_legacy_recovery_failed),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        TextButton(onClick = { showLegacyRecoveryDialog = true }) {
                                            Text(stringResource(R.string.bookshelf_legacy_recovery_action))
                                        }
                                    }
                                }
                            }
                            if (!editing && recentReads.isNotEmpty()) {
                                AppBookshelfRecentReads(
                                    books = recentReads,
                                    modifier = Modifier.padding(bottom = AppSpacing.md),
                                    onBookClick = { entry ->
                                        openBook(entry)
                                    }
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
                                        activeFilter != BookshelfFilter.ALL -> R.string.bookshelf_empty_filtered
                                        visible.isEmpty() && entries.isNotEmpty() -> R.string.bookshelf_empty_filtered
                                        else -> R.string.bookshelf_empty
                                    }
                                ),
                                message = stringResource(
                                    when {
                                        activeFilter == BookshelfFilter.DOWNLOADED -> R.string.download_empty_hint
                                        activeFilter == BookshelfFilter.UPDATES -> R.string.bookshelf_empty_filtered_hint
                                        visible.isEmpty() && entries.isNotEmpty() -> R.string.bookshelf_empty_filtered_hint
                                        else -> R.string.bookshelf_empty_hint
                                    }
                                ),
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
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xl)
                            ) {
                                row.forEach { entry ->
                                    key(entry.bookKey) {
                                        Box(Modifier.weight(1f)) {
                                            if (listView && !editing) ShelfListItem(
                                                entry = entry, enabled = !deleting,
                                                onClick = { openBook(entry) }
                                            ) else ShelfCard(
                                                entry = entry,
                                                readingActivity = readingIndex.activityFor(entry),
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
        }

        if (showLegacyRecoveryDialog) {
            AlertDialog(
                onDismissRequest = { showLegacyRecoveryDialog = false },
                title = { Text(stringResource(R.string.bookshelf_legacy_recovery_title)) },
                text = { Text(stringResource(R.string.bookshelf_legacy_recovery_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showLegacyRecoveryDialog = false
                        model.claimLegacyBookshelf()
                    }) { Text(stringResource(R.string.bookshelf_legacy_recovery_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { showLegacyRecoveryDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
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
    isSyncSuccess: Boolean,
    isSyncFailed: Boolean,
    showSyncStatusMenu: Boolean,
    listView: Boolean,
    onToggleView: () -> Unit,
    deleting: Boolean,
    onBack: () -> Unit,
    onSyncStatusMenuChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reader_back)) }
            Column(Modifier.weight(1f).padding(horizontal = AppSpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Text(
                        if (editing) stringResource(R.string.bookshelf_selected_header, selectedCount)
                        else stringResource(R.string.bookshelf),
                        style = AppTypography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!editing) {
                        BookshelfSyncStatusIndicator(
                            syncing = syncing,
                            isSyncSuccess = isSyncSuccess,
                            isSyncFailed = isSyncFailed,
                            expanded = showSyncStatusMenu,
                            onExpandedChange = onSyncStatusMenuChange
                        )
                    }
                }
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
                IconButton(onClick = onToggleView) {
                    Icon(if (listView) Icons.Filled.GridView else Icons.Filled.ViewList, "切换书架展示方式")
                }
                IconButton(onClick = onEdit, enabled = totalCount > 0) { Icon(Icons.Filled.Edit, stringResource(R.string.bookshelf_edit)) }
            }
        }
    }
}

@Composable
private fun BookshelfSyncStatusIndicator(
    syncing: Boolean,
    isSyncSuccess: Boolean,
    isSyncFailed: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val statusLabelRes = when {
        syncing -> R.string.bookshelf_sync_running_short
        isSyncFailed -> R.string.bookshelf_sync_status_failed
        isSyncSuccess -> R.string.bookshelf_sync_status_success
        else -> R.string.bookshelf_sync_status_idle
    }
    val statusDetailRes = when {
        syncing -> R.string.bookshelf_sync_running_short
        isSyncFailed -> R.string.bookshelf_sync_failed
        isSyncSuccess -> R.string.bookshelf_sync_done
        else -> R.string.bookshelf_sync_idle
    }
    Box {
        Box(
            modifier = Modifier
                .size(AppSpacing.xl)
                .clip(CircleShape)
                .clickable(
                    onClickLabel = stringResource(statusLabelRes)
                ) { onExpandedChange(true) },
            contentAlignment = Alignment.Center
        ) {
            AppSyncStatusDot(syncing = syncing, isSuccess = isSyncSuccess)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    AppSyncStatusDot(syncing = syncing, isSuccess = isSyncSuccess)
                    Text(
                        text = stringResource(statusLabelRes),
                        style = AppTypography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = stringResource(statusDetailRes),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ShelfCard(
    entry: BookshelfEntry,
    readingActivity: LocalReadingActivity?,
    selected: Boolean,
    editing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    // Keep bookshelf covers aligned with the compact 8dp corners used by
    // the home discovery tiles.
    val shape = AppShapes.compact
    Column(
        Modifier.fillMaxWidth().clip(shape).then(
            if (selected && editing) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
        ).clickable(enabled = enabled, onClick = onClick).padding(if (selected && editing) AppSpacing.xs else AppSpacing.zero),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f)) {
            AppNovelCover(
                coverUrl = BookshelfCoverStore.localOrRemote(entry),
                title = entry.title,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
            if (entry.hasUpdate) {
                BookshelfUpdateDot(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(AppSpacing.xs)
                )
            }
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
            style = AppTypography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.sm, start = AppSpacing.xs, end = AppSpacing.xs)
        )
        Text(
            text = readingActivity?.let { activity ->
                stringResource(
                    R.string.reader_book_progress_percent,
                    (fullBookProgress(
                        chapterIndex = activity.chapterIndex,
                        totalChapters = activity.totalChapters,
                        chapterProgress = activity.chapterProgress
                    ) * 100).roundToInt()
                )
            } ?: stringResource(R.string.bookshelf_unread),
            style = AppTypography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xxs, start = AppSpacing.xs, end = AppSpacing.xs)
        )
    }
}

@Composable
private fun BookshelfUpdateDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(AppSpacing.sm)
            .background(Color(0xFF22C55E), CircleShape)
    )
}

@Composable
private fun ShelfListItem(entry: BookshelfEntry, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(width = 100.dp, height = 140.dp)) {
            AppNovelCover(
                BookshelfCoverStore.localOrRemote(entry),
                entry.title,
                Modifier.fillMaxSize().clip(AppShapes.compact)
            )
            if (entry.hasUpdate) {
                BookshelfUpdateDot(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(AppSpacing.xs)
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(entry.title, style = AppTypography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            entry.latestChapterTitle.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.bookshelf_entry_latest, it), style = AppTypography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            entry.remoteUpdatedAt.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.bookshelf_entry_updated_at, it), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            entry.remoteLastViewedTitle.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.bookshelf_entry_last_viewed, it), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
