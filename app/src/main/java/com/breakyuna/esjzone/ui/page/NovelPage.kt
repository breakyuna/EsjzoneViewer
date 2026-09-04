package com.breakyuna.esjzone.ui.page

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.entity.BookshelfSyncState
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.VisibleChapterItem
import com.breakyuna.esjzone.novellibrary.component.initiallyExpandedChapterKeys
import com.breakyuna.esjzone.novellibrary.component.visibleChapterRows
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.novellibrary.novel.preview
import com.breakyuna.esjzone.offline.BackgroundDownloadStatus
import com.breakyuna.esjzone.offline.DownloadProgress
import com.breakyuna.esjzone.offline.DownloadedChapterRecord
import com.breakyuna.esjzone.offline.DownloadedNovelManifest
import com.breakyuna.esjzone.offline.NovelDownloadManager
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.offline.NovelExporter
import com.breakyuna.esjzone.ui.component.ChapterListRow
import com.breakyuna.esjzone.ui.component.Description
import com.breakyuna.esjzone.ui.component.QuietErrorState
import com.breakyuna.esjzone.ui.component.QuietLoadingState
import com.breakyuna.esjzone.ui.component.NovelDetailHero
import com.breakyuna.esjzone.ui.component.NovelDetailRule
import com.breakyuna.esjzone.ui.component.NovelDetailSectionHeading
import com.breakyuna.esjzone.ui.component.NovelDetailStats
import com.breakyuna.esjzone.ui.component.NovelDetailTags
import com.breakyuna.esjzone.ui.component.NovelDetailTopBar
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.navigation.BooleanStateHolder
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NovelPage(
    private val novel: Novel,
    private val history: ChapterStateHolder = ChapterStateHolder(),
    private val favorite: BooleanStateHolder = BooleanStateHolder()
) : Screen {

    override val key: ScreenKey =
        "NovelPage:" + EsjzoneUrls.canonicalPageKey(novel.url).ifBlank { novel.name.trim() }

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val context = LocalContext.current
        val screenModel = rememberScreenModel { NovelPageModel(authorization, novel) }
        val commentModel = rememberScreenModel { CommentPageModel(authorization, novel.url) }
        val state by screenModel.state.collectAsState()
        val localShelfEntry by BookshelfRepository.observeEntry(authorization, novel.url)
            .collectAsState(initial = null)
        var showMoreActions by rememberSaveable(novel.url) { mutableStateOf(false) }
        val exportScope = rememberCoroutineScope()
        val detailedForExport = (state as? NovelPageModel.State.Result)?.detailed

        fun export(uri: Uri, format: NovelExportFormat, detailed: DetailedNovel) {
            exportScope.launch {
                val succeeded = try {
                    exportNovel(context, detailed, uri, format)
                    true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e(
                        "NovelPage",
                        "Failed to export ${detailed.name} as ${format.name}",
                        error
                    )
                    false
                }
                Toast.makeText(
                    context,
                    if (succeeded) R.string.novel_export_success else R.string.novel_export_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Register launchers from the screen composition, not from a LazyColumn
        // item subcomposition.  The latter may not carry the ActivityResult
        // registry owner on some Android/Compose combinations.
        val txtLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain")
        ) { uri ->
            val detailed = detailedForExport
            if (uri != null && detailed != null) export(uri, NovelExportFormat.TXT, detailed)
        }
        val epubLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/epub+zip")
        ) { uri ->
            val detailed = detailedForExport
            if (uri != null && detailed != null) export(uri, NovelExportFormat.EPUB, detailed)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            NovelDetailTopBar(
                onBack = { navigator?.pop() },
                onOpenExternal = {
                    openExternal(
                        context,
                        (state as? NovelPageModel.State.Result)?.detailed?.sourceUrl
                            ?.takeIf(String::isNotBlank)
                            ?: EsjzoneUrls.resolve(novel.url)
                    )
                },
                onMore = { showMoreActions = true }
            )

            when (val snapshot = state) {
                NovelPageModel.State.Loading -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { QuietLoadingState() }

                is NovelPageModel.State.Error -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    QuietErrorState(
                        onRetry = screenModel::retry,
                        failure = snapshot.failure
                    )
                }

                is NovelPageModel.State.Result -> {
                    val detailed = snapshot.detailed
                    val chapterList = detailed.chapterList
                    val historyState = history.state()
                    val hasHistory = rememberSaveable(novel.url) {
                        mutableStateOf(chapterList.hasHistory)
                    }
                    val favoriteState = favorite.state()
                    var rememberedFavorite by rememberSaveable(novel.url) {
                        mutableStateOf(favoriteState.value)
                    }
                    var descriptionExpanded by rememberSaveable(novel.url) {
                        mutableStateOf(false)
                    }
                    var showAllChapters by rememberSaveable(novel.url) {
                        mutableStateOf(false)
                    }
                    val expandedChapterGroups = rememberSaveable(novel.url) {
                        mutableStateOf(initiallyExpandedChapterKeys(chapterList.items))
                    }
                    val visibleRows = remember(chapterList, expandedChapterGroups.value) {
                        visibleChapterRows(chapterList.items, expandedChapterGroups.value)
                    }
                    val hasExplicitHistory = remember(chapterList) {
                        historyState.value != null
                    }

                    LaunchedEffect(chapterList) {
                        if (historyState.value == null) historyState.value = chapterList.toRead
                        // A first chapter chosen as the default target is not
                        // a resume marker. Only server history or a chapter
                        // explicitly supplied by the source task means
                        // "Continue reading".
                        hasHistory.value = chapterList.hasHistory || hasExplicitHistory
                    }

                    LaunchedEffect(detailed.isFavorite, localShelfEntry?.operationVersion) {
                        // A local row is authoritative for the visual toggle;
                        // the detail response only supplements missing metadata.
                        if (localShelfEntry != null || detailed.isFavorite) {
                            screenModel.seedFavoriteMetadata(
                                author = detailed.author,
                                coverUrl = detailed.coverUrl,
                                isAdult = detailed.isAdult
                            )
                        }
                    }
                    LaunchedEffect(localShelfEntry?.operationVersion, localShelfEntry?.visible) {
                        localShelfEntry?.let {
                            favoriteState.value = it.visible
                            rememberedFavorite = it.visible
                        }
                    }

                    NovelDetailContent(
                        detailed = detailed,
                        authorization = authorization,
                        history = history,
                        historyState = historyState,
                        hasHistory = hasHistory,
                        onExportTxt = {
                            txtLauncher.launch(NovelExporter.suggestedFileName(detailed.name, "txt"))
                        },
                        onExportEpub = {
                            epubLauncher.launch(NovelExporter.suggestedFileName(detailed.name, "epub"))
                        },
                        favorite = rememberedFavorite,
                        favoritePending = localShelfEntry?.syncState != null &&
                            localShelfEntry?.syncState != BookshelfSyncState.SYNCED,
                        favoriteFailed = !localShelfEntry?.lastError.isNullOrBlank(),
                        onToggleFavorite = {
                            val next = !rememberedFavorite
                            favoriteState.value = next
                            rememberedFavorite = next
                            screenModel.persistFavorite(next)
                        },
                        descriptionExpanded = descriptionExpanded,
                        onDescriptionExpandedChange = { descriptionExpanded = it },
                        showAllChapters = showAllChapters,
                        onShowAllChaptersChange = { showAllChapters = it },
                        onGroupToggle = { key ->
                            expandedChapterGroups.value = expandedChapterGroups.value.toMutableSet().also {
                                if (!it.add(key)) it.remove(key)
                            }
                        },
                        visibleRows = visibleRows,
                        commentModel = commentModel,
                        navigator = navigator,
                        context = context,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (showMoreActions) {
            NovelMoreActionsSheet(
                detailed = (state as? NovelPageModel.State.Result)?.detailed,
                onDismiss = { showMoreActions = false },
                onOpenSource = {
                    showMoreActions = false
                    openExternal(
                        context,
                        (state as? NovelPageModel.State.Result)?.detailed?.sourceUrl
                            ?.takeIf(String::isNotBlank)
                            ?: EsjzoneUrls.resolve(novel.url)
                    )
                },
                onOpenForum = { forumUrl ->
                    showMoreActions = false
                    openExternal(context, EsjzoneUrls.resolve(forumUrl))
                }
            )
        }

        LaunchedEffect(Unit) { screenModel.getDetail() }
    }
}

@Composable
private fun NovelDetailContent(
    detailed: DetailedNovel,
    authorization: Authorization,
    history: ChapterStateHolder,
    historyState: androidx.compose.runtime.MutableState<com.breakyuna.esjzone.novellibrary.novel.Chapter?>,
    hasHistory: androidx.compose.runtime.MutableState<Boolean>,
    onExportTxt: () -> Unit,
    onExportEpub: () -> Unit,
    favorite: Boolean,
    favoritePending: Boolean,
    favoriteFailed: Boolean,
    onToggleFavorite: () -> Unit,
    descriptionExpanded: Boolean,
    onDescriptionExpandedChange: (Boolean) -> Unit,
    showAllChapters: Boolean,
    onShowAllChaptersChange: (Boolean) -> Unit,
    onGroupToggle: (String) -> Unit,
    visibleRows: List<com.breakyuna.esjzone.novellibrary.component.VisibleChapterRow>,
    commentModel: CommentPageModel,
    navigator: cafe.adriel.voyager.navigator.Navigator?,
    context: Context,
    modifier: Modifier = Modifier
) {
    val orderedChapters = detailed.chapterList.orderedChapters
    val targetChapter = historyState.value ?: detailed.chapterList.toRead
    val descriptionPreview = remember(detailed.description) {
        detailed.description.preview(360)
    }
    val previewChapters = remember(detailed.chapterList, targetChapter) {
        buildList {
            targetChapter?.let(::add)
            orderedChapters.lastOrNull()?.let(::add)
        }.distinctBy { it.url }
    }
    val onChapterOpen: (Chapter) -> Unit = { chapter ->
        historyState.value = chapter
        hasHistory.value = true
        navigator?.pushIfNotCurrent(
            ChapterPage(
                novelId = detailed.id(),
                chapter = chapter,
                history = history,
                chapterOrder = orderedChapters,
                novelName = detailed.name,
                novelUrl = detailed.url,
                novelCoverUrl = detailed.coverUrl
            )
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = QuietEditorial.contentMaxWidth)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "detail-hero") {
                NovelDetailHero(
                    novel = detailed,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .widthIn(max = QuietEditorial.contentMaxWidth)
                )
            }

            item(key = "detail-stats") {
                NovelDetailStats(
                    novel = detailed,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .widthIn(max = QuietEditorial.contentMaxWidth)
                )
            }

            if (detailed.tags.isNotEmpty()) {
                item(key = "detail-tags") {
                    NovelDetailTags(
                        tags = detailed.tags,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .widthIn(max = QuietEditorial.contentMaxWidth)
                    )
                }
            }

            item(key = "detail-actions") {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .widthIn(max = QuietEditorial.contentMaxWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = targetChapter != null,
                            onClick = {
                                targetChapter?.let { chapter ->
                                    navigator?.pushIfNotCurrent(
                                        ChapterPage(
                                            novelId = detailed.id(),
                                            chapter = chapter,
                                            history = history,
                                            chapterOrder = orderedChapters,
                                            novelName = detailed.name,
                                            novelUrl = detailed.url,
                                            novelCoverUrl = detailed.coverUrl
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .heightIn(min = 52.dp),
                            shape = QuietEditorial.controlShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = stringResource(
                                    if (hasHistory.value) R.string.continue_reading else R.string.start_reading
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            shape = QuietEditorial.controlShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (favorite) R.string.novel_favorited else R.string.novel_favorite
                                ),
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (favoriteFailed) {
                                    stringResource(R.string.novel_favorite_failed)
                                } else if (favoritePending) {
                                    stringResource(R.string.novel_favorite_pending)
                                } else {
                                    stringResource(
                                        if (favorite) R.string.novel_favorited else R.string.novel_favorite
                                    )
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NovelDownloadActions(
                            novel = detailed,
                            authorization = authorization,
                            onExportTxt = onExportTxt,
                            onExportEpub = onExportEpub,
                            modifier = Modifier.weight(1f)
                        )
                        if (detailed.forumUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    openExternal(context, EsjzoneUrls.resolve(detailed.forumUrl))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp),
                                shape = QuietEditorial.controlShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forum,
                                    contentDescription = stringResource(R.string.novel_open_forum),
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.novel_open_forum),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (descriptionPreview.isNotBlank() || detailed.description.components.isNotEmpty()) {
                item(key = "detail-description") {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .widthIn(max = QuietEditorial.contentMaxWidth)
                    ) {
                        NovelDetailSectionHeading(title = stringResource(R.string.description))
                        Spacer(modifier = Modifier.height(8.dp))
                        if (descriptionExpanded) {
                            Description(
                                description = detailed.description,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (descriptionPreview.isNotBlank()) {
                            Text(
                                text = descriptionPreview,
                                style = QuietEditorial.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = {
                                onDescriptionExpandedChange(!descriptionExpanded)
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = stringResource(
                                    if (descriptionExpanded) {
                                        R.string.novel_description_collapse
                                    } else {
                                        R.string.novel_description_expand
                                    }
                                )
                            )
                        }
                    }
                }
            }

            item(key = "detail-chapters-heading") {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .widthIn(max = QuietEditorial.contentMaxWidth)
                ) {
                    NovelDetailRule()
                    Spacer(modifier = Modifier.height(18.dp))
                    NovelDetailSectionHeading(
                        title = stringResource(R.string.novel_chapterlist),
                        supportingText = orderedChapters.takeIf { it.isNotEmpty() }?.let {
                            stringResource(R.string.novel_chapter_count, it.size)
                        },
                        actionLabel = if (orderedChapters.isNotEmpty()) {
                            stringResource(
                                if (showAllChapters) R.string.novel_show_fewer_chapters
                                else R.string.novel_show_all_chapters
                            )
                        } else null,
                        onAction = if (orderedChapters.isNotEmpty()) {
                            { onShowAllChaptersChange(!showAllChapters) }
                        } else null
                    )
                }
            }

            if (orderedChapters.isEmpty()) {
                item(key = "detail-chapters-empty") {
                    Text(
                        text = stringResource(R.string.reader_contents_empty),
                        style = QuietEditorial.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .widthIn(max = QuietEditorial.contentMaxWidth)
                    )
                }
            } else if (!showAllChapters) {
                item(key = "detail-chapter-preview") {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .widthIn(max = QuietEditorial.contentMaxWidth),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        previewChapters.forEachIndexed { index, chapter ->
                            ChapterListRow(
                                row = VisibleChapterItem(
                                    item = ChapterItem(chapter),
                                    key = "chapter-preview:${chapter.url}:$index",
                                    depth = 0
                                ),
                                currentChapter = historyState.value,
                                hasHistory = hasHistory.value,
                                onChapterOpen = onChapterOpen,
                                onGroupToggle = {}
                            )
                        }
                    }
                }
            } else {
                items(visibleRows, key = { it.key }) { row ->
                    ChapterListRow(
                        row = row,
                        currentChapter = historyState.value,
                        hasHistory = hasHistory.value,
                        onChapterOpen = onChapterOpen,
                        onGroupToggle = onGroupToggle
                    )
                }
                item(key = "detail-chapters-collapse") {
                    TextButton(
                        onClick = { onShowAllChaptersChange(false) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(stringResource(R.string.novel_show_fewer_chapters))
                    }
                }
            }

            item(key = "detail-comments-rule") {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                        .widthIn(max = QuietEditorial.contentMaxWidth)
                ) { NovelDetailRule() }
            }
            item(key = "detail-comments") {
                Column(
                    modifier = Modifier
                        .widthIn(max = QuietEditorial.contentMaxWidth)
                ) {
                    NovelDetailSectionHeading(title = stringResource(R.string.comments))
                    Spacer(modifier = Modifier.height(8.dp))
                    CommentSectionContent(
                        model = commentModel,
                        showHeader = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        CommentComposerHost(model = commentModel)
    }
}

private enum class NovelExportFormat {
    TXT,
    EPUB
}

private suspend fun exportNovel(
    context: Context,
    novel: DetailedNovel,
    uri: Uri,
    format: NovelExportFormat
) {
    withContext(Dispatchers.IO) {
        val manifest = NovelDownloadStore.manifest(novel.url)
            ?.takeIf { it.complete }
            ?: error("Novel download is incomplete")
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: error("Unable to open the selected file")
        output.use { stream ->
            val loader = { record: DownloadedChapterRecord ->
                NovelDownloadStore.chapterContent(novel.url, record)
            }
            when (format) {
                NovelExportFormat.TXT -> NovelExporter.exportTxt(manifest, loader, stream)
                NovelExportFormat.EPUB -> NovelExporter.exportEpub(
                    manifest = manifest,
                    chapterLoader = loader,
                    output = stream,
                    imageLoader = { component ->
                        NovelDownloadStore.imageFile(novel.url, component)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelDownloadActions(
    novel: DetailedNovel,
    authorization: Authorization,
    onExportTxt: () -> Unit,
    onExportEpub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSheet by rememberSaveable(novel.url) { mutableStateOf(false) }
    var downloaded by remember(novel.url) { mutableStateOf<DownloadedNovelManifest?>(null) }
    var downloadStatus by remember(novel.url) { mutableStateOf<BackgroundDownloadStatus?>(null) }
    var downloading by remember(novel.url) { mutableStateOf(false) }
    var progress by remember(novel.url) { mutableStateOf<DownloadProgress?>(null) }
    var requestedWorkId by rememberSaveable(novel.url) { mutableStateOf<String?>(null) }

    LaunchedEffect(novel.url) {
        downloaded = withContext(Dispatchers.IO) { NovelDownloadStore.manifest(novel.url) }
    }

    LaunchedEffect(novel.url, requestedWorkId) {
        while (isActive) {
            val status = runCatching {
                withContext(Dispatchers.IO) {
                    NovelDownloadManager.status(context, novel.url)
                }
            }.getOrNull()
            downloadStatus = status
            status?.progress?.let { progress = it }
            val waitingForEnqueue = requestedWorkId != null &&
                (status == null || status.id != requestedWorkId)
            downloading = status?.running == true || waitingForEnqueue
            // WorkManager keeps an already-running unique job when enqueue is
            // called again. In that case the returned request id can differ
            // from the job currently reported for this novel; the unique-job
            // status is still the source of truth for this page.
            if (status?.finished == true && requestedWorkId != null) {
                downloaded = withContext(Dispatchers.IO) {
                    NovelDownloadStore.manifest(novel.url)
                }
                Toast.makeText(
                    context,
                    if (status.succeeded) R.string.novel_download_success
                    else R.string.novel_download_failed,
                    Toast.LENGTH_SHORT
                ).show()
                requestedWorkId = null
                downloading = false
            }
            if (!downloading && requestedWorkId == null) break
            delay(750)
        }
    }

    fun enqueueDownload() {
        if (downloading || novel.chapterList.orderedChapters.isEmpty()) return
        val existingCompleted = downloaded?.chapters?.count { it.downloaded } ?: 0
        progress = DownloadProgress(
            completed = existingCompleted,
            total = novel.chapterList.orderedChapters.size,
            chapterName = ""
        )
        downloading = true
        runCatching {
            NovelDownloadManager.enqueue(
                context = context,
                authorization = authorization,
                novel = novel
            )
        }.onSuccess { requestId ->
            requestedWorkId = requestId.toString()
        }.onFailure { error ->
            downloading = false
            AppLogger.e("NovelPage", "Unable to schedule background novel download", error)
            Toast.makeText(context, R.string.novel_download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    FilledTonalButton(
        onClick = { showSheet = true },
        modifier = modifier.heightIn(min = 52.dp),
        shape = QuietEditorial.controlShape,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = if (downloaded?.complete == true) Icons.Filled.DownloadDone else Icons.Filled.Download,
            contentDescription = stringResource(R.string.novel_download),
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (downloading) stringResource(R.string.novel_downloading_count,
                progress?.completed ?: 0, progress?.total ?: novel.chapterList.orderedChapters.size)
            else stringResource(
                if (downloaded?.complete == true) R.string.novel_download_update
                else R.string.novel_download
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (showSheet) {
        NovelDownloadSheet(
            novel = novel,
            manifest = downloaded,
            status = downloadStatus,
            progress = progress,
            downloading = downloading,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { showSheet = false },
            onDownload = ::enqueueDownload,
            onExportTxt = {
                showSheet = false
                onExportTxt()
            },
            onExportEpub = {
                showSheet = false
                onExportEpub()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelDownloadSheet(
    novel: DetailedNovel,
    manifest: DownloadedNovelManifest?,
    status: BackgroundDownloadStatus?,
    progress: DownloadProgress?,
    downloading: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onExportTxt: () -> Unit,
    onExportEpub: () -> Unit
) {
    val completed = manifest?.chapters?.count { it.downloaded } ?: 0
    val total = novel.chapterList.orderedChapters.size
    val actualTotal = manifest?.chapters?.size?.takeIf { it > 0 } ?: total
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .widthIn(max = QuietEditorial.contentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = stringResource(R.string.novel_local_copy_title),
                    style = QuietEditorial.sectionTitle,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }
            Text(
                text = novel.name,
                style = QuietEditorial.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = QuietEditorial.largeShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        downloading -> {
                            Text(
                                text = stringResource(R.string.novel_downloading_count,
                                    progress?.completed ?: 0,
                                    progress?.total ?: total),
                                style = QuietEditorial.title
                            )
                            val current = progress
                            if (current != null && current.total > 0) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = (current.completed.toFloat() / current.total.toFloat())
                                        .coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                current.chapterName.takeIf(String::isNotBlank)?.let {
                                    Text(
                                        text = it,
                                        style = QuietEditorial.label,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        status?.finished == true && !status.succeeded -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(21.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.novel_download_failed),
                                    color = MaterialTheme.colorScheme.error,
                                    style = QuietEditorial.body
                                )
                            }
                            if (completed > 0) {
                                Text(
                                    text = stringResource(R.string.novel_local_copy_partial, completed, actualTotal),
                                    style = QuietEditorial.body,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        manifest?.complete == true -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.novel_local_copy_complete, completed, actualTotal),
                                    style = QuietEditorial.title
                                )
                            }
                        }
                        manifest != null && completed > 0 -> {
                            Text(
                                text = stringResource(R.string.novel_local_copy_partial, completed, actualTotal),
                                style = QuietEditorial.title
                            )
                        }
                        total == 0 -> {
                            Text(
                                text = stringResource(R.string.novel_download_no_chapters),
                                style = QuietEditorial.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.novel_local_copy_none),
                                style = QuietEditorial.title
                            )
                            Text(
                                text = stringResource(R.string.novel_download_background_note),
                                style = QuietEditorial.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            FilledTonalButton(
                enabled = !downloading && total > 0,
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = QuietEditorial.controlShape
            ) {
                Icon(
                    imageVector = if (manifest?.complete == true) Icons.Filled.Refresh else Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = stringResource(
                        if (manifest?.complete == true) R.string.novel_download_update
                        else R.string.novel_download
                    )
                )
            }

            if (manifest?.complete == true && !downloading) {
                NovelDetailRule()
                Text(
                    text = stringResource(R.string.novel_export_title),
                    style = QuietEditorial.sectionTitle
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportTxt,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = QuietEditorial.controlShape
                    ) {
                        Icon(Icons.Filled.TextSnippet, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.novel_export_txt))
                    }
                    OutlinedButton(
                        onClick = onExportEpub,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = QuietEditorial.controlShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.novel_export_epub))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelMoreActionsSheet(
    detailed: DetailedNovel?,
    onDismiss: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenForum: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.novel_more_actions),
                style = QuietEditorial.sectionTitle,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            TextButton(onClick = onOpenSource, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.novel_open_source), modifier = Modifier.weight(1f))
            }
            detailed?.forumUrl?.takeIf(String::isNotBlank)?.let { forumUrl ->
                TextButton(
                    onClick = { onOpenForum(forumUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.novel_open_forum), modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun openExternal(context: Context, rawUrl: String) {
    val url = rawUrl.trim()
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure { error ->
        if (error !is ActivityNotFoundException) {
            AppLogger.w("NovelPage", "Unable to open external URL", error)
        }
    }
}
