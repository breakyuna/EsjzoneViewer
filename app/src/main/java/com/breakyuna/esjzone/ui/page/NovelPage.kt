package com.breakyuna.esjzone.ui.page
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.breakyuna.esjzone.ui.designsystem.appAccentColors
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.features.getChapterDetail
import com.breakyuna.esjzone.network.cancellablePageRequest

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.breakyuna.esjzone.ui.designsystem.GlobalToast as Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import com.breakyuna.esjzone.network.features.unlockPasswordProtectedChapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.database.entity.BookshelfSyncState
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.VisibleChapterItem
import com.breakyuna.esjzone.novellibrary.component.VisibleChapterGroup
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
import com.breakyuna.esjzone.offline.NovelExporter
import com.breakyuna.esjzone.ui.component.Description
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppLottieAsset
import com.breakyuna.esjzone.ui.designsystem.AppLottieState
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.AppTouchTarget
import com.breakyuna.esjzone.ui.designsystem.appStateColors
import com.breakyuna.esjzone.ui.designsystem.AppAdaptiveMetrics
import com.breakyuna.esjzone.ui.designsystem.AppWindowSizeClass
import com.breakyuna.esjzone.ui.designsystem.rememberAppAdaptiveMetrics
import com.breakyuna.esjzone.ui.designsystem.AppImageViewer
import com.breakyuna.esjzone.ui.product.NovelCoverModel
import com.breakyuna.esjzone.ui.product.NovelHero
import com.breakyuna.esjzone.ui.product.NovelMetadataModel
import com.breakyuna.esjzone.ui.product.NovelMetricModel
import com.breakyuna.esjzone.ui.product.NovelTag
import com.breakyuna.esjzone.ui.product.NovelTagModel
import com.breakyuna.esjzone.ui.product.LoadingSkeleton
import com.breakyuna.esjzone.ui.product.ErrorState
import com.breakyuna.esjzone.ui.product.OfflineState
import com.breakyuna.esjzone.ui.navigation.BooleanStateHolder
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.AppNavigator
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class NovelPage(
    private val novel: Novel,
    private val history: ChapterStateHolder = ChapterStateHolder(),
    private val favorite: BooleanStateHolder = BooleanStateHolder()
) : AppDestination {

    override val key: String =
        "NovelPage:" + EsjzoneUrls.canonicalPageKey(novel.url).ifBlank { novel.name.trim() }

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val context = LocalContext.current
        val screenModel = rememberAppViewModel { NovelPageModel(authorization, novel) }
        val commentModel = rememberAppViewModel { CommentPageModel(authorization, novel.url) }
        val state by screenModel.state.collectAsState()
        val localShelfEntry by BookshelfRepository.observeEntry(authorization, novel.url)
            .collectAsState(initial = null)
        var showMoreActions by rememberSaveable(novel.url) { mutableStateOf(false) }
        var exportFormat by rememberSaveable(novel.url) { mutableStateOf<NovelExportFormat?>(null) }
        var exportSelection by rememberSaveable(novel.url, stateSaver = DownloadChapterSelectionSaver) {
            mutableStateOf(emptySet<String>())
        }
        val exportScope = rememberCoroutineScope()
        val detailedForExport = (state as? NovelPageModel.State.Result)?.detailed

        fun export(uri: Uri, format: NovelExportFormat, detailed: DetailedNovel) {
            val selected = exportSelection
            exportScope.launch {
                val succeeded = try {
                    exportNovel(context, detailed, uri, format, selected)
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

        exportFormat?.let { format ->
            detailedForExport?.let { detailed ->
                ExportChapterSelectionDialog(
                    novelUrl = detailed.url,
                    title = stringResource(if (format == NovelExportFormat.TXT)
                        R.string.novel_export_txt else R.string.novel_export_epub),
                    initialSelection = exportSelection.takeIf { it.isNotEmpty() },
                    onDismiss = { exportFormat = null },
                    onExport = { selected ->
                        exportSelection = selected
                        exportFormat = null
                        when (format) {
                            NovelExportFormat.TXT -> txtLauncher.launch(NovelExporter.suggestedFileName(detailed.name, "txt"))
                            NovelExportFormat.EPUB -> epubLauncher.launch(NovelExporter.suggestedFileName(detailed.name, "epub"))
                        }
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            RebuiltDetailTopBar(
                onBack = { navigator?.pop() },
                onOpenExternal = {
                    openSystemBrowser(
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
                    contentAlignment = Alignment.TopCenter
                ) { LoadingSkeleton(modifier = Modifier.fillMaxWidth()) }

                is NovelPageModel.State.Error -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (snapshot.failure == LoadFailureKind.NETWORK) {
                        OfflineState(
                            modifier = Modifier.fillMaxWidth(),
                            onRetry = screenModel::retry
                        )
                    } else {
                        ErrorState(
                            title = stringResource(R.string.community_load_failed),
                            message = stringResource(R.string.community_empty_guidance),
                            modifier = Modifier.fillMaxWidth(),
                            onRetry = screenModel::retry
                        )
                    }
                }

                is NovelPageModel.State.Result -> {
                    val detailed = snapshot.detailed
                    val chapterList = detailed.chapterList
                    val localReading by remember(detailed.id(), detailed.url) {
                        PresentationAccess.database.localReadingActivityDao()
                            .observeLatestForIdentity(detailed.id(), detailed.url)
                    }.collectAsState(initial = null)
                    LaunchedEffect(localReading?.activityId, localReading?.novelName, detailed.name) {
                        val activity = localReading ?: return@LaunchedEffect
                        if (detailed.name.isNotBlank() && detailed.name != activity.novelId &&
                            detailed.name != activity.chapterName &&
                            (activity.novelName.isBlank() || activity.novelName == activity.novelId ||
                                activity.novelName == activity.chapterName)) {
                            try {
                                withContext(Dispatchers.IO) {
                                    PresentationAccess.database.localReadingActivityDao()
                                        .updateName(activity.activityId, detailed.name)
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                AppLogger.w("NovelPage", "Unable to repair local history title", error)
                            }
                        }
                    }
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

                    LaunchedEffect(chapterList, localReading) {
                        if (historyState.value == null) historyState.value = chapterList.toRead
                        // A first chapter chosen as the default target is not
                        // a resume marker. Only server history or a chapter
                        // explicitly supplied by the source task means
                        // "Continue reading".
                        hasHistory.value = chapterList.hasHistory || hasExplicitHistory || localReading != null
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
                        localReading = localReading,
                        hasHistory = hasHistory,
                        onExportTxt = {
                            exportFormat = NovelExportFormat.TXT
                        },
                        onExportEpub = {
                            exportFormat = NovelExportFormat.EPUB
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
                    openSystemBrowser(
                        context,
                        (state as? NovelPageModel.State.Result)?.detailed?.sourceUrl
                            ?.takeIf(String::isNotBlank)
                            ?: EsjzoneUrls.resolve(novel.url)
                    )
                },
                onOpenForum = { forumUrl ->
                    showMoreActions = false
                    openSystemBrowser(context, EsjzoneUrls.resolve(forumUrl))
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
    localReading: LocalReadingActivity?,
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
    navigator: AppNavigator?,
    context: Context,
    modifier: Modifier = Modifier
) {
    val orderedChapters = detailed.chapterList.orderedChapters
    var downloaded by remember(detailed.url) { mutableStateOf<com.breakyuna.esjzone.offline.DownloadedNovelManifest?>(null) }
    LaunchedEffect(detailed.url) {
        downloaded = kotlinx.coroutines.withContext(Dispatchers.IO) {
            PresentationAccess.downloads.manifest(detailed.url)
        }
    }
    val passwordRequiredChapterKeys = remember(downloaded) {
        downloaded?.pendingPasswordChapters.orEmpty()
            .map { com.breakyuna.esjzone.offline.NovelDownloadStore.chapterKey(it.url) }
            .toSet()
    }
    val localChapter = localReading?.let { activity ->
        orderedChapters.firstOrNull {
            EsjzoneUrls.canonicalPageKey(it.url) == EsjzoneUrls.canonicalPageKey(activity.chapterUrl)
        } ?: activity.chapterUrl.takeIf { it.isNotBlank() }
            ?.let { Chapter(activity.chapterName, it, true) }
    }
    val targetChapter = localChapter ?: historyState.value ?: detailed.chapterList.toRead
    val metrics = rememberAppAdaptiveMetrics()
    var hasVisualOverflow by remember(detailed.description) {
        mutableStateOf(false)
    }
    val descriptionText = remember(detailed.description) {
        detailed.description.preview(Int.MAX_VALUE)
    }
    val hasRichDescription = remember(detailed.description) {
        detailed.description.components.any { it !is TextComponent }
    }
    val isExpandable = hasRichDescription ||
        detailed.description.components.size > 1 ||
        descriptionText.length > 120 ||
        hasVisualOverflow
    val isExpanded = descriptionExpanded || !isExpandable
    val showDescriptionToggle = isExpandable
    val previewChapters = remember(detailed.chapterList, targetChapter) {
        buildList {
            targetChapter?.let(::add)
            orderedChapters.lastOrNull()?.let(::add)
        }.distinctBy { it.url }
    }
    val latestChapter = orderedChapters.lastOrNull()
    val onChapterOpen: (Chapter) -> Unit = remember(detailed, history, orderedChapters, navigator, context) {
        { chapter: Chapter ->
            if (chapter.isExternal) {
                openExternal(context, EsjzoneUrls.resolve(chapter.url))
            } else {
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
        }
    }

    val horizontalPadding = when (metrics.sizeClass) {
        AppWindowSizeClass.Compact -> AppSpacing.sm
        AppWindowSizeClass.Medium -> AppSpacing.md
        AppWindowSizeClass.Expanded -> AppSpacing.lg
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (metrics.sizeClass != AppWindowSizeClass.Compact) {
                        Modifier.widthIn(max = metrics.contentMaxWidth)
                    } else {
                        Modifier
                    }
                )
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                bottom = AppSpacing.lg
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            item(key = "detail-hero", contentType = "novel-hero") {
                RebuiltNovelHero(detailed, metrics)
            }

            if (detailed.tags.isNotEmpty()) {
                item(key = "detail-tags", contentType = "novel-tags") {
                    RebuiltNovelTags(
                        tags = detailed.tags,
                        onTagClick = { tag -> navigator?.pushIfNotCurrent(SearchPage(tag)) }
                    )
                }
            }

            item(key = "detail-actions", contentType = "novel-actions") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        Button(
                            enabled = targetChapter != null,
                            onClick = {
                                targetChapter?.let { chapter ->
                                    if (chapter.isExternal) {
                                        openExternal(context, EsjzoneUrls.resolve(chapter.url))
                                    } else {
                                        navigator?.pushIfNotCurrent(
                                            ChapterPage(
                                                novelId = detailed.id(),
                                                chapter = chapter,
                                                history = history,
                                                chapterOrder = orderedChapters,
                                                novelName = detailed.name,
                                                novelUrl = detailed.url,
                                                novelCoverUrl = detailed.coverUrl,
                                                resumeChapterProgress = localReading?.chapterProgress
                                                    ?.takeIf { localChapter != null &&
                                                        EsjzoneUrls.canonicalPageKey(chapter.url) ==
                                                        EsjzoneUrls.canonicalPageKey(localChapter.url) }
                                            )
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = AppTouchTarget.minimum),
                            shape = AppShapes.standard,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = AppSpacing.sm, vertical = AppSpacing.sm)
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
                        Button(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = AppTouchTarget.minimum),
                            shape = AppShapes.standard,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (favorite) appAccentColors().favoriteContainer else MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = if (favorite) appAccentColors().favorite else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = AppSpacing.sm, vertical = AppSpacing.sm)
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
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        NovelDownloadActions(
                            novel = detailed,
                            authorization = authorization,
                            downloaded = downloaded,
                            onDownloadedChange = { downloaded = it },
                            onExportTxt = onExportTxt,
                            onExportEpub = onExportEpub,
                            modifier = if (detailed.forumUrl.isBlank()) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.weight(1f)
                            }
                        )
                        if (detailed.forumUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    openSystemBrowser(context, EsjzoneUrls.resolve(detailed.forumUrl))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = AppTouchTarget.minimum),
                                shape = AppShapes.standard,
                                contentPadding = PaddingValues(horizontal = AppSpacing.sm, vertical = AppSpacing.sm)
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

            if (descriptionText.isNotBlank() || detailed.description.components.isNotEmpty()) {
                item(key = "detail-description", contentType = "novel-description") {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RebuiltSectionHeading(title = stringResource(R.string.description))
                        Spacer(modifier = Modifier.height(AppSpacing.sm))
                        if (isExpanded || descriptionText.isBlank()) {
                            Description(
                                description = detailed.description,
                                showHeader = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (showDescriptionToggle) {
                                            Modifier.clickable { onDescriptionExpandedChange(true) }
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                Text(
                                    text = descriptionText,
                                    style = AppTypography.bodyMedium.copy(
                                        lineHeight = 22.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { result ->
                                        if (result.hasVisualOverflow) {
                                            hasVisualOverflow = true
                                        }
                                    }
                                )
                            }
                        }
                        if (showDescriptionToggle) {
                            TextButton(
                                onClick = {
                                    onDescriptionExpandedChange(!descriptionExpanded)
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(
                                        if (isExpanded) {
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
            }

            item(key = "detail-chapters-heading", contentType = "chapter-heading") {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RebuiltRule()
                    Spacer(modifier = Modifier.height(AppSpacing.lg))
                    RebuiltSectionHeading(
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
                item(key = "detail-chapters-empty", contentType = "chapter-empty") {
                    Text(
                        text = stringResource(R.string.reader_contents_empty),
                        style = AppTypography.bodyMedium,
                        color = appStateColors().contentMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppSpacing.lg)
                    )
                }
            } else if (!showAllChapters) {
                item(key = "detail-chapter-preview", contentType = "chapter-preview") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        previewChapters.forEachIndexed { index, chapter ->
                            key("chapter-preview:${chapter.url}:$index") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    when {
                                        hasHistory.value && targetChapter?.url == chapter.url -> {
                                            Text(
                                                text = stringResource(R.string.novel_last_read),
                                                style = AppTypography.labelMedium,
                                                color = appStateColors().contentMuted
                                            )
                                        }
                                        latestChapter?.url == chapter.url -> {
                                            Text(
                                                text = stringResource(R.string.novel_latest_chapter),
                                                style = AppTypography.labelMedium,
                                                color = appStateColors().contentMuted
                                            )
                                        }
                                    }
                                    val isPw = passwordRequiredChapterKeys.contains(
                                        com.breakyuna.esjzone.offline.NovelDownloadStore.chapterKey(chapter.url)
                                    )
                                    RebuiltChapterRow(
                                        row = VisibleChapterItem(
                                            item = ChapterItem(chapter),
                                            key = "chapter-preview:${chapter.url}:$index",
                                            depth = 0
                                        ),
                                        currentChapter = historyState.value,
                                        hasHistory = hasHistory.value,
                                        onChapterOpen = onChapterOpen,
                                        onGroupToggle = {},
                                        isPasswordRequired = isPw
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(
                    visibleRows,
                    key = { it.key },
                    contentType = { row ->
                        when (row) {
                            is VisibleChapterGroup -> "chapter-group"
                            is VisibleChapterItem -> "chapter-item"
                        }
                    }
                ) { row ->
                    val isPw = (row as? VisibleChapterItem)?.let { item ->
                        (item.item as? ChapterItem)?.let { ci ->
                            passwordRequiredChapterKeys.contains(
                                com.breakyuna.esjzone.offline.NovelDownloadStore.chapterKey(ci.chapter.url)
                            )
                        }
                    } == true
                    RebuiltChapterRow(
                        row = row,
                        currentChapter = historyState.value,
                        hasHistory = hasHistory.value,
                        onChapterOpen = onChapterOpen,
                        onGroupToggle = onGroupToggle,
                        isPasswordRequired = isPw
                    )
                }
                item(key = "detail-chapters-collapse", contentType = "chapter-collapse") {
                    TextButton(
                        onClick = { onShowAllChaptersChange(false) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.novel_show_fewer_chapters))
                    }
                }
            }

            item(key = "detail-comments-rule", contentType = "section-divider") {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) { RebuiltRule() }
            }
            item(key = "detail-comments", contentType = "comments") {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RebuiltSectionHeading(title = stringResource(R.string.comments))
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
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

/** Feature-owned detail building blocks. They intentionally consume only the
 * rebuilt token/component layer; networking and domain models stay untouched. */
@Composable
private fun RebuiltNovelHero(novel: DetailedNovel, metrics: AppAdaptiveMetrics) {
    val coverUrl = EsjzoneUrls.coverOrEmpty(novel.coverUrl)
    var viewingCover by rememberSaveable(coverUrl) { mutableStateOf(false) }
    val adultLabel = stringResource(R.string.adult_badge)
    val metadataDescription = listOfNotNull(
        novel.type.trim().takeIf(String::isNotBlank),
        novel.updatedAt?.trim()?.takeIf(String::isNotBlank)
    ).joinToString(" · ").takeIf(String::isNotBlank)
    val stats = listOfNotNull(
        novel.views.takeIf { it > 0 }?.let {
            NovelMetricModel(
                label = stringResource(R.string.novel_views_label),
                value = it.toString()
            )
        },
        novel.likes.takeIf { it > 0 }?.let {
            NovelMetricModel(
                label = stringResource(R.string.novel_likes_label),
                value = it.toString()
            )
        },
        novel.words.takeIf { it > 0 }?.let {
            NovelMetricModel(
                label = stringResource(R.string.novel_words_label),
                value = it.toString()
            )
        }
    )
    NovelHero(
        novel = com.breakyuna.esjzone.ui.product.NovelCardModel(
            id = novel.id().ifBlank { novel.url },
            title = novel.name,
            cover = NovelCoverModel(
                model = coverUrl.takeIf(String::isNotBlank)
                    ?: R.drawable.missing_cover,
                contentDescription = novel.name
            ),
            metadata = NovelMetadataModel(
                author = novel.author.trim().takeIf(String::isNotBlank),
                description = metadataDescription,
                tags = listOfNotNull(
                    adultLabel.takeIf { novel.isAdult }?.let { NovelTagModel(it) }
                ),
                metrics = stats
            )
        ),
        onCoverClick = if (coverUrl.isNotBlank()) ({ viewingCover = true }) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.prominent)
            .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)))
            .padding(AppSpacing.md)
    )
    if (viewingCover && coverUrl.isNotBlank()) {
        AppImageViewer(model = coverUrl, contentDescription = novel.name,
            onDismiss = { viewingCover = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RebuiltDetailTopBar(
    onBack: () -> Unit,
    onOpenExternal: () -> Unit,
    onMore: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(AppTouchTarget.minimum)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back)
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenExternal, modifier = Modifier.size(AppTouchTarget.minimum)) {
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.novel_open_source)
                )
            }
            IconButton(onClick = onMore, modifier = Modifier.size(AppTouchTarget.minimum)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.novel_more_actions)
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun RebuiltNovelTags(tags: List<String>, onTagClick: (String) -> Unit) {
    val visibleTags = remember(tags) {
        tags.map(String::trim).filter(String::isNotBlank).distinct()
    }
    if (visibleTags.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        visibleTags.forEach { tag ->
            key("novel-tag:$tag") {
                NovelTag(
                    tag = NovelTagModel(label = tag),
                    onClick = { onTagClick(tag) }
                )
            }
        }
    }
}

@Composable
private fun RebuiltSectionHeading(
    title: String,
    supportingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = AppTypography.titleMedium, modifier = Modifier.weight(1f))
        supportingText?.takeIf(String::isNotBlank)?.let {
            Text(it, style = AppTypography.bodySmall, color = appStateColors().contentMuted)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = AppTouchTarget.minimum)) {
                Text(actionLabel)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = actionLabel)
            }
        }
    }
}

@Composable
private fun RebuiltRule() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun RebuiltChapterRow(
    row: com.breakyuna.esjzone.novellibrary.component.VisibleChapterRow,
    currentChapter: Chapter?,
    hasHistory: Boolean,
    onChapterOpen: (Chapter) -> Unit,
    onGroupToggle: (String) -> Unit,
    isPasswordRequired: Boolean = false
) {
    when (row) {
        is VisibleChapterGroup -> Card(
            onClick = { onGroupToggle(row.key) },
            modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs),
            shape = AppShapes.standard,
            colors = CardDefaults.cardColors(containerColor = appStateColors().containerRaised)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(row.group.name.text, style = AppTypography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (row.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (row.expanded) R.string.chapter_group_collapse else R.string.chapter_group_expand
                    ),
                    tint = appStateColors().contentMuted
                )
            }
        }

        is VisibleChapterItem -> {
            val chapter = row.item as? ChapterItem
            if (chapter == null) {
                // Non-chapter source notes still use their domain renderer;
                // they are not made navigable or converted into fake chapters.
                row.item.Render(currentChapter, hasHistory, onChapterOpen)
            } else {
                val current = chapter.chapter.isHistory ||
                    (hasHistory && chapter.chapter == currentChapter)
                val isExternal = chapter.chapter.isExternal
                Card(
                    onClick = { onChapterOpen(chapter.chapter) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppSpacing.md * row.depth, top = AppSpacing.xs, bottom = AppSpacing.xs),
                    shape = AppShapes.compact,
                    colors = CardDefaults.cardColors(
                        containerColor = if (current) appStateColors().containerAccent else appStateColors().containerRaised
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        if (current) Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.chapter_current), tint = MaterialTheme.colorScheme.primary)
                        if (isPasswordRequired) {
                            Text(
                                text = stringResource(R.string.chapter_password_required_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = appAccentColors().warning,
                                modifier = Modifier
                                    .background(
                                        appAccentColors().warningContainer,
                                        shape = AppShapes.compact
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            chapter.chapter.name.ifBlank { stringResource(R.string.untitled_chapter) },
                            style = AppTypography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isExternal) Icons.Filled.OpenInNew else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (isExternal) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
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
    format: NovelExportFormat,
    selectedChapterUrls: Set<String>
) {
    withContext(Dispatchers.IO) {
        val manifest = PresentationAccess.downloads.manifest(novel.url)
            ?: error("Novel download is unavailable")
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: error("Unable to open the selected file")
        output.use { stream ->
            val loader = { record: DownloadedChapterRecord ->
                PresentationAccess.downloads.chapterContent(novel.url, record)
            }
            when (format) {
                NovelExportFormat.TXT -> NovelExporter.exportTxt(manifest, loader, stream, selectedChapterUrls)
                NovelExportFormat.EPUB -> NovelExporter.exportEpub(
                    manifest = manifest,
                    chapterLoader = loader,
                    output = stream,
                    imageLoader = { component ->
                        PresentationAccess.downloads.imageFile(novel.url, component)
                    },
                    selectedChapterUrls = selectedChapterUrls
                )
            }
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelDownloadActions(
    novel: DetailedNovel,
    authorization: Authorization,
    downloaded: DownloadedNovelManifest?,
    onDownloadedChange: (DownloadedNovelManifest?) -> Unit,
    onExportTxt: () -> Unit,
    onExportEpub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val defaultUnlockFailedText = stringResource(R.string.novel_download_unlock_failed, "")
    var showSheet by rememberSaveable(novel.url) { mutableStateOf(false) }
    var unlockingRecord by remember(novel.url) { mutableStateOf<DownloadedChapterRecord?>(null) }
    var showCommonPasswordDialog by rememberSaveable(novel.url) { mutableStateOf(false) }
    var batchUnlocking by remember(novel.url) { mutableStateOf(false) }
    var batchProgress by remember(novel.url) { mutableStateOf<Pair<Int, Int>?>(null) }
    var downloadStatus by remember(novel.url) { mutableStateOf<BackgroundDownloadStatus?>(null) }
    var downloading by remember(novel.url) { mutableStateOf(false) }
    var preflighting by remember(novel.url) { mutableStateOf(false) }
    var wenkuVerificationUrl by remember(novel.url) { mutableStateOf<String?>(null) }
    var pausing by remember(novel.url) { mutableStateOf(false) }
    var progress by remember(novel.url) { mutableStateOf<DownloadProgress?>(null) }
    var requestedWorkId by rememberSaveable(novel.url) { mutableStateOf<String?>(null) }
    var deletingDownload by remember(novel.url) { mutableStateOf(false) }
    var showDeleteDownloadDialog by rememberSaveable(novel.url) { mutableStateOf(false) }
    var showChapterSelection by rememberSaveable(novel.url) { mutableStateOf(false) }
    var selectedChapterUrls by rememberSaveable(novel.url, stateSaver = DownloadChapterSelectionSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var verificationSelection by remember(novel.url) { mutableStateOf<Set<String>?>(null) }
    val downloadScope = rememberCoroutineScope()

    LaunchedEffect(novel.url) {
        val current = withContext(Dispatchers.IO) { PresentationAccess.downloads.manifest(novel.url) }
        onDownloadedChange(current)
    }

    LaunchedEffect(novel.url, requestedWorkId) {
        NovelDownloadManager.statusFlow(context, novel.url).collect { status ->
            downloadStatus = status
            if (!preflighting) progress = status?.progress
            if (status?.running != true) {
                pausing = false
            }
            val waitingForEnqueue = requestedWorkId != null &&
                (status == null || (status.id != requestedWorkId && !status.running))
            downloading = preflighting || (status?.running == true && !pausing) || waitingForEnqueue
            // WorkManager keeps an already-running unique job when enqueue is
            // called again. In that case the returned request id can differ
            // from the job currently reported for this novel; the unique-job
            // status is still the source of truth for this page.
            if (status?.running == true && requestedWorkId != null && status.id != requestedWorkId) {
                requestedWorkId = status.id
            }
            if (status?.finished == true) {
                val updated = withContext(Dispatchers.IO) {
                    PresentationAccess.downloads.manifest(novel.url)
                }
                onDownloadedChange(updated)
                if (requestedWorkId == status.id && !status.cancelled) {
                    val pendingPasswordCount = updated?.pendingPasswordChapters?.size ?: 0
                    if (status.succeeded && pendingPasswordCount > 0) {
                        Toast.makeText(
                            context,
                            appContext.getString(R.string.novel_download_completed_with_passwords, pendingPasswordCount),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            if (status.succeeded) R.string.novel_download_success
                            else R.string.novel_download_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                if (requestedWorkId == status.id) {
                    requestedWorkId = null
                    downloading = preflighting
                }
            }
        }
    }

    fun enqueueDownload(selectedUrls: Set<String>? = null) {
        if (downloading || preflighting || novel.chapterList.orderedChapters.isEmpty()) return
        pausing = false
        val selectedKeys = selectedUrls?.map(com.breakyuna.esjzone.offline.NovelDownloadStore::chapterKey)?.toSet()
        val targetChapters = novel.chapterList.orderedChapters.filter { chapter ->
            !chapter.isExternal && (selectedKeys == null ||
                com.breakyuna.esjzone.offline.NovelDownloadStore.chapterKey(chapter.url) in selectedKeys)
        }.distinctBy { com.breakyuna.esjzone.offline.NovelDownloadStore.chapterKey(it.url) }
        if (targetChapters.isEmpty()) return
        val targetKeys = targetChapters.map { com.breakyuna.esjzone.offline.NovelDownloadStore.chapterKey(it.url) }.toSet()
        val existingCompleted = downloaded?.chapters.orEmpty().filter { record ->
            record.downloaded && com.breakyuna.esjzone.offline.NovelDownloadStore.chapterKey(record.url) in targetKeys
        }.distinctBy { com.breakyuna.esjzone.offline.NovelDownloadStore.chapterKey(it.url) }.size
        progress = DownloadProgress(
            completed = existingCompleted,
            total = targetChapters.size,
            chapterName = ""
        )
        downloading = true
        preflighting = true
        downloadScope.launch {
            var pendingWenku: com.breakyuna.esjzone.novellibrary.novel.Chapter? = null
            try {
                val completedKeys = downloaded?.chapters.orEmpty().filter { it.downloaded }
                    .map { com.breakyuna.esjzone.offline.NovelDownloadStore.chapterKey(it.url) }.toSet()
                val wenkuChapters = targetChapters.filter {
                    it.source == com.breakyuna.esjzone.novellibrary.novel.ChapterSource.WENKU8 &&
                        com.breakyuna.esjzone.offline.NovelDownloadStore.chapterKey(it.url) !in completedKeys
                }
                val prefetchSlots = Semaphore(2)
                var prefetched = 0
                val failures = coroutineScope {
                    wenkuChapters.map { chapter ->
                        async {
                            prefetchSlots.withPermit {
                                val failure = try {
                                    cancellablePageRequest {
                                        com.breakyuna.esjzone.network.EsjzoneClient.getChapterDetail(
                                            authorization, chapter, preferDownloaded = false, forceRefresh = false
                                        )
                                    }
                                    null
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    error
                                }
                                if (failure == null) prefetched++
                                progress = DownloadProgress(prefetched, wenkuChapters.size, chapter.name)
                                failure?.let { chapter to it }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                failures.firstOrNull()?.let { (chapter, error) ->
                    pendingWenku = chapter
                    throw error
                }
                requestedWorkId = NovelDownloadManager.enqueue(context, authorization, novel, selectedUrls).toString()
                preflighting = false
            } catch (error: CancellationException) {
                preflighting = false
                downloading = false
                throw error
            } catch (error: com.breakyuna.esjzone.network.external.CloudflareChallengeRequiredException) {
                preflighting = false
                downloading = false
                verificationSelection = selectedUrls
                wenkuVerificationUrl = pendingWenku?.url
            } catch (error: com.breakyuna.esjzone.network.external.WenkuCookieStoreUnavailableException) {
                preflighting = false
                downloading = false
                Toast.makeText(context, R.string.wenku_cookie_store_unavailable, Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                preflighting = false
                downloading = false
                AppLogger.e("NovelPage", "Unable to schedule background novel download", error)
                Toast.makeText(context, R.string.novel_download_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    wenkuVerificationUrl?.let { url ->
        WenkuVerificationDialog(
            url = EsjzoneUrls.resolve(url),
            acceptChapterContent = true,
            onVerified = { html ->
                val chapter = novel.chapterList.orderedChapters.firstOrNull { it.url == url }
                wenkuVerificationUrl = null
                downloadScope.launch {
                    if (html != null && chapter != null) {
                        withContext(Dispatchers.IO) {
                            com.breakyuna.esjzone.network.EsjzoneClient.importWenkuBrowserChapter(
                                chapter, EsjzoneUrls.resolve(url), html
                            )
                        }
                    }
                    enqueueDownload(verificationSelection)
                }
            },
            onUnavailable = {
                wenkuVerificationUrl = null
                Toast.makeText(context, R.string.wenku_cookie_store_unavailable, Toast.LENGTH_LONG).show()
            },
            onDismiss = { wenkuVerificationUrl = null }
        )
    }

    fun pauseDownload() {
        if (!downloading || pausing) return
        pausing = true
        downloading = false
        requestedWorkId = null
        NovelDownloadManager.cancel(context, novel.url)
        downloadScope.launch {
            val updated = withContext(Dispatchers.IO) {
                PresentationAccess.downloads.manifest(novel.url)
            }
            onDownloadedChange(updated)
        }
        Toast.makeText(context, R.string.novel_download_paused, Toast.LENGTH_SHORT).show()
    }

    fun deleteDownload() {
        if (downloading || deletingDownload || downloaded == null) return
        showDeleteDownloadDialog = false
        deletingDownload = true
        downloadScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                PresentationAccess.downloads.delete(novel.url)
            }
            onDownloadedChange(null)
            deletingDownload = false
            if (deleted) {
                Toast.makeText(
                    context,
                    context.getString(R.string.novel_download_deleted, novel.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    OutlinedButton(
        onClick = { showSheet = true },
        modifier = modifier.heightIn(min = AppTouchTarget.minimum),
        shape = AppShapes.standard,
        contentPadding = PaddingValues(horizontal = AppSpacing.sm, vertical = AppSpacing.sm)
    ) {
        Icon(
            imageVector = if (downloaded?.complete == true) Icons.Filled.DownloadDone else Icons.Filled.Download,
            contentDescription = stringResource(R.string.novel_download),
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (downloading) {
                stringResource(
                    R.string.novel_downloading_count,
                    progress?.completed ?: 0,
                    progress?.total ?: novel.chapterList.orderedChapters.size
                )
            } else {
                val downloadedCount = downloaded?.chapters?.count { it.downloaded } ?: 0
                stringResource(
                    when {
                        downloaded?.complete == true -> R.string.novel_download_update
                        downloadedCount > 0 -> R.string.novel_download_continue
                        else -> R.string.novel_download
                    }
                )
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    fun triggerBatchUnlock(password: String) {
        val trimmed = password.trim()
        if (trimmed.isBlank() || batchUnlocking) return
        batchUnlocking = true
        downloadScope.launch(Dispatchers.IO) {
            val result = PresentationAccess.downloads.unlockChaptersWithCommonPassword(
                authorization = authorization,
                novel = novel,
                commonPassword = trimmed,
                onProgress = { cur, tot -> batchProgress = Pair(cur, tot) }
            )
            withContext(Dispatchers.Main) {
                batchUnlocking = false
                batchProgress = null
                onDownloadedChange(result.updatedManifest)
                if (result.unlockedCount > 0 && result.failedCount == 0) {
                    Toast.makeText(
                        context,
                        appContext.getString(R.string.novel_download_batch_unlock_success, result.unlockedCount),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (result.unlockedCount > 0) {
                    Toast.makeText(
                        context,
                        appContext.getString(R.string.novel_download_batch_unlock_partial, result.unlockedCount, result.failedCount),
                        Toast.LENGTH_LONG
                    ).show()
                } else if (result.failedCount > 0) {
                    Toast.makeText(
                        context,
                        R.string.novel_download_batch_unlock_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun clearCommonPassword() {
        downloadScope.launch(Dispatchers.IO) {
            val updated = PresentationAccess.downloads.updateCommonPassword(novel, null)
            withContext(Dispatchers.Main) {
                onDownloadedChange(updated)
                Toast.makeText(context, R.string.novel_download_common_password_cleared, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showSheet) {
        NovelDownloadSheet(
            novel = novel,
            manifest = downloaded,
            status = downloadStatus,
            progress = progress,
            downloading = downloading,
            batchUnlocking = batchUnlocking,
            batchProgress = batchProgress,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { showSheet = false },
            onDownload = { enqueueDownload() },
            onSelectChapters = {
                selectedChapterUrls = emptySet()
                showChapterSelection = true
            },
            onPauseDownload = ::pauseDownload,
            onDeleteDownload = {
                if (!downloading && !deletingDownload && downloaded != null) {
                    showDeleteDownloadDialog = true
                }
            },
            onExportTxt = {
                showSheet = false
                onExportTxt()
            },
            onExportEpub = {
                showSheet = false
                onExportEpub()
            },
            onUnlockChapter = { unlockingRecord = it },
            onSetCommonPassword = { showCommonPasswordDialog = true },
            onBatchUnlock = {
                val common = downloaded?.commonPassword
                if (common.isNullOrBlank()) {
                    showCommonPasswordDialog = true
                } else {
                    triggerBatchUnlock(common)
                }
            },
            onClearCommonPassword = ::clearCommonPassword
        )
    }

    if (showChapterSelection) {
        DownloadChapterSelectionDialog(
            novel = novel,
            downloaded = downloaded,
            selectedUrls = selectedChapterUrls,
            onSelectionChange = { selectedChapterUrls = it },
            onDismiss = { showChapterSelection = false },
            onDownload = { selected ->
                showChapterSelection = false
                enqueueDownload(selected)
            }
        )
    }

    if (showDeleteDownloadDialog) {
        AlertDialog(
            onDismissRequest = { if (!deletingDownload) showDeleteDownloadDialog = false },
            title = { Text(stringResource(R.string.novel_download_delete_title)) },
            text = { Text(stringResource(R.string.novel_download_delete_message, novel.name)) },
            confirmButton = {
                TextButton(
                    onClick = ::deleteDownload,
                    enabled = !downloading && !deletingDownload
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDownloadDialog = false },
                    enabled = !deletingDownload
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showCommonPasswordDialog) {
        var passwordInput by rememberSaveable(novel.url) { mutableStateOf(downloaded?.commonPassword.orEmpty()) }
        AlertDialog(
            onDismissRequest = { if (!batchUnlocking) showCommonPasswordDialog = false },
            title = { Text(stringResource(R.string.novel_download_common_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.reader_password_label)) },
                        singleLine = true,
                        enabled = !batchUnlocking
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passwordInput.isNotBlank() && !batchUnlocking,
                    onClick = {
                        val submitted = passwordInput.trim()
                        showCommonPasswordDialog = false
                        triggerBatchUnlock(submitted)
                    }
                ) {
                    Text(stringResource(R.string.novel_download_save_and_unlock))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !batchUnlocking,
                    onClick = { showCommonPasswordDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (unlockingRecord != null) {
        val defaultPassword = remember(unlockingRecord) { downloaded?.commonPassword.orEmpty() }
        var passwordInput by remember(unlockingRecord) { mutableStateOf(defaultPassword) }
        var saveAsCommon by remember(unlockingRecord) { mutableStateOf(false) }
        var unlocking by remember { mutableStateOf(false) }
        var unlockError by remember { mutableStateOf<String?>(null) }
        val record = unlockingRecord!!

        AlertDialog(
            onDismissRequest = {
                if (!unlocking) unlockingRecord = null
            },
            title = { Text(record.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(stringResource(R.string.reader_password_message))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.reader_password_label)) },
                        singleLine = true,
                        enabled = !unlocking
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = !unlocking) { saveAsCommon = !saveAsCommon }
                    ) {
                        Checkbox(
                            checked = saveAsCommon,
                            onCheckedChange = { saveAsCommon = it },
                            enabled = !unlocking
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.xs))
                        Text(
                            text = stringResource(R.string.novel_download_save_as_common_password),
                            style = AppTypography.bodySmall
                        )
                    }
                    unlockError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = AppTypography.bodySmall)
                    }
                    if (unlocking) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passwordInput.isNotBlank() && !unlocking,
                    onClick = {
                        unlocking = true
                        unlockError = null
                        val submittedPassword = passwordInput.trim()
                        val shouldSaveCommon = saveAsCommon
                        downloadScope.launch(Dispatchers.IO) {
                            try {
                                val detail = PresentationAccess.client.unlockPasswordProtectedChapter(
                                    authorization,
                                    Chapter(record.name, record.url, false),
                                    submittedPassword,
                                    baseUrl = EsjzoneUrls.resolve(
                                        novel.url,
                                        EsjzoneUrls.baseForDomain(
                                            authorization.domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol }
                                        )
                                    )
                                )
                                PresentationAccess.downloads.saveChapter(
                                    novelName = novel.name,
                                    novelUrl = novel.url,
                                    coverUrl = novel.coverUrl,
                                    chapterOrder = novel.chapterList.orderedChapters,
                                    chapter = Chapter(record.name, record.url, false),
                                    detail = detail,
                                    authorization = authorization
                                )
                                if (shouldSaveCommon) {
                                    PresentationAccess.downloads.updateCommonPassword(novel, submittedPassword)
                                }
                                val updated = PresentationAccess.downloads.manifest(novel.url)
                                withContext(Dispatchers.Main) {
                                    onDownloadedChange(updated)
                                    unlockingRecord = null
                                    Toast.makeText(context, R.string.novel_download_unlock_success, Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    unlocking = false
                                    unlockError = e.message ?: defaultUnlockFailedText
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.reader_password_submit))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !unlocking,
                    onClick = { unlockingRecord = null }
                ) {
                    Text(stringResource(R.string.cancel))
                }
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
    batchUnlocking: Boolean,
    batchProgress: Pair<Int, Int>?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onSelectChapters: () -> Unit,
    onPauseDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onExportTxt: () -> Unit,
    onExportEpub: () -> Unit,
    onUnlockChapter: (DownloadedChapterRecord) -> Unit = {},
    onSetCommonPassword: () -> Unit = {},
    onBatchUnlock: () -> Unit = {},
    onClearCommonPassword: () -> Unit = {}
) {
    val completed = manifest?.chapters?.count { it.downloaded } ?: 0
    val total = novel.chapterList.orderedChapters.size
    val actualTotal = manifest?.chapters?.size?.takeIf { it > 0 } ?: total
    val metrics = rememberAppAdaptiveMetrics()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = metrics.horizontalPadding, vertical = AppSpacing.sm)
                .widthIn(max = metrics.contentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
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
                    style = AppTypography.titleMedium,
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
                style = AppTypography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.prominent,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    when {
                        downloading -> {
                            val current = progress ?: DownloadProgress(completed, actualTotal, "")
                            Text(
                                text = stringResource(
                                    if (status?.waiting == true) R.string.novel_download_waiting_count
                                    else R.string.novel_downloading_count,
                                    current.completed,
                                    current.total),
                                style = AppTypography.titleMedium,
                                color = if (status?.waiting == true) appAccentColors().warning else MaterialTheme.colorScheme.primary
                            )
                            if (current.total > 0) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = (current.completed.toFloat() / current.total.toFloat())
                                        .coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (status?.waiting == true) appAccentColors().warning else MaterialTheme.colorScheme.primary
                                )
                                current.chapterName.takeIf(String::isNotBlank)?.let {
                                    Text(
                                        text = it,
                                        style = AppTypography.labelMedium,
                                        color = appStateColors().contentMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        status?.finished == true && !status.succeeded && !status.cancelled -> {
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
                                    style = AppTypography.bodyMedium
                                )
                            }
                            if (completed > 0) {
                                Text(
                                    text = stringResource(R.string.novel_local_copy_partial, completed, actualTotal),
                                    style = AppTypography.bodyMedium,
                                    color = appStateColors().contentMuted
                                )
                            }
                        }
                        manifest?.complete == true -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppLottieState(
                                    asset = AppLottieAsset.Completion,
                                    modifier = Modifier.size(36.dp),
                                    contentDescription = stringResource(R.string.novel_local_copy_complete, completed, actualTotal)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = appAccentColors().success,
                                    modifier = Modifier.size(21.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.novel_local_copy_complete, completed, actualTotal),
                                    style = AppTypography.titleMedium
                                )
                            }
                        }
                        manifest != null && completed > 0 -> {
                            Text(
                                text = stringResource(R.string.novel_local_copy_partial, completed, actualTotal),
                                style = AppTypography.titleMedium
                            )
                            val pendingPasswords = manifest.pendingPasswordChapters
                            if (pendingPasswords.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(AppSpacing.xs))
                                Text(
                                    text = stringResource(R.string.novel_download_pending_passwords_title, pendingPasswords.size),
                                    style = AppTypography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        total == 0 -> {
                            Text(
                                text = stringResource(R.string.novel_download_no_chapters),
                                style = AppTypography.bodyMedium,
                                color = appStateColors().contentMuted
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.novel_local_copy_none),
                                style = AppTypography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.novel_download_background_note),
                                style = AppTypography.bodyMedium,
                                color = appStateColors().contentMuted
                            )
                        }
                    }
                }
            }
            val pendingPasswords = manifest?.pendingPasswordChapters.orEmpty()
            if (pendingPasswords.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.standard,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.novel_download_pending_passwords_title, pendingPasswords.size),
                                style = AppTypography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            if (batchUnlocking) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            }
                        }
                        if (batchUnlocking && batchProgress != null) {
                            Text(
                                text = stringResource(R.string.novel_download_batch_unlocking, batchProgress.first, batchProgress.second),
                                style = AppTypography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            val hasCommon = !manifest?.commonPassword.isNullOrBlank()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                            ) {
                                TextButton(
                                    onClick = onBatchUnlock,
                                    enabled = !batchUnlocking
                                ) {
                                    Text(
                                        if (hasCommon) {
                                            stringResource(R.string.novel_download_batch_unlock_action, pendingPasswords.size)
                                        } else {
                                            stringResource(R.string.novel_download_set_common_password)
                                        }
                                    )
                                }
                                if (hasCommon) {
                                    TextButton(
                                        onClick = onClearCommonPassword,
                                        enabled = !batchUnlocking
                                    ) {
                                        Text(stringResource(R.string.novel_download_clear_common_password))
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                        ) {
                            pendingPasswords.forEach { record ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = record.name,
                                        style = AppTypography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { onUnlockChapter(record) },
                                        enabled = !batchUnlocking
                                    ) {
                                        Text(stringResource(R.string.novel_download_unlock_chapter))
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (!manifest?.commonPassword.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.standard,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.novel_download_common_password_active),
                            style = AppTypography.bodySmall,
                            color = appStateColors().contentMuted
                        )
                        TextButton(onClick = onClearCommonPassword) {
                            Text(stringResource(R.string.novel_download_clear_common_password))
                        }
                    }
                }
            }
            if (downloading) {
                FilledTonalButton(
                    onClick = onPauseDownload,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = AppShapes.standard
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.novel_download_pause),
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(text = stringResource(R.string.novel_download_pause))
                }
            } else {
                OutlinedButton(
                    enabled = total > 0,
                    onClick = onSelectChapters,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) { Text(stringResource(R.string.novel_download_select_chapters)) }
                FilledTonalButton(
                    enabled = total > 0,
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = AppShapes.standard
                ) {
                    Icon(
                        imageVector = if (manifest?.complete == true) Icons.Filled.Refresh else Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = stringResource(
                            when {
                                manifest?.complete == true -> R.string.novel_download_update
                                completed > 0 -> R.string.novel_download_continue
                                else -> R.string.novel_download
                            }
                        )
                    )
                }
            }

            if (manifest != null && completed > 0 && !downloading) {
                OutlinedButton(
                    onClick = onDeleteDownload,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = AppShapes.standard,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.download_delete),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(stringResource(R.string.download_delete))
                }
            }

            if (completed > 0 && !downloading) {
                RebuiltRule()
                Text(
                    text = stringResource(R.string.novel_export_title),
                    style = AppTypography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportTxt,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = AppShapes.standard
                    ) {
                        Icon(Icons.Filled.TextSnippet, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.novel_export_txt))
                    }
                    OutlinedButton(
                        onClick = onExportEpub,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = AppShapes.standard
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
                style = AppTypography.titleMedium,
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
        InAppBrowserActivity.open(context, url)
    }.onFailure { error ->
        AppLogger.w("NovelPage", "Unable to open external URL", error)
    }
}

private fun openSystemBrowser(context: Context, rawUrl: String) {
    val url = rawUrl.trim()
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { error ->
        AppLogger.w("NovelPage", "Unable to open URL in system browser", error)
    }
}
