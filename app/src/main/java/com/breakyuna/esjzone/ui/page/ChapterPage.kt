package com.breakyuna.esjzone.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.LocalReadingHistoryRecorder
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.reader.ReaderBackground
import com.breakyuna.esjzone.ui.reader.ReaderFont
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderScriptConverter
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.reader.ReaderSettingsStore
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.util.AppLogger

class ChapterPage(
    private val novelId: String,
    private val chapter: Chapter,
    private val history: ChapterStateHolder,
    private val chapterOrder: List<Chapter> = emptyList(),
    private val novelName: String = "",
    private val novelUrl: String = "",
    private val novelCoverUrl: String = ""
) : Screen {

    override val key: ScreenKey =
        "ChapterPage:" +
            novelId.trim().ifBlank { chapter.novelId() } +
            ":" +
            chapterIdentity(chapter)

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current

        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val historyState = history.state()

        var readerSettings by remember(context) {
            mutableStateOf(ReaderSettingsStore.load(context))
        }
        var showReaderSettings by rememberSaveable {
            mutableStateOf(false)
        }
        var showReaderContents by rememberSaveable {
            mutableStateOf(false)
        }
        var isBookmarked by rememberSaveable { mutableStateOf(false) }

        val readerTextStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = readerSettings.font.family,
            fontSize = readerSettings.fontSizeSp.sp,
            lineHeight = readerSettings.lineHeightSp.sp,
            letterSpacing = readerSettings.letterSpacingSp.sp
        )
        val readerContentColor = readerSettings.background.contentColor()

        fun updateReaderSettings(settings: ReaderSettings) {
            readerSettings = settings
        }

        // Coalesce rapid slider updates into one preference write after the
        // user pauses dragging, rather than calling SharedPreferences.apply()
        // for every pointer event.
        LaunchedEffect(readerSettings) {
            delay(250)
            ReaderSettingsStore.save(context, readerSettings)
        }

        val requestedChapter = rememberSaveable {
            mutableStateOf(chapter)
        }

        val chapterPageModel =
            rememberScreenModel {
                ChapterPageModel(
                    authorization = authorization,
                    requestedChapter = requestedChapter,
                    novelId = novelId,
                    chapterOrder = chapterOrder
                )
            }
        val state by chapterPageModel.state.collectAsState()

        var showToolbar by rememberSaveable {
            mutableStateOf(false)
        }

        // Keep one stable list state for the entire reading session.  Chapter
        // items use stable URL keys below, so adding a chapter before the
        // current item no longer requires manually summing measured heights.
        val scrollState = rememberLazyListState()

        var progressPreview by remember { mutableStateOf<ReaderBookLocation?>(null) }
        var progressReturnLocation by remember { mutableStateOf<ReaderBookLocation?>(null) }
        var pendingSeekLocation by remember { mutableStateOf<ReaderBookLocation?>(null) }
        var isBookProgressDragging by remember { mutableStateOf(false) }
        var isProgrammaticScroll by remember { mutableStateOf(false) }

        fun dismissProgressPreview() {
            progressPreview = null
            progressReturnLocation = null
        }

        BackHandler(enabled = navigator != null) {
            when {
                showReaderSettings -> showReaderSettings = false
                showReaderContents -> showReaderContents = false
                progressPreview != null -> dismissProgressPreview()
                else -> navigator?.pop()
            }
        }

        val continuousLoadThreshold = with(LocalDensity.current) { 720.dp.toPx().toInt() }
        val previousLoadThreshold = with(LocalDensity.current) { 240.dp.toPx().toInt() }
        val chapterActivationOffset = with(density) { 56.dp.toPx().roundToInt() }
        val result = state as? ChapterPageModel.State.Result
        val readerTextTransform: (String) -> String = remember(readerSettings.script) {
            { text -> ReaderScriptConverter.convert(text, readerSettings.script) }
        }
        var retainedActiveChapterKey by rememberSaveable {
            mutableStateOf(chapterIdentity(chapter))
        }
        val visibleActiveChapterKey by remember(result, scrollState, chapterActivationOffset) {
            derivedStateOf {
                val chapters = result?.chapters.orEmpty()
                val visibleChapters = scrollState.layoutInfo.visibleItemsInfo
                    .mapNotNull { item ->
                        val itemKey = item.key as? String ?: return@mapNotNull null
                        val chapterIndex = chapters.indexOfFirst {
                            chapterIdentity(it.chapter) == itemKey
                        }
                        chapterIndex.takeIf { it >= 0 }?.let { it to item.offset }
                    }
                visibleChapters
                    .lastOrNull { it.second <= chapterActivationOffset }
                    ?.first
                    ?.let { chapters.getOrNull(it)?.chapter?.let(::chapterIdentity) }
                    ?: visibleChapters.firstOrNull()?.first
                        ?.let { chapters.getOrNull(it)?.chapter?.let(::chapterIdentity) }
            }
        }
        LaunchedEffect(requestedChapter.value.url) {
            retainedActiveChapterKey = chapterIdentity(requestedChapter.value)
        }
        LaunchedEffect(visibleActiveChapterKey, result?.chapters) {
            val key = visibleActiveChapterKey ?: return@LaunchedEffect
            if (result?.chapters?.any { chapterIdentity(it.chapter) == key } == true) {
                retainedActiveChapterKey = key
            }
        }
        val activeChapter = result?.chapters?.firstOrNull {
            chapterIdentity(it.chapter) == retainedActiveChapterKey
        }
        val activeChapterItem by remember(result, activeChapter, scrollState) {
            derivedStateOf {
                val activeKey = activeChapter?.chapter?.let(::chapterIdentity)
                scrollState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == activeKey }
            }
        }
        val bookmarkChapter = activeChapter?.chapter ?: requestedChapter.value
        val bookmarkChapterUrl = remember(bookmarkChapter.url) {
            EsjzoneUrls.canonicalPageKey(bookmarkChapter.url)
                .ifBlank { bookmarkChapter.url.trim() }
        }
        LaunchedEffect(bookmarkChapterUrl) {
            isBookmarked = withContext(Dispatchers.IO) {
                runCatching {
                    MainActivity.database.bookmarkDao().findByChapterUrl(bookmarkChapterUrl) != null
                }.getOrElse { error ->
                    AppLogger.w("ChapterPage", "Failed to load local bookmark state", error)
                    false
                }
            }
        }

        fun toggleBookmark() {
            val target = bookmarkChapter
            if (bookmarkChapterUrl.isBlank()) return
            val wasBookmarked = isBookmarked
            isBookmarked = !wasBookmarked
            scope.launch(Dispatchers.IO) {
                try {
                    val dao = MainActivity.database.bookmarkDao()
                    if (wasBookmarked) {
                        dao.deleteByChapterUrl(bookmarkChapterUrl)
                    } else {
                        dao.insert(
                            com.breakyuna.esjzone.database.entity.Bookmark(
                                chapterUrl = bookmarkChapterUrl,
                                novelId = novelId.ifBlank { target.novelId() },
                                novelName = novelName
                                    .ifBlank { novelId }
                                    .ifBlank { target.novelId() }
                                    .ifBlank { target.name },
                                chapterName = target.name
                            )
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) {
                        isBookmarked = wasBookmarked
                    }
                    AppLogger.e("ChapterPage", "Failed to update local bookmark", error)
                }
            }
        }

        val measuredChapterProgress = chapterProgressFor(
            itemOffset = activeChapterItem?.offset,
            itemSize = activeChapterItem?.size
        )
        val bookChapterOrder = result?.chapterOrder.orEmpty()
            .ifEmpty { chapterOrder }
            .ifEmpty {
                if (novelId.isBlank()) {
                    result?.chapters?.map { it.chapter }.orEmpty()
                } else {
                    emptyList()
                }
            }
        val measuredBookLocation = activeChapter?.chapter
            ?.takeIf { measuredChapterProgress != null }
            ?.let {
                readerBookLocationFor(
                    activeChapter = it,
                    chapterProgress = measuredChapterProgress ?: 0f,
                    chapterOrder = bookChapterOrder
                )
            }
        var retainedBookLocation by remember(requestedChapter.value.url) {
            mutableStateOf<ReaderBookLocation?>(null)
        }
        LaunchedEffect(measuredBookLocation) {
            measuredBookLocation?.let { retainedBookLocation = it }
        }
        val currentBookLocation = measuredBookLocation ?: retainedBookLocation
        // Keep the last measured chapter as the UI/history anchor while a
        // list update briefly leaves no matching visible item.
        val currentReadingChapter = currentBookLocation?.chapter
            ?: activeChapter?.chapter
            ?: requestedChapter.value
        val visibleReaderChapterKeys by remember(result, scrollState) {
            derivedStateOf {
                val loadedKeys = result?.chapters
                    .orEmpty()
                    .map { chapterIdentity(it.chapter) }
                    .toSet()
                scrollState.layoutInfo.visibleItemsInfo
                    .mapNotNull { it.key as? String }
                    .filter { it in loadedKeys }
                    .toSet()
            }
        }
        LaunchedEffect(
            visibleReaderChapterKeys,
            activeChapter?.chapter?.url,
            result?.chapters
        ) {
            chapterPageModel.updateWindowAnchor(
                ReaderWindowAnchor(
                    visibleChapterKeys = visibleReaderChapterKeys,
                    activeChapterKey = activeChapter?.chapter?.let(::chapterIdentity),
                    layoutReady = result != null && visibleReaderChapterKeys.isNotEmpty()
                )
            )
        }
        val localHistoryActivityId = remember(novelId, novelUrl, chapter.url) {
            localReadingHistoryKey(
                novelId = novelId.ifBlank { chapter.novelId() },
                novelUrl = novelUrl,
                chapterUrl = chapter.url
            )
        }
        val localHistoryStartedAt = remember { System.currentTimeMillis() }
        val localHistoryPosition = rememberUpdatedState(
            LocalReadingPosition(
                novelId = novelId.ifBlank {
                    currentReadingChapter.novelId()
                },
                novelName = novelName.ifBlank {
                    novelId.ifBlank {
                        currentReadingChapter.novelId()
                    }.ifBlank { currentReadingChapter.name }
                },
                novelUrl = novelUrl.ifBlank {
                    novelId.ifBlank {
                        currentReadingChapter.novelId()
                    }.takeIf { it.isNotBlank() }?.let { id ->
                        EsjzoneUrls.resolve("/detail/$id.html")
                    }.orEmpty()
                },
                novelCoverUrl = EsjzoneUrls.coverOrEmpty(novelCoverUrl),
                chapterUrl = currentReadingChapter.url,
                chapterName = currentReadingChapter.name,
                chapterIndex = currentBookLocation?.chapterIndex ?: -1,
                totalChapters = currentBookLocation?.totalChapters ?: bookChapterOrder.size,
                chapterProgress = currentBookLocation?.chapterProgress ?: 0f
            )
        )
        LaunchedEffect(localHistoryActivityId) {
            snapshotFlow { localHistoryPosition.value }
                .distinctUntilChanged()
                .debounce(750)
                .collect { position ->
                    LocalReadingHistoryRecorder.upsert(
                        position.toLocalReadingActivity(
                            activityId = localHistoryActivityId,
                            startedAt = localHistoryStartedAt
                        )
                    )
                }
        }
        DisposableEffect(localHistoryActivityId) {
            onDispose {
                LocalReadingHistoryRecorder.upsert(
                    localHistoryPosition.value.toLocalReadingActivity(
                        activityId = localHistoryActivityId,
                        startedAt = localHistoryStartedAt
                    )
                )
            }
        }
        val displayedBookProgress = if (isBookProgressDragging) {
            progressPreview?.bookProgress ?: currentBookLocation?.bookProgress ?: 0f
        } else {
            currentBookLocation?.bookProgress ?: 0f
        }
        val currentChapterName = currentReadingChapter.name

        fun seekTo(location: ReaderBookLocation) {
            pendingSeekLocation = location
            val current = currentReadingChapter
            if (!sameReaderChapter(current, location.chapter)) {
                chapterPageModel.openChapter(location.chapter)
            }
        }

        // Progress gestures are shared by the compact reading rail and the
        // expanded controls. Keeping the callbacks here guarantees that both
        // surfaces capture the same active chapter/fraction snapshot.
        fun beginBookProgressPreview(progress: Float) {
            progressReturnLocation = currentBookLocation
            isBookProgressDragging = true
            progressPreview = readerBookLocationFor(
                bookProgress = progress,
                chapterOrder = bookChapterOrder
            )
        }

        fun updateBookProgressPreview(progress: Float) {
            progressPreview = readerBookLocationFor(
                bookProgress = progress,
                chapterOrder = bookChapterOrder
            )
        }

        fun finishBookProgressPreview() {
            isBookProgressDragging = false
            progressPreview?.let(::seekTo)
        }

        var previousBootstrapFor by remember { mutableStateOf<String?>(null) }

        var previousRequestedChapterUrl by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(requestedChapter.value.url) {
            val currentRequestedChapterUrl = requestedChapter.value.url
            val oldRequestedChapterUrl = previousRequestedChapterUrl
            previousRequestedChapterUrl = currentRequestedChapterUrl
            // The first run may be restoring a saved ScrollState after a
            // background process recreation; keep that position intact.
            if (
                oldRequestedChapterUrl == null ||
                oldRequestedChapterUrl == currentRequestedChapterUrl
            ) {
                return@LaunchedEffect
            }
            isProgrammaticScroll = true
            try {
                scrollState.scrollToItem(0)
            } finally {
                isProgrammaticScroll = false
            }
        }

        // Prime one previous chapter per requested chapter.  LazyColumn's
        // stable chapter keys preserve the current item's anchor when this
        // item is prepended, so this no longer needs manual height correction.
        LaunchedEffect(state is ChapterPageModel.State.Result, requestedChapter.value.url) {
            if (
                state is ChapterPageModel.State.Result &&
                previousBootstrapFor != requestedChapter.value.url
            ) {
                previousBootstrapFor = requestedChapter.value.url
                chapterPageModel.loadPreviousChapter()
            }
        }

        LaunchedEffect(pendingSeekLocation?.chapter?.url, result?.chapters) {
            val target = pendingSeekLocation ?: return@LaunchedEffect
            val targetIndex = result?.chapters?.indexOfFirst {
                sameReaderChapter(it.chapter, target.chapter)
            } ?: -1
            if (targetIndex < 0) return@LaunchedEffect
            val targetKey = chapterIdentity(target.chapter)

            isProgrammaticScroll = true
            try {
                scrollState.scrollToItem(targetIndex)
                if (target.chapterProgress > 0f) {
                    val itemSize = snapshotFlow {
                        scrollState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == targetKey }
                            ?.let { it.index to it.size }
                    }.first { layout -> layout?.second?.let { it > 0 } == true }
                    if (itemSize != null) {
                        scrollState.scrollToItem(
                            itemSize.first,
                            (itemSize.second * target.chapterProgress)
                                .roundToInt()
                                .coerceAtLeast(0)
                        )
                    }
                }
            } finally {
                isProgrammaticScroll = false
            }
            pendingSeekLocation = null
        }

        fun openTargetChapter(target: Chapter) {
            pendingSeekLocation = null
            chapterPageModel.openChapter(target)
            scope.launch(Dispatchers.Main) {
                isProgrammaticScroll = true
                try {
                    scrollState.scrollToItem(0)
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        if (progressPreview != null) {
                            dismissProgressPreview()
                        } else {
                            showToolbar = !showToolbar
                        }
                    },
                color = readerSettings.background.containerColor()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .widthIn(max = QuietEditorial.contentMaxWidth)
                            .align(Alignment.Center)
                            .padding(horizontal = readerSettings.horizontalPaddingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(readerSettings.pageSpacingDp.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            // Reserve a fixed, quiet header area. The header
                            // is an overlay and never changes list geometry.
                            top = 52.dp,
                            bottom = 80.dp
                        )
                    ) {
                    when (state) {
                        is ChapterPageModel.State.Loading -> item(key = "reader-loading") {
                            Column {
                                ChapterHeading(
                                    currentChapterName,
                                    readerSettings,
                                    readerTextTransform
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.5.dp)
                                }
                            }
                        }

                        is ChapterPageModel.State.Error -> item(key = "reader-error") {
                            Column {
                                ChapterHeading(
                                    currentChapterName,
                                    readerSettings,
                                    readerTextTransform
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(
                                            if ((state as ChapterPageModel.State.Error).failure == com.breakyuna.esjzone.network.LoadFailureKind.NETWORK) {
                                                R.string.load_network_error
                                            } else {
                                                R.string.load_client_error
                                            }
                                        ),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        is ChapterPageModel.State.Result -> {
                            val readerResult = state as ChapterPageModel.State.Result
                            items(
                                items = readerResult.chapters,
                                key = { entry -> chapterIdentity(entry.chapter) }
                            ) { entry ->
                                ReaderChapterBlock(
                                    entry = entry,
                                    textMeasurer = textMeasurer,
                                    textStyle = readerTextStyle,
                                    density = density,
                                    settings = readerSettings,
                                    contentColor = readerContentColor,
                                    textTransform = readerTextTransform
                                )
                            }

                            if (readerResult.isLoadingNext) {
                                item(key = "reader-loading-next") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(strokeWidth = 2.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

            ReaderStatusBar(
                novelName = novelName,
                chapterName = currentChapterName,
                chapterIndex = currentBookLocation?.chapterIndex ?: -1,
                totalChapters = currentBookLocation?.totalChapters ?: bookChapterOrder.size
            )

            if ((state as? ChapterPageModel.State.Result)?.isLoadingPrevious == true) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 42.dp),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = stringResource(id = R.string.reader_loading_previous),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // A quiet rail remains available in immersive mode. It gives the
            // reader orientation without turning the transient controls into
            // part of the scrollable content or changing its anchor.
            if (
                !showToolbar &&
                !showReaderContents &&
                !showReaderSettings &&
                progressPreview == null &&
                state is ChapterPageModel.State.Result &&
                bookChapterOrder.isNotEmpty()
            ) {
                ReaderMiniProgress(
                    progress = displayedBookProgress,
                    chapterIndex = currentBookLocation?.chapterIndex ?: -1,
                    totalChapters = currentBookLocation?.totalChapters ?: bookChapterOrder.size,
                    enabled = bookChapterOrder.isNotEmpty(),
                    onDragStart = ::beginBookProgressPreview,
                    onDrag = ::updateBookProgressPreview,
                    onDragFinished = ::finishBookProgressPreview,
                    onDragCancelled = ::finishBookProgressPreview,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, bottom = 14.dp)
                        .zIndex(2f)
                )
            }

            AnimatedVisibility(
                visible = progressPreview != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 150.dp)
                    .zIndex(3f)
            ) {
                progressPreview?.let { preview ->
                    ReaderProgressPreview(
                        location = preview,
                        origin = progressReturnLocation,
                        canReturn = progressReturnLocation != null,
                        onReturn = {
                            progressReturnLocation?.let { location ->
                                seekTo(location)
                                progressPreview = location
                                // Keep the preview until a later screen tap.
                                progressReturnLocation = null
                            }
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = showToolbar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 12.dp, end = 16.dp)
                        ) {
                            if (state is ChapterPageModel.State.Result) {
                                val readerResult = state as ChapterPageModel.State.Result
                                val navigationChapter = currentReadingChapter
                                val navigationIndex = bookChapterOrder.indexOfFirst {
                                    sameReaderChapter(it, navigationChapter)
                                }
                                val activePrevious = if (navigationIndex > 0) {
                                    bookChapterOrder.getOrNull(navigationIndex - 1)
                                } else {
                                    readerResult.previous
                                }
                                val activeNext = if (navigationIndex >= 0) {
                                    bookChapterOrder.getOrNull(navigationIndex + 1)
                                } else {
                                    null
                                } ?: readerResult.next

                                if (bookChapterOrder.isNotEmpty()) {
                                    ReaderBookProgressBar(
                                        progress = displayedBookProgress,
                                        enabled = true,
                                        previousEnabled = activePrevious != null,
                                        nextEnabled = activeNext != null,
                                        onPrevious = {
                                            activePrevious?.let { previous ->
                                                if (novelId == previous.novelId()) {
                                                    historyState.value = previous
                                                }
                                                dismissProgressPreview()
                                                openTargetChapter(previous)
                                            }
                                        },
                                        onNext = {
                                            activeNext?.let { next ->
                                                if (novelId == next.novelId()) {
                                                    historyState.value = next
                                                }
                                                dismissProgressPreview()
                                                openTargetChapter(next)
                                            }
                                        },
                                        onDragStart = ::beginBookProgressPreview,
                                        onDrag = ::updateBookProgressPreview,
                                        onDragFinished = ::finishBookProgressPreview,
                                        onDragCancelled = ::finishBookProgressPreview
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.reader_progress_unavailable),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            val commentChapter = currentReadingChapter
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ReaderToolButton(
                                    label = stringResource(R.string.reader_contents_action),
                                    contentDescription = stringResource(R.string.reader_contents),
                                    icon = Icons.Filled.List,
                                    onClick = {
                                        dismissProgressPreview()
                                        showReaderSettings = false
                                        showReaderContents = true
                                    }
                                )
                                ReaderToolButton(
                                    label = stringResource(R.string.reader_settings_action),
                                    contentDescription = stringResource(R.string.reader_settings),
                                    icon = Icons.Filled.Settings,
                                    onClick = {
                                        dismissProgressPreview()
                                        showReaderContents = false
                                        showReaderSettings = true
                                    }
                                )
                                ReaderToolButton(
                                    label = stringResource(R.string.reader_bookmark_action),
                                    contentDescription = stringResource(
                                        if (isBookmarked) R.string.reader_remove_bookmark
                                        else R.string.reader_add_bookmark
                                    ),
                                    icon = if (isBookmarked) {
                                        Icons.Filled.Bookmark
                                    } else {
                                        Icons.Filled.BookmarkBorder
                                    },
                                    enabled = state is ChapterPageModel.State.Result,
                                    onClick = {
                                        dismissProgressPreview()
                                        toggleBookmark()
                                    }
                                )
                                ReaderToolButton(
                                    label = stringResource(R.string.reader_comments_action),
                                    contentDescription = stringResource(R.string.comments),
                                    icon = Icons.Filled.Forum,
                                    enabled = state is ChapterPageModel.State.Result,
                                    onClick = {
                                        dismissProgressPreview()
                                        navigator?.pushIfNotCurrent(
                                            ChapterCommentsPage(
                                                chapterName = commentChapter.name,
                                                chapterUrl = commentChapter.url
                                            )
                                        )
                                    }
                                )
                                val detailUrl = novelUrl.ifBlank {
                                    novelId.ifBlank { commentChapter.novelId() }
                                        .takeIf { it.isNotBlank() }
                                        ?.let { id -> EsjzoneUrls.resolve("/detail/$id.html") }
                                        .orEmpty()
                                }
                                ReaderToolButton(
                                    label = stringResource(R.string.reader_details_action),
                                    contentDescription = stringResource(
                                        R.string.reader_open_novel_detail
                                    ),
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    enabled = detailUrl.isNotBlank(),
                                    onClick = {
                                        dismissProgressPreview()
                                        navigator?.pushIfNotCurrent(
                                            NovelPage(
                                                novel = FavoriteNovel(
                                                    name = novelName.ifBlank {
                                                        novelId.ifBlank { commentChapter.name }
                                                    },
                                                    url = detailUrl
                                                ),
                                                history = history
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showToolbar,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 4.dp)
                    .zIndex(2f)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { navigator?.pop() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.reader_back)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = novelName.ifBlank { stringResource(R.string.reader_contents) },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentChapterName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp).size(22.dp)
                        )
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            chapterPageModel.getDetail()
        }

        val loadedReaderChapterKeys = result?.chapters
            .orEmpty()
            .map { chapterIdentity(it.chapter) }
        LaunchedEffect(
            scrollState,
            chapterPageModel,
            continuousLoadThreshold,
            previousLoadThreshold,
            loadedReaderChapterKeys
        ) {
            var previousSnapshot: ReaderScrollSnapshot? = null
            snapshotFlow {
                val loadedKeys = loadedReaderChapterKeys.toSet()
                val visibleChapterItems = scrollState.layoutInfo.visibleItemsInfo
                    .filter { (it.key as? String) in loadedKeys }
                val layoutMatchesLoadedWindow = visibleChapterItems.all { item ->
                    loadedReaderChapterKeys.getOrNull(item.index) == item.key
                }
                ReaderScrollSnapshot(
                    firstVisibleIndex = scrollState.firstVisibleItemIndex,
                    firstVisibleOffset = scrollState.firstVisibleItemScrollOffset,
                    firstVisibleChapterKey = visibleChapterItems.firstOrNull()
                        ?.key as? String,
                    lastVisibleChapterKey = visibleChapterItems.lastOrNull()
                        ?.key as? String,
                    distanceToLoadedTail = visibleChapterItems.lastOrNull()?.let { item ->
                        (
                            item.offset + item.size - scrollState.layoutInfo.viewportEndOffset
                        ).coerceAtLeast(0)
                    } ?: Int.MAX_VALUE,
                    loadedChapterKeys = loadedReaderChapterKeys,
                    layoutMatchesLoadedWindow = layoutMatchesLoadedWindow,
                    isScrollInProgress = scrollState.isScrollInProgress,
                    isProgrammaticScroll = isProgrammaticScroll
                )
            }.collect { snapshot ->
                if (shouldLoadPreviousChapter(
                        previous = previousSnapshot,
                        current = snapshot,
                        threshold = previousLoadThreshold
                    )
                ) {
                    chapterPageModel.loadPreviousChapter()
                }
                if (shouldLoadNextChapter(
                        previous = previousSnapshot,
                        current = snapshot,
                        threshold = continuousLoadThreshold
                    )
                ) {
                    chapterPageModel.loadNextChapter()
                }
                previousSnapshot = snapshot.takeIf { it.layoutMatchesLoadedWindow }
            }
        }

        LaunchedEffect(activeChapter?.chapter?.url) {
            activeChapter?.chapter?.let { current ->
                if (novelId == current.novelId()) {
                    historyState.value = current
                }
            }
        }

        val readerChapters = result?.chapterOrder.orEmpty()
            .ifEmpty { chapterOrder }
            .ifEmpty { result?.chapters?.map { it.chapter }.orEmpty() }
        ReaderContentsDrawer(
            visible = showReaderContents,
            chapters = readerChapters,
            currentChapter = currentReadingChapter,
            onChapterSelected = { selectedChapter ->
                showReaderContents = false
                dismissProgressPreview()
                openTargetChapter(selectedChapter)
            },
            onDismiss = { showReaderContents = false }
        )
        ReaderSettingsDrawer(
            visible = showReaderSettings,
            settings = readerSettings,
            previewText = activeChapter?.detail?.content
                ?.filterIsInstance<TextComponent>()
                ?.firstOrNull()
                ?.text
                ?.let(readerTextTransform)
                .orEmpty(),
            onSettingsChange = { updated -> updateReaderSettings(updated) },
            onDismiss = { showReaderSettings = false }
        )
    }

}

private data class ReaderBookLocation(
    val chapter: Chapter,
    val chapterIndex: Int,
    val chapterProgress: Float,
    val totalChapters: Int
) {
    val bookProgress: Float
        get() = if (totalChapters <= 0) {
            0f
        } else {
            ((chapterIndex + chapterProgress.coerceIn(0f, 1f)) / totalChapters.toFloat())
                .coerceIn(0f, 1f)
        }
}

private data class LocalReadingPosition(
    val novelId: String,
    val novelName: String,
    val novelUrl: String,
    val novelCoverUrl: String,
    val chapterUrl: String,
    val chapterName: String,
    val chapterIndex: Int,
    val totalChapters: Int,
    val chapterProgress: Float
)

private fun LocalReadingPosition.toLocalReadingActivity(
    activityId: String,
    startedAt: Long,
    now: Long = System.currentTimeMillis()
): LocalReadingActivity = LocalReadingActivity(
    activityId = activityId,
    novelId = novelId,
    novelName = novelName,
    novelUrl = novelUrl,
    novelCoverUrl = novelCoverUrl,
    chapterUrl = chapterUrl,
    chapterName = chapterName,
    chapterIndex = chapterIndex,
    totalChapters = totalChapters,
    chapterProgress = chapterProgress.coerceIn(0f, 1f),
    startedAt = startedAt,
    lastReadAt = now,
    durationMs = (now - startedAt).coerceAtLeast(0L)
)

private fun readerBookLocationFor(
    activeChapter: Chapter,
    chapterProgress: Float,
    chapterOrder: List<Chapter>
): ReaderBookLocation? {
    val index = chapterOrder.indexOfFirst { sameReaderChapter(it, activeChapter) }
    if (index < 0) return null
    return ReaderBookLocation(
        chapter = chapterOrder[index],
        chapterIndex = index,
        chapterProgress = chapterProgress.coerceIn(0f, 1f),
        totalChapters = chapterOrder.size
    )
}

private fun readerBookLocationFor(
    bookProgress: Float,
    chapterOrder: List<Chapter>
): ReaderBookLocation? {
    if (chapterOrder.isEmpty()) return null
    val clampedProgress = bookProgress.coerceIn(0f, 1f)
    val scaledProgress = clampedProgress * chapterOrder.size
    val index = if (clampedProgress >= 1f) {
        chapterOrder.lastIndex
    } else {
        scaledProgress.toInt().coerceIn(0, chapterOrder.lastIndex)
    }
    val chapterProgress = if (clampedProgress >= 1f) {
        1f
    } else {
        scaledProgress - index
    }
    return ReaderBookLocation(
        chapter = chapterOrder[index],
        chapterIndex = index,
        chapterProgress = chapterProgress,
        totalChapters = chapterOrder.size
    )
}

@Composable
private fun ReaderStatusBar(
    novelName: String,
    chapterName: String,
    chapterIndex: Int,
    totalChapters: Int
) {
    val title = listOf(novelName.trim(), chapterName.trim())
        .filter(String::isNotBlank)
        .joinToString(" · ")
    val statusColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .zIndex(1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = statusColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (chapterIndex >= 0 && totalChapters > 0) {
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(
                    id = R.string.reader_progress_chapter_count,
                    chapterIndex + 1,
                    totalChapters
                ),
                color = statusColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ReaderProgressPreview(
    location: ReaderBookLocation,
    origin: ReaderBookLocation?,
    canReturn: Boolean,
    onReturn: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 44.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 0.dp,
        tonalElevation = 1.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 18.dp, top = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = location.chapter.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            id = R.string.reader_progress_chapter_count,
                            location.chapterIndex + 1,
                            location.totalChapters
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            id = R.string.reader_preview_percent,
                            (location.chapterProgress * 100f).roundToInt()
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    origin?.let { captured ->
                        Text(
                            text = stringResource(
                                R.string.reader_preview_origin,
                                stringResource(
                                    R.string.reader_progress_chapter_count,
                                    captured.chapterIndex + 1,
                                    captured.totalChapters
                                )
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (canReturn) {
                    TextButton(onClick = onReturn) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.reader_preview_return),
                            modifier = Modifier.padding(start = 4.dp),
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.reader_preview_dismiss_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.68f),
                modifier = Modifier.padding(start = 18.dp, bottom = 10.dp)
            )
        }
    }
}

private fun localReadingHistoryKey(
    novelId: String,
    novelUrl: String,
    chapterUrl: String
): String {
    val stableNovel = novelId.trim().ifBlank {
        EsjzoneUrls.canonicalPageKey(novelUrl).ifBlank {
            val chapterNovelId = Chapter("", chapterUrl, false).novelId()
            chapterNovelId.ifBlank { EsjzoneUrls.canonicalPageKey(chapterUrl) }
        }
    }
    return "novel:$stableNovel"
}

@Composable
private fun ReaderBookProgressBar(
    progress: Float,
    enabled: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragFinished: () -> Unit,
    onDragCancelled: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)
    val currentOnDragCancelled by rememberUpdatedState(onDragCancelled)
    val trackInset = with(LocalDensity.current) { 10.dp.toPx() }
    val previousDescription = stringResource(id = R.string.previous_chapter)
    val nextDescription = stringResource(id = R.string.next_chapter)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            modifier = Modifier.semantics { contentDescription = previousDescription },
            enabled = previousEnabled,
            onClick = onPrevious
        ) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .pointerInput(enabled, trackInset) {
                    if (!enabled) return@pointerInput

                    fun progressAt(x: Float): Float {
                        val usableWidth = max(size.width.toFloat() - trackInset * 2f, 1f)
                        return ((x - trackInset) / usableWidth).coerceIn(0f, 1f)
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset -> currentOnDragStart(progressAt(offset.x)) },
                        onDrag = { change, _ ->
                            change.consume()
                            currentOnDrag(progressAt(change.position.x))
                        },
                        onDragEnd = currentOnDragFinished,
                        onDragCancel = currentOnDragCancelled
                    )
                }
        ) {
            val centerY = size.height / 2f
            val startX = trackInset
            val endX = max(size.width - trackInset, startX)
            val thumbX = startX + (endX - startX) * progress.coerceIn(0f, 1f)
            val trackColor = if (enabled) inactiveColor else disabledColor
            val progressColor = if (enabled) activeColor else disabledColor

            drawLine(
                color = trackColor,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = progressColor,
                start = Offset(startX, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = progressColor,
                radius = 9.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
        }

        IconButton(
            modifier = Modifier.semantics { contentDescription = nextDescription },
            enabled = nextEnabled,
            onClick = onNext
        ) {
            Text(
                text = ">",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ChapterHeading(
    name: String,
    settings: ReaderSettings,
    textTransform: (String) -> String = { it }
) {
    Text(
        text = textTransform(name),
        style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = settings.font.family,
            fontSize = (settings.fontSizeSp + 6f).sp,
            lineHeight = (settings.lineHeightSp + 6f).sp,
            letterSpacing = settings.letterSpacingSp.sp
        ),
        color = settings.background.contentColor(),
        modifier = Modifier.padding(bottom = (settings.paragraphSpacingDp + 8f).dp)
    )
}

@Composable
private fun ReaderMiniProgress(
    progress: Float,
    chapterIndex: Int,
    totalChapters: Int,
    enabled: Boolean,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragFinished: () -> Unit,
    onDragCancelled: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = QuietEditorial.contentMaxWidth),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (chapterIndex >= 0 && totalChapters > 0) {
                        stringResource(
                            R.string.reader_progress_chapter_count,
                            chapterIndex + 1,
                            totalChapters
                        )
                    } else {
                        stringResource(R.string.reader_contents)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        R.string.reader_book_progress_percent,
                        (progress.coerceIn(0f, 1f) * 100f).roundToInt()
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            ReaderScrubber(
                progress = progress,
                enabled = enabled,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragFinished = onDragFinished,
                onDragCancelled = onDragCancelled
            )
        }
    }
}

@Composable
private fun ReaderScrubber(
    progress: Float,
    enabled: Boolean,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragFinished: () -> Unit,
    onDragCancelled: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)
    val currentOnDragCancelled by rememberUpdatedState(onDragCancelled)
    val trackInset = with(LocalDensity.current) { 9.dp.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            // Keep the long-press target at the minimum touch size while the
            // visual rail itself remains the specified 6dp/9dp treatment.
            .height(48.dp)
            .pointerInput(enabled, trackInset) {
                if (!enabled) return@pointerInput
                fun progressAt(x: Float): Float {
                    val usableWidth = max(size.width.toFloat() - trackInset * 2f, 1f)
                    return ((x - trackInset) / usableWidth).coerceIn(0f, 1f)
                }
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> currentOnDragStart(progressAt(offset.x)) },
                    onDrag = { change, _ ->
                        change.consume()
                        currentOnDrag(progressAt(change.position.x))
                    },
                    onDragEnd = currentOnDragFinished,
                    onDragCancel = currentOnDragCancelled
                )
            }
    ) {
        val centerY = size.height / 2f
        val startX = trackInset
        val endX = max(size.width - trackInset, startX)
        val thumbX = startX + (endX - startX) * progress.coerceIn(0f, 1f)
        val trackColor = if (enabled) inactiveColor else disabledColor
        val progressColor = if (enabled) activeColor else disabledColor
        drawLine(
            color = trackColor,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = progressColor,
            start = Offset(startX, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = progressColor,
            radius = 9.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
    }
}

@Composable
private fun ReaderToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.widthIn(min = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalIconButton(
            enabled = enabled,
            onClick = onClick
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReaderChapterBlock(
    entry: ReaderChapter,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: androidx.compose.ui.text.TextStyle,
    density: Density,
    settings: ReaderSettings,
    contentColor: androidx.compose.ui.graphics.Color,
    textTransform: (String) -> String = { it }
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        ChapterHeading(entry.chapter.name, settings, textTransform)
        ChapterContent(
            detail = entry.detail,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            density = density,
            settings = settings,
            contentColor = contentColor,
            textTransform = textTransform
        )
    }
}

@Composable
private fun ChapterContent(
    detail: DetailedChapter,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: androidx.compose.ui.text.TextStyle,
    density: Density,
    settings: ReaderSettings,
    contentColor: androidx.compose.ui.graphics.Color,
    textTransform: (String) -> String = { it }
) {
    for (component in detail.content) {
        if (component is TextComponent) {
            val (str, inlines) = component.toInlineAnnotatedString(
                textMeasurer,
                textStyle,
                density,
                textTransform
            )
            Text(
                text = str,
                inlineContent = inlines,
                style = textStyle,
                color = contentColor,
                modifier = Modifier.padding(bottom = settings.paragraphSpacingDp.dp)
            )
        } else if (component is ImageComponent) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(component.url)
                    .crossfade(true)
                    .build(),
                contentDescription = "chapter image",
                imageLoader = MainActivity.imageLoader,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = settings.paragraphSpacingDp.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

private enum class ReaderDrawerSide {
    START,
    END
}

@Composable
private fun ReaderDrawer(
    visible: Boolean,
    side: ReaderDrawerSide,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val alignment = if (side == ReaderDrawerSide.START) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }
    val offset: (Int) -> Int = if (side == ReaderDrawerSide.START) {
        { width: Int -> -width }
    } else {
        { width: Int -> width }
    }
    val shape = if (side == ReaderDrawerSide.START) {
        RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    } else {
        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = scrimInteractionSource,
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = offset) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = offset) + fadeOut(),
            modifier = Modifier
                .align(alignment)
                .fillMaxHeight()
                .fillMaxWidth(2f / 3f)
                .widthIn(max = 480.dp)
                .zIndex(1f)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp,
                tonalElevation = 1.dp
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ReaderContentsDrawer(
    visible: Boolean,
    chapters: List<Chapter>,
    currentChapter: Chapter,
    onChapterSelected: (Chapter) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val currentChapterKey = chapterIdentity(currentChapter)

    LaunchedEffect(visible, chapters.size, currentChapterKey) {
        if (!visible) return@LaunchedEffect
        val currentIndex = chapters.indexOfFirst { chapter ->
            chapterIdentity(chapter) == currentChapterKey
        }
        if (currentIndex >= 0) {
            listState.scrollToItem(currentIndex)
        }
    }

    ReaderDrawer(
        visible = visible,
        side = ReaderDrawerSide.START,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.reader_contents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(id = R.string.close)
                    )
                }
            }

            if (chapters.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.reader_contents_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(
                        items = chapters,
                        key = { chapter -> chapterIdentity(chapter) }
                    ) { item ->
                        val selected = sameReaderChapter(item, currentChapter)
                        TextButton(
                            onClick = { onChapterSelected(item) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item.name,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (selected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sameReaderChapter(first: Chapter, second: Chapter): Boolean =
    chapterIdentity(first) == chapterIdentity(second)

@Composable
private fun ReaderSettingsDrawer(
    visible: Boolean,
    settings: ReaderSettings,
    previewText: String,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ReaderDrawer(
        visible = visible,
        side = ReaderDrawerSide.END,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.reader_settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onSettingsChange(ReaderSettings()) }
                ) {
                    Text(text = stringResource(id = R.string.reader_reset))
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(id = R.string.close)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = settings.background.containerColor().copy(alpha = 0.72f),
                contentColor = settings.background.contentColor()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.reader_live_preview),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${settings.fontSizeSp.roundToInt()}sp",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (previewText.isNotBlank()) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = settings.font.family,
                                fontSize = settings.fontSizeSp.sp,
                                lineHeight = settings.lineHeightSp.sp,
                                letterSpacing = settings.letterSpacingSp.sp
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Text(
                text = stringResource(id = R.string.reader_background),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            ReaderSettingChoices(
                selected = settings.background,
                options = listOf(
                    ReaderBackground.SYSTEM to stringResource(id = R.string.reader_background_system),
                    ReaderBackground.PAPER to stringResource(id = R.string.reader_background_paper),
                    ReaderBackground.SEPIA to stringResource(id = R.string.reader_background_sepia),
                    ReaderBackground.DARK to stringResource(id = R.string.reader_background_dark)
                ),
                onSelected = { background ->
                    onSettingsChange(settings.copy(background = background))
                }
            )

            Text(
                text = stringResource(id = R.string.reader_font),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            ReaderSettingChoices(
                selected = settings.font,
                options = listOf(
                    ReaderFont.SYSTEM to stringResource(id = R.string.reader_font_system),
                    ReaderFont.SERIF to stringResource(id = R.string.reader_font_serif),
                    ReaderFont.MONOSPACE to stringResource(id = R.string.reader_font_monospace)
                ),
                onSelected = { font ->
                    onSettingsChange(settings.copy(font = font))
                }
            )

            Text(
                text = stringResource(id = R.string.reader_script),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            ReaderSettingChoices(
                selected = settings.script,
                options = listOf(
                    ReaderScript.ORIGINAL to stringResource(id = R.string.reader_script_original),
                    ReaderScript.SIMPLIFIED to stringResource(id = R.string.reader_script_simplified),
                    ReaderScript.TRADITIONAL to stringResource(id = R.string.reader_script_traditional)
                ),
                onSelected = { script ->
                    onSettingsChange(settings.copy(script = script))
                }
            )

            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_font_size),
                value = settings.fontSizeSp,
                valueLabel = "${settings.fontSizeSp.roundToInt()}sp",
                valueRange = 14f..30f,
                steps = 15,
                onValueChange = { value ->
                    onSettingsChange(settings.copy(fontSizeSp = value))
                }
            )
            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_letter_spacing),
                value = settings.letterSpacingSp,
                valueLabel = "${(settings.letterSpacingSp * 10f).roundToInt() / 10f}sp",
                valueRange = 0f..2f,
                steps = 19,
                onValueChange = { value ->
                    onSettingsChange(settings.copy(letterSpacingSp = value))
                }
            )
            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_line_spacing),
                value = settings.lineSpacingSp,
                valueLabel = "${settings.lineSpacingSp.roundToInt()}sp",
                valueRange = 4f..24f,
                steps = 19,
                onValueChange = { value ->
                    onSettingsChange(settings.copy(lineSpacingSp = value))
                }
            )
            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_paragraph_spacing),
                value = settings.paragraphSpacingDp,
                valueLabel = "${settings.paragraphSpacingDp.roundToInt()}dp",
                valueRange = 0f..32f,
                steps = 15,
                onValueChange = { value ->
                    onSettingsChange(settings.copy(paragraphSpacingDp = value))
                }
            )
            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_page_spacing),
                value = settings.pageSpacingDp,
                valueLabel = "${settings.pageSpacingDp.roundToInt()}dp",
                valueRange = 16f..80f,
                steps = 15,
                onValueChange = { value ->
                    onSettingsChange(settings.copy(pageSpacingDp = value))
                }
            )
            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_horizontal_padding),
                value = settings.horizontalPaddingDp,
                valueLabel = "${settings.horizontalPaddingDp.roundToInt()}dp",
                valueRange = 12f..48f,
                steps = 8,
                onValueChange = { value ->
                    onSettingsChange(settings.copy(horizontalPaddingDp = value))
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun <T> ReaderSettingChoices(
    selected: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(text = label) }
            )
        }
    }
}

@Composable
private fun ReaderSettingSlider(
    label: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
