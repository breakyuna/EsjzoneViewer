package com.breakyuna.esjzone.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.breakyuna.esjzone.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.BookshelfSyncResult
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.ui.navigation.BooleanStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent

/** The shelf is rendered entirely from the local Room flow. */
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
        val snackbar = remember { SnackbarHostState() }
        val gridState = rememberLazyGridState()
        var refreshing by rememberSaveable { mutableStateOf(false) }
        val adult by remember { GlobalSettings.adult }
        val syncDoneText = stringResource(R.string.bookshelf_sync_done)
        val syncAddedPattern = stringResource(R.string.bookshelf_sync_added)
        val syncFailedText = stringResource(R.string.bookshelf_sync_failed)
        val deleteConfirmText = stringResource(R.string.bookshelf_delete_confirm)
        val deleteFailedText = stringResource(R.string.bookshelf_delete_failed)
        val deleteDonePattern = stringResource(R.string.bookshelf_delete_done)
        val editTitle = stringResource(R.string.bookshelf_edit)
        val selectedPattern = stringResource(R.string.bookshelf_selected)
        var editing by rememberSaveable { mutableStateOf(false) }
        var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingDelete by remember { mutableStateOf<List<BookshelfEntry>>(emptyList()) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        val deleteState by model.deleteState.collectAsState()
        val deleting = deleteState is FavoritePageModel.DeleteState.Deleting

        fun refresh() {
            if (!refreshing) {
                refreshing = true
                model.sync()
            }
        }

        LaunchedEffect(syncState) {
            when (val result = syncState) {
                is FavoritePageModel.State.Completed -> {
                    refreshing = false
                    snackbar.showSnackbar(
                        if (result.result.added > 0) {
                            syncAddedPattern.format(result.result.added)
                        } else {
                            syncDoneText
                        }
                    )
                }
                FavoritePageModel.State.Failed -> {
                    refreshing = false
                    snackbar.showSnackbar(syncFailedText)
                }
                else -> Unit
            }
        }

        // Enrichment is detached from rendering: Room remains the only UI
        // source and newly discovered covers update the grid through its flow.
        LaunchedEffect(Unit) {
            model.scheduleMetadataSupplement()
        }

        val visibleEntries = remember(entries, adult) {
            entries.filter { adult || !it.isAdultHint() }
        }
        val visibleKeys = remember(visibleEntries) {
            visibleEntries.mapTo(HashSet<String>()) { it.bookKey }
        }
        LaunchedEffect(visibleKeys) {
            selectedKeys = selectedKeys.intersect(visibleKeys)
        }

        fun leaveEditMode() {
            editing = false
            selectedKeys = emptySet()
            pendingDelete = emptyList()
            showDeleteDialog = false
        }

        BackHandler(enabled = editing) {
            leaveEditMode()
        }

        LaunchedEffect(deleteState) {
            when (val result = deleteState) {
                is FavoritePageModel.DeleteState.Completed -> {
                    leaveEditMode()
                    snackbar.showSnackbar(
                        deleteDonePattern.format(result.count)
                    )
                }
                FavoritePageModel.DeleteState.Failed -> snackbar.showSnackbar(deleteFailedText)
                else -> Unit
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                BookshelfTopBar(
                    showBack = showBack,
                    editing = editing,
                    deleting = deleting,
                    selectedCount = selectedKeys.size,
                    totalCount = visibleEntries.size,
                    refreshing = refreshing,
                    onBack = { if (editing) leaveEditMode() else navigator?.pop() },
                    onRefresh = ::refresh,
                    onEdit = { editing = true },
                    onSelectAll = {
                        selectedKeys = if (selectedKeys.size == visibleKeys.size) {
                            emptySet()
                        } else {
                            visibleKeys
                        }
                    },
                    onDelete = {
                        pendingDelete = visibleEntries.filter { it.bookKey in selectedKeys }
                        showDeleteDialog = true
                    },
                    onDone = ::leaveEditMode,
                    editTitle = editTitle,
                    selectedPattern = selectedPattern
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val minCardWidth = 148.dp
                    val gridSpacing = 12.dp
                    val horizontalPadding = 24.dp
                    // Compute from usable width so narrow phones still get
                    // two columns; wider tablets naturally receive more.
                    val columnCount = remember(maxWidth) {
                        (
                            (maxWidth - horizontalPadding + gridSpacing) /
                                (minCardWidth + gridSpacing)
                            ).toInt().coerceAtLeast(2)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().bookshelfPullToRefresh(
                            gridState = gridState,
                            enabled = !refreshing,
                            onRefresh = ::refresh
                        ),
                        contentPadding = PaddingValues(
                            start = horizontalPadding / 2,
                            end = horizontalPadding / 2,
                            top = 12.dp,
                            bottom = 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (visibleEntries.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(stringResource(R.string.bookshelf_empty))
                                    Text(
                                        stringResource(R.string.bookshelf_empty_hint),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                        items(visibleEntries, key = { it.bookKey }) { entry ->
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
                                            NovelPage(entry.asCoveredNovel(), favorite = BooleanStateHolder(true))
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteDialog && pendingDelete.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { if (deleteState !is FavoritePageModel.DeleteState.Deleting) {
                    showDeleteDialog = false
                    pendingDelete = emptyList()
                } },
                title = { Text(stringResource(R.string.bookshelf_delete_title)) },
                text = { Text(deleteConfirmText.format(pendingDelete.size)) },
                confirmButton = {
                    TextButton(
                        enabled = deleteState !is FavoritePageModel.DeleteState.Deleting,
                        onClick = {
                            val snapshot = pendingDelete
                            showDeleteDialog = false
                            pendingDelete = emptyList()
                            model.delete(snapshot)
                        }
                    ) {
                        Text(stringResource(R.string.bookshelf_delete_confirm_action), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = deleteState !is FavoritePageModel.DeleteState.Deleting,
                        onClick = {
                            showDeleteDialog = false
                            pendingDelete = emptyList()
                        }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                properties = DialogProperties(dismissOnBackPress = deleteState !is FavoritePageModel.DeleteState.Deleting)
            )
        }
    }
}

@Composable
private fun BookshelfTopBar(
    showBack: Boolean,
    editing: Boolean,
    deleting: Boolean,
    selectedCount: Int,
    totalCount: Int,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    editTitle: String,
    selectedPattern: String
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = if (editing || showBack) 0.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
                    }
                }
                if (editing) {
                    Text(
                        text = if (selectedCount == 0) editTitle else selectedPattern.format(selectedCount),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onSelectAll, enabled = totalCount > 0 && !deleting) {
                        Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.bookshelf_select_all))
                    }
                    IconButton(onClick = onDelete, enabled = selectedCount > 0 && !deleting) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.bookshelf_delete_selected))
                    }
                    IconButton(onClick = onDone, enabled = !deleting) {
                        Icon(Icons.Filled.Done, contentDescription = stringResource(R.string.bookshelf_edit_done))
                    }
                } else if (showBack) {
                    Text(
                        text = stringResource(R.string.bookshelf),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.sync_bookshelf))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.bookshelf_edit))
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bookshelf),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.bookshelf_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.sync_bookshelf))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.bookshelf_edit))
                    }
                }
            }
        }
    }
}

/** Lightweight, fixed-size shelf card kept separate from the list-style Novel component. */
@Composable
private fun BookshelfGridItem(
    entry: BookshelfEntry,
    editing: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val coverUrl = remember(entry.coverUrl) {
        EsjzoneUrls.coverOrEmpty(entry.coverUrl).trim().substringBefore('#')
    }
    val coverRequest = remember(coverUrl) {
        val data = coverUrl.takeIf { it.isNotBlank() } ?: R.drawable.missing_cover
        ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey("bookshelf-cover:$coverUrl")
            .diskCacheKey("bookshelf-cover:$coverUrl")
            // Avoid animation/recomposition work while flinging the grid.
            .crossfade(false)
            .build()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = coverRequest,
                imageLoader = MainActivity.imageLoader,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.missing_cover),
                error = painterResource(R.drawable.missing_cover),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            )
            if (editing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(28.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
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
                // A pull must start at the absolute top; never carry distance
                // from a gesture that began inside the first item.
                distance = 0f
            }
            if (!change.pressed) {
                if (
                    enabled &&
                    gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0 &&
                    distance > 72f
                ) onRefresh()
                distance = 0f
            } else if (gridState.firstVisibleItemIndex == 0 && change.positionChange().y > 0f) {
                distance += change.positionChange().y
            }
        }
    }
}

class FavoritePageModel(private val authorization: Authorization) :
    StateScreenModel<FavoritePageModel.State>(State.Idle) {
    val entries = BookshelfRepository.observe(authorization)

    sealed class State {
        data object Idle : State()
        data object Syncing : State()
        data class Completed(val result: BookshelfSyncResult) : State()
        data object Failed : State()
    }

    sealed class DeleteState {
        data object Idle : DeleteState()
        data object Deleting : DeleteState()
        data class Completed(val count: Int) : DeleteState()
        data object Failed : DeleteState()
    }

    private val _deleteState = kotlinx.coroutines.flow.MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: kotlinx.coroutines.flow.StateFlow<DeleteState> = _deleteState

    fun sync() {
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Syncing
            try {
                val result = BookshelfRepository.sync(authorization)
                mutableState.value = if (result.success) State.Completed(result) else State.Failed
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = State.Failed
            }
        }
    }

    fun scheduleMetadataSupplement() {
        BookshelfRepository.scheduleMetadataSupplement(authorization)
    }

    fun delete(entries: List<BookshelfEntry>) {
        if (entries.isEmpty() || _deleteState.value is DeleteState.Deleting) return
        // Set the guard before launching IO so two rapid confirmations cannot
        // enqueue duplicate removal jobs.
        _deleteState.value = DeleteState.Deleting
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val count = BookshelfRepository.removeBatch(authorization, entries)
                _deleteState.value = DeleteState.Completed(count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _deleteState.value = DeleteState.Failed
            }
        }
    }
}
