package com.breakyuna.esjzone.ui.page
import com.breakyuna.esjzone.app.PresentationAccess

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.navigation.AppDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.LocalReadingHistoryRecorder
import com.breakyuna.esjzone.database.ReadingStatisticsSession
import com.breakyuna.esjzone.database.readingStatisticsBookKey
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.ui.reader.ReaderPageAnimation
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.reader.ReaderBackground
import com.breakyuna.esjzone.ui.reader.ReaderFont
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderScriptConverter
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.reader.ReaderChapterHeading
import com.breakyuna.esjzone.ui.reader.ReaderBlocks
import com.breakyuna.esjzone.ui.reader.ReaderShell
import com.breakyuna.esjzone.ui.reader.ReaderVolumeKeyDispatcher
import com.breakyuna.esjzone.ui.designsystem.AppSideSheet
import com.breakyuna.esjzone.ui.designsystem.AppSideSheetEdge
import com.breakyuna.esjzone.ui.designsystem.AppFeedback
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.rememberAppAdaptiveMetrics
import com.breakyuna.esjzone.ui.designsystem.glass.AppGlassSurface
import com.breakyuna.esjzone.util.AppLogger

private data class ReaderTextSnapshot(
    val script: ReaderScript,
    val chapters: List<ReaderChapter>?,
    val values: Map<String, String>
)

class ChapterPage(
    private val novelId: String,
    private val chapter: Chapter,
    private val history: ChapterStateHolder,
    private val chapterOrder: List<Chapter> = emptyList(),
    private val novelName: String = "",
    private val novelUrl: String = "",
    private val novelCoverUrl: String = "",
    private val resumeChapterProgress: Float? = null,
    private val restoreFromLocalHistory: Boolean = false
) : AppDestination {

    override val isReaderDestination: Boolean = true

    override val key: String =
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
        val adaptiveMetrics = rememberAppAdaptiveMetrics()
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val historyState = history.state()

        val storedReaderSettings by PresentationAccess.readerSettings.settings.collectAsState()
        var readerSettings by remember(storedReaderSettings) {
            mutableStateOf(storedReaderSettings)
        }

        val view = LocalView.current
        val window = remember(view) {
            var ctx: android.content.Context? = view.context
            while (ctx is ContextWrapper) {
                if (ctx is Activity) return@remember ctx.window
                ctx = ctx.baseContext
            }
            null
        }
        val isLightBackground = readerSettings.background.containerColor().luminance() > 0.5f

        DisposableEffect(window, view) {
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val originalStatusAppearance = controller?.isAppearanceLightStatusBars
            val originalNavAppearance = controller?.isAppearanceLightNavigationBars
            onDispose {
                if (originalStatusAppearance != null) {
                    controller.isAppearanceLightStatusBars = originalStatusAppearance
                }
                if (originalNavAppearance != null) {
                    controller.isAppearanceLightNavigationBars = originalNavAppearance
                }
            }
        }

        LaunchedEffect(isLightBackground, window, view) {
            window?.let {
                val controller = WindowCompat.getInsetsController(it, view)
                controller.isAppearanceLightStatusBars = isLightBackground
                controller.isAppearanceLightNavigationBars = isLightBackground
            }
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
            PresentationAccess.readerSettings.saveDebounced(settings)
        }

        val requestedChapter = rememberSaveable {
            mutableStateOf(chapter)
        }

        val chapterPageModel =
            rememberAppViewModel {
                ChapterPageModel(
                    authorization = authorization,
                    requestedChapter = requestedChapter,
                    novelId = novelId,
                    chapterOrder = chapterOrder,
                    novelName = novelName,
                    novelUrl = novelUrl,
                    novelCoverUrl = novelCoverUrl
                )
            }
        val state by chapterPageModel.state.collectAsState()
        var wenkuVerificationChapter by remember { mutableStateOf<Chapter?>(null) }
        var dismissedWenkuPrompt by remember { mutableStateOf<String?>(null) }
        val pendingWenkuVerification = (state as? ChapterPageModel.State.Result)?.verificationChapter
        val pendingWenkuResult = state as? ChapterPageModel.State.Result
        if (pendingWenkuVerification != null &&
            pendingWenkuVerification.url != dismissedWenkuPrompt && wenkuVerificationChapter == null) {
            AlertDialog(
                onDismissRequest = { dismissedWenkuPrompt = pendingWenkuVerification.url },
                title = { Text(stringResource(R.string.wenku_verification_title)) },
                text = { Text(stringResource(
                    if (pendingWenkuResult?.verificationWebViewUnavailable == true)
                        R.string.wenku_webview_unavailable_desc
                    else if (pendingWenkuResult?.verificationStorageUnavailable == true)
                        R.string.wenku_cookie_store_unavailable_desc
                    else R.string.wenku_verification_message
                )) },
                confirmButton = {
                    TextButton(onClick = {
                        dismissedWenkuPrompt = pendingWenkuVerification.url
                        if (pendingWenkuResult?.verificationWebViewUnavailable == true ||
                            pendingWenkuResult?.verificationStorageUnavailable == true) {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse(EsjzoneUrls.resolve(pendingWenkuVerification.url))))
                        } else {
                            wenkuVerificationChapter = pendingWenkuVerification
                        }
                    }) { Text(stringResource(
                        if (pendingWenkuResult?.verificationWebViewUnavailable == true ||
                            pendingWenkuResult?.verificationStorageUnavailable == true)
                            R.string.wenku_open_browser else R.string.wenku_verification_open)) }
                },
                dismissButton = {
                    TextButton(onClick = { dismissedWenkuPrompt = pendingWenkuVerification.url }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        if (wenkuVerificationChapter != null) {
            WenkuVerificationDialog(
                url = EsjzoneUrls.resolve(wenkuVerificationChapter!!.url),
                acceptChapterContent = true,
                onVerified = { html ->
                    val verified = wenkuVerificationChapter
                    val retryPending = verified != null && pendingWenkuVerification?.url == verified.url
                    wenkuVerificationChapter = null
                    dismissedWenkuPrompt = null
                    scope.launch {
                        if (verified != null && html != null) {
                            val cached = withContext(Dispatchers.IO) {
                                com.breakyuna.esjzone.network.EsjzoneClient.importWenkuBrowserChapter(
                                    verified, EsjzoneUrls.resolve(verified.url), html)
                            }
                            if (!cached) com.breakyuna.esjzone.util.AppLogger.w(
                                "ChapterPageModel",
                                "Browser-loaded chapter could not be validated or cached; retrying network"
                            )
                        }
                        if (retryPending) {
                            chapterPageModel.retryPendingVerification()
                        } else {
                            chapterPageModel.openChapter(requestedChapter.value)
                        }
                    }
                },
                onUnavailable = {
                    val pending = pendingWenkuVerification
                    wenkuVerificationChapter = null
                    if (pending != null) chapterPageModel.markPendingStorageUnavailable()
                    else chapterPageModel.openChapter(requestedChapter.value)
                },
                onDismiss = { wenkuVerificationChapter = null }
            )
        }
        var chapterPassword by remember { mutableStateOf("") }
        val passwordRequired = state as? ChapterPageModel.State.PasswordRequired

        if (passwordRequired != null) {
            AlertDialog(
                onDismissRequest = {
                    chapterPassword = ""
                    navigator?.pop()
                },
                title = { Text(stringResource(R.string.reader_password_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(stringResource(R.string.reader_password_message))
                        OutlinedTextField(
                            value = chapterPassword,
                            onValueChange = { chapterPassword = it },
                            label = { Text(stringResource(R.string.reader_password_label)) },
                            singleLine = true
                        )
                        passwordRequired.message?.takeIf { it.isNotBlank() }?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = chapterPassword.isNotBlank(),
                        onClick = {
                            val submittedPassword = chapterPassword
                            chapterPassword = ""
                            chapterPageModel.submitChapterPassword(submittedPassword)
                        }
                    ) { Text(stringResource(R.string.reader_password_submit)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        chapterPassword = ""
                        navigator?.pop()
                    }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        var showToolbar by rememberSaveable {
            mutableStateOf(false)
        }

        // Keep one stable list state for the entire reading session.  Chapter
        // items use stable URL keys below, so adding a chapter before the
        // current item no longer requires manually summing measured heights.
        val scrollState = rememberLazyListState()
        val lifecycleOwner = LocalLifecycleOwner.current
        var readerResumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        var commentReturnKey by rememberSaveable { mutableStateOf<String?>(null) }
        var commentReturnOffset by rememberSaveable { mutableStateOf(0) }
        var commentReturnChapterUrl by rememberSaveable { mutableStateOf<String?>(null) }
        var commentReturnChapterName by rememberSaveable { mutableStateOf("") }
        var commentRecoveryStarted by remember { mutableStateOf(false) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> readerResumed = true
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> readerResumed = false
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        var progressPreview by remember { mutableStateOf<ReaderBookLocation?>(null) }
        var progressReturnLocation by remember { mutableStateOf<ReaderBookLocation?>(null) }
        var pendingSeekLocation by remember { mutableStateOf<ReaderBookLocation?>(null) }
        var resolvedResumeProgress by remember(chapter.url) { mutableStateOf(resumeChapterProgress) }
        var restoreLookupPending by remember(chapter.url) { mutableStateOf(restoreFromLocalHistory) }
        // Do not save the initial zero position before the stored location is restored.
        var resumePending by remember(chapter.url, resumeChapterProgress, restoreFromLocalHistory) {
            mutableStateOf(resumeChapterProgress != null || restoreFromLocalHistory)
        }
        LaunchedEffect(restoreFromLocalHistory, novelId, chapter.url) {
            if (!restoreFromLocalHistory) return@LaunchedEffect
            try {
                val saved = withContext(Dispatchers.IO) {
                    PresentationAccess.database.localReadingActivityDao()
                        .getLatestForNovel(novelId)
                }
                if (saved != null) {
                    val savedKey = EsjzoneUrls.canonicalPageKey(saved.chapterUrl)
                    val initialKey = EsjzoneUrls.canonicalPageKey(chapter.url)
                    if (savedKey.isNotBlank() && savedKey != initialKey) {
                        val target = Chapter(saved.chapterName, saved.chapterUrl, true)
                        requestedChapter.value = target
                        chapterPageModel.openChapter(target)
                    }
                    resolvedResumeProgress = saved.chapterProgress
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("ChapterPage", "Unable to restore local reading position", error)
            } finally {
                restoreLookupPending = false
            }
        }
        var isBookProgressDragging by remember { mutableStateOf(false) }
        var draggingBookProgress by remember { mutableStateOf<Float?>(null) }
        var isProgrammaticScroll by remember { mutableStateOf(false) }

        fun dismissProgressPreview() {
            progressPreview = null
            progressReturnLocation = null
            draggingBookProgress = null
            isBookProgressDragging = false
        }

        LaunchedEffect(scrollState.isScrollInProgress) {
            if (scrollState.isScrollInProgress && !isProgrammaticScroll) {
                if (showToolbar) {
                    showToolbar = false
                }
                if (progressPreview != null) {
                    dismissProgressPreview()
                }
            }
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
        val displayItems = remember(result?.chapters) {
            result?.chapters.orEmpty().flatMap { entry ->
                val chapterKey = chapterIdentity(entry.chapter)
                val chunks = entry.document.blocks.chunked(8)
                val count = chunks.size + 1
                listOf(ReaderDisplayItem(chapterKey, chapterKey, entry, 0, count, emptyList())) +
                    chunks.mapIndexed { index, blocks ->
                        ReaderDisplayItem("$chapterKey|part:$index", chapterKey, entry, index + 1, count, blocks)
                    }
            }
        }
        val displayIndexByKey = remember(displayItems) {
            displayItems.mapIndexed { index, item -> item.key to index }.toMap()
        }
        val displayByKey = remember(displayItems) { displayItems.associateBy { it.key } }
        val firstItemByChapter = remember(displayItems) {
            displayItems.mapIndexedNotNull { index, item ->
                if (item.ordinal == 0) item.chapterKey to index else null
            }.toMap()
        }
        LaunchedEffect(readerResumed, commentReturnKey, displayItems) {
            val key = commentReturnKey ?: return@LaunchedEffect
            if (!readerResumed) return@LaunchedEffect
            val index = displayIndexByKey[key] ?: run {
                if (result != null && !commentRecoveryStarted) {
                    val url = commentReturnChapterUrl
                    if (!url.isNullOrBlank()) {
                        commentRecoveryStarted = true
                        chapterPageModel.openChapter(Chapter(commentReturnChapterName, url, false))
                    }
                }
                return@LaunchedEffect
            }
            isProgrammaticScroll = true
            try {
                scrollState.scrollToItem(index, commentReturnOffset)
                commentReturnKey = null
                commentReturnChapterUrl = null
                commentRecoveryStarted = false
            } finally {
                isProgrammaticScroll = false
            }
        }
        var convertedText by remember { mutableStateOf<ReaderTextSnapshot?>(null) }
        val readerTextTransform: (String) -> String = remember(readerSettings.script, result?.chapters, convertedText) {
            val script = readerSettings.script
            val snapshot = convertedText?.takeIf {
                it.script == script && it.chapters == result?.chapters
            }?.values
            val transform: (String) -> String = { text ->
                if (script == ReaderScript.ORIGINAL) text
                else snapshot?.get(text) ?: text
            }
            transform
        }
        LaunchedEffect(readerSettings.script, result?.chapters) {
            val script = readerSettings.script
            val chapters = result?.chapters.orEmpty()
            if (script == ReaderScript.ORIGINAL || chapters.isEmpty()) {
                convertedText = null
                return@LaunchedEffect
            }
            val values = withContext(Dispatchers.Default) {
                ReaderScriptConverter.snapshot(chapters.map { it.document }, script).toMutableMap().apply {
                    chapters.forEach { entry ->
                        val name = entry.chapter.name
                        if (name.isNotBlank() && name !in this) {
                            this[name] = ReaderScriptConverter.convert(name, script)
                        }
                    }
                }.toMap()
            }
            convertedText = ReaderTextSnapshot(script, result?.chapters, values)
        }
        var retainedActiveChapterKey by rememberSaveable {
            mutableStateOf(chapterIdentity(chapter))
        }
        val visibleActiveChapterKey by remember(displayByKey, scrollState, chapterActivationOffset) {
            derivedStateOf {
                val visibleChapters = scrollState.layoutInfo.visibleItemsInfo
                    .mapNotNull { item ->
                        displayByKey[item.key.toString()]?.let { it.chapterKey to item.offset }
                    }
                visibleChapters
                    .lastOrNull { it.second <= chapterActivationOffset }
                    ?.first
                    ?: visibleChapters.firstOrNull()?.first
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
        val activeChapterItem by remember(displayByKey, activeChapter, scrollState) {
            derivedStateOf {
                val activeKey = activeChapter?.chapter?.let(::chapterIdentity)
                scrollState.layoutInfo.visibleItemsInfo.firstOrNull {
                    displayByKey[it.key.toString()]?.chapterKey == activeKey
                }
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
                    PresentationAccess.database.bookmarkDao().findByChapterUrl(bookmarkChapterUrl) != null
                }.getOrElse { error ->
                    AppLogger.w("ChapterPage", "Failed to load local bookmark state", error)
                    false
                }
            }
        }

        val isAtEndOfChapter = remember(displayByKey, activeChapter, scrollState) {
            derivedStateOf {
                val layoutInfo = scrollState.layoutInfo
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                val activeKey = activeChapter?.chapter?.let(::chapterIdentity)
                val item = displayByKey[lastVisible.key.toString()]
                item != null && item.chapterKey == activeKey &&
                    item.ordinal == item.itemCount - 1 &&
                    (lastVisible.offset + lastVisible.size <= layoutInfo.viewportEndOffset)
            }
        }
        val measuredChapterProgress = if (isAtEndOfChapter.value) {
            1.0f
        } else {
            activeChapterItem?.let { visible ->
                displayByKey[visible.key.toString()]?.let { item ->
                    (item.ordinal + (chapterProgressFor(visible.offset, visible.size) ?: 0f)) /
                        item.itemCount.toFloat()
                }
            }
        }
        var retainedChapterOrder by remember { mutableStateOf<List<Chapter>>(emptyList()) }
        val currentLoadedOrder = result?.chapterOrder.orEmpty()
            .ifEmpty { chapterOrder }
            .ifEmpty {
                if (novelId.isBlank()) {
                    result?.chapters?.map { it.chapter }.orEmpty()
                } else {
                    emptyList()
                }
            }
        if (currentLoadedOrder.isNotEmpty()) {
            retainedChapterOrder = currentLoadedOrder
        }
        val bookChapterOrder = currentLoadedOrder.ifEmpty { retainedChapterOrder }
        fun belongsToCurrentNovel(target: Chapter): Boolean =
            (novelId.isNotBlank() && novelId == target.novelId()) ||
                (target.source == com.breakyuna.esjzone.novellibrary.novel.ChapterSource.WENKU8 &&
                    bookChapterOrder.any { sameReaderChapter(it, target) })
        val bookChapterIndices = remember(bookChapterOrder) {
            bookChapterOrder.mapIndexed { index, item -> chapterIdentity(item) to index }.toMap()
        }
        val measuredBookLocation = activeChapter?.chapter
            ?.takeIf { measuredChapterProgress != null }
            ?.let {
                readerBookLocationFor(
                    activeChapter = it,
                    chapterProgress = measuredChapterProgress ?: 0f,
                    chapterOrder = bookChapterOrder,
                    chapterIndices = bookChapterIndices
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
                    .mapNotNull { displayByKey[it.key.toString()]?.chapterKey }
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
                chapterProgress = currentBookLocation?.chapterProgress ?: measuredChapterProgress ?: 0f
            )
        )

        val statisticsBookKey = readingStatisticsBookKey(
            localHistoryPosition.value.novelId,
            localHistoryPosition.value.novelUrl,
            chapter.url
        )
        val statisticsBookName = localHistoryPosition.value.novelName
        val statisticsReady = (state as? ChapterPageModel.State.Result)
            ?.chapters?.any { it.document.blocks.isNotEmpty() } == true
        val statisticsSession = remember(statisticsBookKey, statisticsBookName) {
            ReadingStatisticsSession(statisticsBookKey, statisticsBookName)
        }
        DisposableEffect(lifecycleOwner, statisticsReady, readerResumed, statisticsSession) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> if (statisticsReady && readerResumed) statisticsSession.start()
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> statisticsSession.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (statisticsReady && readerResumed && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                statisticsSession.start()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                statisticsSession.stop()
            }
        }
        LaunchedEffect(statisticsReady, statisticsSession) {
            if (!statisticsReady) return@LaunchedEffect
            while (true) {
                delay(30_000L)
                statisticsSession.checkpoint()
            }
        }

        // A history-origin reader suppresses position writes until restoration
        // completes. Still update its timestamp immediately so the bookshelf's
        // recent-reading showcase and default order react on a quick exit.
        LaunchedEffect(localHistoryActivityId, resumeChapterProgress, restoreFromLocalHistory) {
            if (resumeChapterProgress != null || restoreFromLocalHistory) {
                LocalReadingHistoryRecorder.touch(
                    novelId = localHistoryPosition.value.novelId,
                    novelUrl = localHistoryPosition.value.novelUrl
                )
            }
        }

        fun toggleBookmark() {
            val target = bookmarkChapter
            if (bookmarkChapterUrl.isBlank()) return
            val wasBookmarked = isBookmarked
            isBookmarked = !wasBookmarked
            scope.launch(Dispatchers.IO) {
                try {
                    val dao = PresentationAccess.database.bookmarkDao()
                    val finalNovelId = novelId.ifBlank { target.novelId() }
                    if (wasBookmarked) {
                        dao.deleteByChapterUrl(bookmarkChapterUrl)
                        com.breakyuna.esjzone.database.BookmarkCoverStore.cleanupIfUnused(finalNovelId, bookmarkChapterUrl)
                    } else {
                        dao.insert(
                            com.breakyuna.esjzone.database.entity.Bookmark(
                                chapterUrl = bookmarkChapterUrl,
                                novelId = finalNovelId,
                                novelName = novelName
                                    .ifBlank { novelId }
                                    .ifBlank { target.novelId() }
                                    .ifBlank { target.name },
                                chapterName = target.name
                            )
                        )
                        val coverSource = novelCoverUrl.ifBlank {
                            localHistoryPosition.value.novelCoverUrl
                        }
                        val currentNovelUrl = novelUrl.ifBlank {
                            localHistoryPosition.value.novelUrl
                        }
                        com.breakyuna.esjzone.database.BookmarkCoverStore.saveCoverFromCacheOrDownload(
                            novelId = finalNovelId,
                            coverUrl = coverSource,
                            novelUrl = currentNovelUrl,
                            chapterUrl = bookmarkChapterUrl
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
        LaunchedEffect(localHistoryActivityId) {
            snapshotFlow { if (resumePending) null else localHistoryPosition.value }
                .filterNotNull()
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
        DisposableEffect(lifecycleOwner, localHistoryActivityId) {
            val observer = LifecycleEventObserver { _, event ->
                if ((event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) && !resumePending) {
                    LocalReadingHistoryRecorder.upsert(
                        localHistoryPosition.value.toLocalReadingActivity(
                            activityId = localHistoryActivityId,
                            startedAt = localHistoryStartedAt
                        )
                    )
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                if (!resumePending) {
                    LocalReadingHistoryRecorder.upsert(
                        localHistoryPosition.value.toLocalReadingActivity(
                            activityId = localHistoryActivityId,
                            startedAt = localHistoryStartedAt
                        )
                    )
                }
            }
        }
        val displayedBookProgress = if (isBookProgressDragging) {
            draggingBookProgress ?: progressPreview?.bookProgress ?: currentBookLocation?.bookProgress ?: 0f
        } else {
            pendingSeekLocation?.bookProgress ?: currentBookLocation?.bookProgress ?: 0f
        }
        val currentChapterName = currentReadingChapter.name
        var previousBootstrapFor by remember { mutableStateOf<String?>(null) }
        var suppressPreviousBootstrapFor by remember {
            mutableStateOf<String?>(chapterIdentity(chapter))
        }

        fun seekTo(location: ReaderBookLocation) {
            pendingSeekLocation = location
            val current = currentReadingChapter
            if (!sameReaderChapter(current, location.chapter)) {
                // A progress/contents jump is already an intentional target
                // selection. Do not immediately bootstrap another previous
                // chapter and surprise the window policy with a second load.
                suppressPreviousBootstrapFor = chapterIdentity(location.chapter)
                chapterPageModel.openChapter(location.chapter)
            }
        }

        // Progress gestures are shared by the compact reading rail and the
        // expanded controls. Keeping the callbacks here guarantees that both
        // surfaces capture the same active chapter/fraction snapshot.
        fun beginBookProgressPreview(progress: Float) {
            progressReturnLocation = currentBookLocation
            isBookProgressDragging = true
            draggingBookProgress = progress
            progressPreview = readerBookLocationFor(
                bookProgress = progress,
                chapterOrder = bookChapterOrder
            )
        }

        fun updateBookProgressPreview(progress: Float) {
            draggingBookProgress = progress
            progressPreview = readerBookLocationFor(
                bookProgress = progress,
                chapterOrder = bookChapterOrder
            )
        }

        fun finishBookProgressPreview() {
            isBookProgressDragging = false
            draggingBookProgress = null
            progressPreview?.copy(chapterProgress = 0f)?.let(::seekTo)
        }

        fun cancelBookProgressPreview() {
            isBookProgressDragging = false
            draggingBookProgress = null
            dismissProgressPreview()
        }

        var previousRequestedChapterUrl by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(requestedChapter.value.url) {
            val currentRequestedChapterUrl = requestedChapter.value.url
            val oldRequestedChapterUrl = previousRequestedChapterUrl
            previousRequestedChapterUrl = currentRequestedChapterUrl
            if (commentReturnKey != null) return@LaunchedEffect
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
                if (suppressPreviousBootstrapFor == chapterIdentity(requestedChapter.value)) {
                    suppressPreviousBootstrapFor = null
                    return@LaunchedEffect
                }
                chapterPageModel.loadPreviousChapter()
            }
        }

        LaunchedEffect(resolvedResumeProgress, restoreLookupPending, result?.chapters) {
            if (!resumePending || pendingSeekLocation != null) return@LaunchedEffect
            if (restoreLookupPending) return@LaunchedEffect
            val restored = resolvedResumeProgress ?: run {
                resumePending = false
                return@LaunchedEffect
            }
            val current = result?.chapters?.firstOrNull {
                sameReaderChapter(it.chapter, requestedChapter.value)
            }?.chapter ?: return@LaunchedEffect
            pendingSeekLocation = ReaderBookLocation(
                chapter = current,
                chapterIndex = 0,
                chapterProgress = restored.coerceIn(0f, 1f),
                totalChapters = 1
            )
        }

        LaunchedEffect(pendingSeekLocation?.chapter?.url, result?.chapters) {
            val target = pendingSeekLocation ?: return@LaunchedEffect
            val targetKey = chapterIdentity(target.chapter)
            val startIndex = firstItemByChapter[targetKey] ?: return@LaunchedEffect
            val itemCount = displayItems[startIndex].itemCount
            val scaled = (target.chapterProgress.coerceIn(0f, 1f) * itemCount)
                .coerceAtMost(itemCount - 0.001f)
            val targetIndex = startIndex + scaled.toInt()
            val targetItemKey = displayItems[targetIndex].key

            isProgrammaticScroll = true
            try {
                scrollState.scrollToItem(targetIndex)
                if (scaled > 0f) {
                    val itemSize = kotlinx.coroutines.withTimeoutOrNull(1000L) {
                        snapshotFlow {
                            scrollState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == targetItemKey }
                                ?.let { it.index to it.size }
                        }.first { layout -> layout?.second?.let { it > 0 } == true }
                    }
                    if (itemSize != null) {
                        scrollState.scrollToItem(
                            itemSize.first,
                            (itemSize.second * (scaled - scaled.toInt()))
                                .roundToInt()
                                .coerceAtLeast(0)
                        )
                    }
                }
            } finally {
                isProgrammaticScroll = false
            }
            pendingSeekLocation = null
            resumePending = false
        }

        fun openTargetChapter(target: Chapter) {
            requestedChapter.value = target
            pendingSeekLocation = null
            resumePending = false
            suppressPreviousBootstrapFor = chapterIdentity(target)
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

        DisposableEffect(readerSettings.volumeKeyPaging, currentReadingChapter.url, result) {
            if (!readerSettings.volumeKeyPaging) {
                onDispose { }
            } else {
                val registration = ReaderVolumeKeyDispatcher.register { keyCode ->
                    val pageSize = scrollState.layoutInfo.viewportSize.height.toFloat()
                    if (pageSize <= 0f) return@register false
                    val offset = when (keyCode) {
                        android.view.KeyEvent.KEYCODE_VOLUME_UP -> -pageSize
                        android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> pageSize
                        else -> return@register false
                    }
                    scope.launch { scrollState.animateScrollBy(offset) }
                    true
                }
                onDispose { ReaderVolumeKeyDispatcher.unregister(registration) }
            }
        }

        val pageTranslation = remember { Animatable(0f) }
        val pageAlpha = remember { Animatable(1f) }
        var pageTurnInProgress by remember { mutableStateOf(false) }

        fun turnReaderPage(forward: Boolean) {
            if (pageTurnInProgress) return
            val viewportHeight = scrollState.layoutInfo.viewportSize.height.toFloat()
            if (viewportHeight <= 0f) return
            // Keep a small overlap so the reader never loses the line at the page boundary.
            val distance = viewportHeight * 0.88f * if (forward) 1f else -1f
            val viewportWidth = scrollState.layoutInfo.viewportSize.width.toFloat().coerceAtLeast(1f)
            scope.launch {
                pageTurnInProgress = true
                try {
                    when (readerSettings.pageAnimation) {
                        ReaderPageAnimation.VERTICAL_SCROLL -> scrollState.animateScrollBy(distance)
                        ReaderPageAnimation.HORIZONTAL_SLIDE -> {
                            pageTranslation.animateTo(
                                if (forward) -viewportWidth else viewportWidth,
                                tween(180)
                            )
                            scrollState.scrollBy(distance)
                            pageTranslation.snapTo(if (forward) viewportWidth else -viewportWidth)
                            pageTranslation.animateTo(0f, tween(180))
                        }
                        ReaderPageAnimation.FADE -> {
                            pageAlpha.animateTo(0f, tween(140))
                            scrollState.scrollBy(distance)
                            pageAlpha.animateTo(1f, tween(180))
                        }
                        ReaderPageAnimation.COVER -> {
                            scrollState.scrollBy(distance)
                            pageTranslation.snapTo(if (forward) viewportWidth else -viewportWidth)
                            pageTranslation.animateTo(0f, tween(260))
                        }
                    }
                } finally {
                    pageTranslation.snapTo(0f)
                    pageAlpha.snapTo(1f)
                    pageTurnInProgress = false
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            ReaderShell(
                background = readerSettings.background.containerColor(),
                horizontalSwipeEnabled = true,
                onHorizontalSwipe = ::turnReaderPage,
                onReadingAreaTap = { xFraction, _ ->
                    if (progressPreview != null) {
                        dismissProgressPreview()
                    } else {
                        val forward = when {
                            xFraction < 0.28f -> false
                            xFraction > 0.72f -> true
                            else -> null
                        }
                        if (forward == null) showToolbar = !showToolbar
                        else turnReaderPage(forward)
                    }
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .widthIn(max = adaptiveMetrics.contentMaxWidth)
                            .align(Alignment.Center)
                            .graphicsLayer {
                                translationX = pageTranslation.value
                                alpha = pageAlpha.value
                            }
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                            .padding(horizontal = readerSettings.horizontalPaddingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            // Reserve a stable header area. The header
                            // is an overlay and never changes list geometry.
                            top = ReaderLayout.contentTopPadding,
                            bottom = ReaderLayout.contentBottomPadding
                        )
                    ) {
                    when (state) {
                        is ChapterPageModel.State.Loading, is ChapterPageModel.State.SecurityCheck -> item(key = "reader-loading") {
                            val loadingDescription = stringResource(
                                if (state is ChapterPageModel.State.SecurityCheck) R.string.wenku_security_check
                                else R.string.reader_loading)
                            Column {
                                ReaderChapterHeading(
                                    currentChapterName,
                                    readerSettings,
                                    readerContentColor,
                                    readerTextTransform
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.semantics {
                                                contentDescription = loadingDescription
                                            },
                                            strokeWidth = 2.5.dp
                                        )
                                        Text(
                                            text = loadingDescription,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = readerContentColor.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(top = AppSpacing.md)
                                        )
                                    }
                                }
                            }
                        }

                        is ChapterPageModel.State.UnsupportedExternalLink -> item(key = "reader-unsupported-link") {
                            Column {
                                ReaderChapterHeading(
                                    currentChapterName,
                                    readerSettings,
                                    readerContentColor,
                                    readerTextTransform
                                )
                                ReaderFeedbackState(
                                    title = stringResource(R.string.external_link_not_supported),
                                    message = stringResource(R.string.external_link_not_supported_desc),
                                    isError = false,
                                    actionLabel = stringResource(R.string.open_in_app_browser),
                                    onAction = {
                                        InAppBrowserActivity.open(
                                            context, EsjzoneUrls.resolve(requestedChapter.value.url)
                                        )
                                    }
                                )
                            }
                        }

                        is ChapterPageModel.State.VerificationRequired -> item(key = "reader-verification") {
                            Column {
                                ReaderChapterHeading(currentChapterName, readerSettings, readerContentColor, readerTextTransform)
                                ReaderFeedbackState(
                                    title = stringResource(R.string.wenku_verification_title),
                                    message = stringResource(R.string.wenku_verification_message),
                                    isError = false,
                                    actionLabel = stringResource(R.string.wenku_verification_open),
                                    onAction = { wenkuVerificationChapter = requestedChapter.value }
                                )
                                TextButton(onClick = { chapterPageModel.openChapter(requestedChapter.value) }) {
                                    Text(stringResource(R.string.retry))
                                }
                                TextButton(onClick = {
                                    val url = EsjzoneUrls.resolve(requestedChapter.value.url)
                                    if (url.startsWith("https://www.wenku8.net/")) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }) { Text(stringResource(R.string.wenku_open_browser)) }
                            }
                        }

                        is ChapterPageModel.State.WebViewUnavailable -> item(key = "reader-webview-unavailable") {
                            ReaderFeedbackState(
                                title = stringResource(R.string.wenku_webview_unavailable),
                                message = stringResource(R.string.wenku_webview_unavailable_desc),
                                isError = true,
                                actionLabel = stringResource(R.string.wenku_open_browser),
                                onAction = {
                                    val url = EsjzoneUrls.resolve(requestedChapter.value.url)
                                    if (url.startsWith("https://www.wenku8.net/")) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                            )
                        }

                        is ChapterPageModel.State.ExternalParseError -> item(key = "reader-external-parse-error") {
                            ReaderFeedbackState(
                                title = stringResource(R.string.wenku_parse_failed),
                                message = stringResource(R.string.wenku_parse_failed_desc),
                                isError = true,
                                onAction = { chapterPageModel.openChapter(requestedChapter.value) }
                            )
                        }

                        is ChapterPageModel.State.ExternalStorageUnavailable -> item(key = "reader-external-storage-unavailable") {
                            ReaderFeedbackState(
                                title = stringResource(R.string.wenku_cookie_store_unavailable),
                                message = stringResource(R.string.wenku_cookie_store_unavailable_desc),
                                isError = true,
                                onAction = { chapterPageModel.openChapter(requestedChapter.value) }
                            )
                        }

                        is ChapterPageModel.State.Error -> item(key = "reader-error") {
                            Column {
                                ReaderChapterHeading(
                                    currentChapterName,
                                    readerSettings,
                                    readerContentColor,
                                    readerTextTransform
                                )
                                val failure = (state as ChapterPageModel.State.Error).failure
                                ReaderFeedbackState(
                                    title = stringResource(
                                        if (failure == com.breakyuna.esjzone.network.LoadFailureKind.NETWORK) {
                                            R.string.reader_offline_title
                                        } else {
                                            R.string.load_client_error
                                        }
                                    ),
                                    message = stringResource(
                                        if (failure == com.breakyuna.esjzone.network.LoadFailureKind.NETWORK) {
                                            R.string.reader_offline_message
                                        } else {
                                            R.string.load_client_error
                                        }
                                    ),
                                    isError = true,
                                    onAction = { chapterPageModel.openChapter(requestedChapter.value) }
                                )
                            }
                        }

                        is ChapterPageModel.State.Empty -> item(key = "reader-empty") {
                            Column {
                                ReaderChapterHeading(
                                    currentChapterName,
                                    readerSettings,
                                    readerContentColor,
                                    readerTextTransform
                                )
                                ReaderFeedbackState(
                                    title = stringResource(R.string.reader_empty_title),
                                    message = stringResource(R.string.reader_empty_message),
                                    isError = false,
                                    onAction = { chapterPageModel.openChapter(requestedChapter.value) }
                                )
                            }
                        }

                        is ChapterPageModel.State.PasswordRequired -> item(key = "reader-password-required") {
                            // The modal above owns the interaction; keep the reader shell stable behind it.
                            Spacer(Modifier.height(1.dp))
                        }

                        is ChapterPageModel.State.Result -> {
                            val readerResult = state as ChapterPageModel.State.Result
                            if (readerResult.verificationChapter != null && readerResult.verificationOffset < 0) {
                                item(key = "reader-verification-previous") {
                                    ReaderWenkuVerificationState(
                                        unavailable = readerResult.verificationWebViewUnavailable,
                                        storageUnavailable = readerResult.verificationStorageUnavailable,
                                        onVerify = { wenkuVerificationChapter = readerResult.verificationChapter },
                                        onBrowser = {
                                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                                Uri.parse(EsjzoneUrls.resolve(readerResult.verificationChapter!!.url))))
                                        },
                                        onRetry = {
                                            dismissedWenkuPrompt = null
                                            chapterPageModel.retryPendingVerification()
                                        }
                                    )
                                }
                            }
                            items(items = displayItems, key = ReaderDisplayItem::key) { item ->
                                if (item.ordinal == 0) {
                                    Column {
                                        if (displayIndexByKey[item.key] != 0) {
                                            Spacer(Modifier.height(readerSettings.pageSpacingDp.dp))
                                        }
                                        ReaderChapterHeading(item.entry.chapter.name, readerSettings,
                                            readerContentColor, readerTextTransform)
                                    }
                                } else {
                                    ReaderBlocks(item.blocks, readerSettings, textMeasurer, density,
                                        readerContentColor, readerTextTransform)
                                }
                            }

                            if (readerResult.verificationChapter != null && readerResult.verificationOffset > 0) {
                                item(key = "reader-verification-next") {
                                    ReaderWenkuVerificationState(
                                        unavailable = readerResult.verificationWebViewUnavailable,
                                        storageUnavailable = readerResult.verificationStorageUnavailable,
                                        onVerify = { wenkuVerificationChapter = readerResult.verificationChapter },
                                        onBrowser = {
                                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                                Uri.parse(EsjzoneUrls.resolve(readerResult.verificationChapter!!.url))))
                                        },
                                        onRetry = {
                                            dismissedWenkuPrompt = null
                                            chapterPageModel.retryPendingVerification()
                                        }
                                    )
                                }
                            }

                            if (readerResult.isLoadingNext) {
                                item(key = "reader-loading-next") {
                                    val loadingDescription = stringResource(R.string.reader_loading)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .semantics {
                                                contentDescription = loadingDescription
                                            },
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
                totalChapters = currentBookLocation?.totalChapters ?: bookChapterOrder.size,
                contentColor = readerContentColor
            )

            if ((state as? ChapterPageModel.State.Result)?.isOffline == true) {
                AppGlassSurface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 32.dp, end = AppSpacing.lg),
                    spec = com.breakyuna.esjzone.ui.designsystem.glass.AppGlassSpec(
                        tint = MaterialTheme.colorScheme.tertiaryContainer,
                        alpha = 0.9f,
                        shape = AppShapes.pill
                    )
                ) {
                    Text(
                        text = stringResource(R.string.reader_offline_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                    )
                }
            }

            if ((state as? ChapterPageModel.State.Result)?.isLoadingPrevious == true) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = ReaderLayout.previousLoadingTopPadding),
                    shape = AppShapes.prominent,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = AppSpacing.md,
                            vertical = AppSpacing.sm
                        ),
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

            AnimatedVisibility(
                visible = progressPreview != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(bottom = ReaderLayout.progressPreviewBottomPadding)
                    .zIndex(3f)
            ) {
                progressPreview?.let { preview ->
                    ReaderProgressLens(
                        location = preview,
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        spec = com.breakyuna.esjzone.ui.designsystem.glass.AppGlassSpec(
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            alpha = 0.94f
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = AppSpacing.lg,
                                    top = AppSpacing.sm,
                                    end = AppSpacing.lg
                                )
                        ) {
                            val readerResult = state as? ChapterPageModel.State.Result
                            val navigationChapter = pendingSeekLocation?.chapter ?: currentReadingChapter
                            val navigationIndex = bookChapterOrder.indexOfFirst {
                                sameReaderChapter(it, navigationChapter)
                            }
                            val activePrevious = if (navigationIndex > 0) {
                                bookChapterOrder.getOrNull(navigationIndex - 1)
                            } else {
                                readerResult?.previous
                            }
                            val activeNext = if (navigationIndex >= 0) {
                                bookChapterOrder.getOrNull(navigationIndex + 1)
                            } else {
                                null
                            } ?: readerResult?.next

                            if (bookChapterOrder.isNotEmpty()) {
                                ReaderProgressRail(
                                    progress = displayedBookProgress,
                                    enabled = true,
                                    previousEnabled = activePrevious != null,
                                    nextEnabled = activeNext != null,
                                    onPrevious = {
                                        activePrevious?.let { previous ->
                                            if (belongsToCurrentNovel(previous)) {
                                                historyState.value = previous
                                            }
                                            dismissProgressPreview()
                                            openTargetChapter(previous)
                                        }
                                    },
                                    onNext = {
                                        activeNext?.let { next ->
                                            if (belongsToCurrentNovel(next)) {
                                                historyState.value = next
                                            }
                                            dismissProgressPreview()
                                            openTargetChapter(next)
                                        }
                                    },
                                    onDragStart = ::beginBookProgressPreview,
                                    onDrag = ::updateBookProgressPreview,
                                    onDragFinished = ::finishBookProgressPreview,
                                    onDragCancelled = ::cancelBookProgressPreview
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.reader_progress_unavailable),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }

                            val commentChapter = currentReadingChapter
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(bottom = AppSpacing.xs),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ReaderToolButton(
                                    contentDescription = stringResource(R.string.reader_contents),
                                    icon = Icons.Filled.List,
                                    onClick = {
                                        dismissProgressPreview()
                                        showReaderSettings = false
                                        showReaderContents = true
                                    }
                                )
                                ReaderToolButton(
                                    contentDescription = stringResource(R.string.reader_settings),
                                    icon = Icons.Filled.Settings,
                                    onClick = {
                                        dismissProgressPreview()
                                        showReaderContents = false
                                        showReaderSettings = true
                                    }
                                )
                                ReaderToolButton(
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
                                    contentDescription = stringResource(R.string.comments),
                                    icon = Icons.Filled.Forum,
                                    enabled = state is ChapterPageModel.State.Result &&
                                        commentChapter.source == com.breakyuna.esjzone.novellibrary.novel.ChapterSource.ESJ_ZONE,
                                    onClick = {
                                        dismissProgressPreview()
                                        val visible = scrollState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { item -> item.key.toString() in displayByKey }
                                        commentReturnKey = visible?.key?.toString()
                                        commentReturnChapterUrl = visible?.key?.toString()
                                            ?.let { displayByKey[it]?.entry?.chapter?.url }
                                        commentReturnChapterName = visible?.key?.toString()
                                            ?.let { displayByKey[it]?.entry?.chapter?.name }.orEmpty()
                                        commentReturnOffset = visible?.let {
                                            if (it.index == scrollState.firstVisibleItemIndex)
                                                scrollState.firstVisibleItemScrollOffset else 0
                                        } ?: 0
                                        val pushed = navigator?.pushIfNotCurrent(
                                            ChapterCommentsPage(
                                                chapterName = commentChapter.name,
                                                chapterUrl = commentChapter.url
                                            )
                                        )
                                        if (pushed == true) readerResumed = false
                                        else {
                                            commentReturnKey = null
                                            commentReturnChapterUrl = null
                                        }
                                    }
                                )
                                val detailUrl = novelUrl.takeIf { it.isNotBlank() }
                                    ?.let { EsjzoneUrls.resolve(it) }
                                    ?: novelId.ifBlank { commentChapter.novelId() }
                                        .takeIf { it.isNotBlank() }
                                        ?.let { id -> EsjzoneUrls.resolve("/detail/$id.html") }
                                        .orEmpty()
                                ReaderToolButton(
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
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(top = 4.dp)
                    .zIndex(2f)
            ) {
                AppGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    spec = com.breakyuna.esjzone.ui.designsystem.glass.AppGlassSpec(
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        alpha = 0.94f
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
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

            val readerChapters = result?.chapterOrder.orEmpty()
                .ifEmpty { chapterOrder }
                .ifEmpty { result?.chapters?.map { it.chapter }.orEmpty() }
            ReaderContentsSheet(
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
            ReaderSettingsSheet(
                visible = showReaderSettings,
                settings = readerSettings,
                previewText = activeChapter?.document?.blocks
                    ?.firstNotNullOfOrNull { block ->
                        when (block) {
                            is ReaderBlock.Paragraph -> block.parts.firstOrNull()?.value
                            is ReaderBlock.Text -> block.value
                            else -> null
                        }
                    }
                    ?.let(readerTextTransform)
                    .orEmpty(),
                onSettingsChange = { updated -> updateReaderSettings(updated) },
                onDismiss = { showReaderSettings = false }
            )
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
            loadedReaderChapterKeys,
            displayItems
        ) {
            var previousSnapshot: ReaderScrollSnapshot? = null
            snapshotFlow {
                val visibleChapterItems = scrollState.layoutInfo.visibleItemsInfo
                    .filter { it.key.toString() in displayByKey }
                val layoutMatchesLoadedWindow = visibleChapterItems.all { item ->
                    displayItems.getOrNull(item.index)?.key == item.key
                }
                ReaderScrollSnapshot(
                    firstVisibleIndex = scrollState.firstVisibleItemIndex,
                    firstVisibleOffset = scrollState.firstVisibleItemScrollOffset,
                    firstVisibleChapterKey = visibleChapterItems.firstOrNull()
                        ?.takeIf { it.index == 0 }
                        ?.let { displayByKey[it.key.toString()]?.chapterKey },
                    lastVisibleChapterKey = visibleChapterItems.lastOrNull()
                        ?.let { displayByKey[it.key.toString()]?.chapterKey },
                    distanceToLoadedTail = visibleChapterItems.lastOrNull()
                        ?.takeIf { it.index == displayItems.lastIndex }?.let { item ->
                        (
                            item.offset + item.size - scrollState.layoutInfo.viewportEndOffset
                        ).coerceAtLeast(0)
                    } ?: Int.MAX_VALUE,
                    loadedChapterKeys = loadedReaderChapterKeys,
                    layoutMatchesLoadedWindow = layoutMatchesLoadedWindow,
                    isScrollInProgress = scrollState.isScrollInProgress,
                    isProgrammaticScroll = isProgrammaticScroll,
                    canScrollForward = scrollState.canScrollForward
                )
            }.collect { snapshot ->
                val currentResult = state as? ChapterPageModel.State.Result
                if (shouldLoadPreviousChapter(
                        previous = previousSnapshot,
                        current = snapshot,
                        threshold = previousLoadThreshold
                    )
                ) {
                    chapterPageModel.loadPreviousChapter()
                }
                val userScrolledToNext = shouldLoadNextChapter(
                    previous = previousSnapshot,
                    current = snapshot,
                    threshold = continuousLoadThreshold
                )
                val contentTailExposed = snapshot.layoutMatchesLoadedWindow &&
                    snapshot.lastVisibleChapterKey == snapshot.loadedChapterKeys.lastOrNull() &&
                    !snapshot.canScrollForward &&
                    !snapshot.isProgrammaticScroll &&
                    currentResult?.next != null &&
                    currentResult?.isLoadingNext == false

                if (userScrolledToNext || contentTailExposed) {
                    chapterPageModel.loadNextChapter()
                }
                previousSnapshot = snapshot.takeIf { it.layoutMatchesLoadedWindow }
            }
        }

        LaunchedEffect(activeChapter?.chapter?.url) {
            activeChapter?.chapter?.let { current ->
                if (belongsToCurrentNovel(current)) {
                    historyState.value = current
                }
            }
        }
    }

}

private data class ReaderDisplayItem(
    val key: String,
    val chapterKey: String,
    val entry: ReaderChapter,
    val ordinal: Int,
    val itemCount: Int,
    val blocks: List<ReaderBlock>
)

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
    chapterOrder: List<Chapter>,
    chapterIndices: Map<String, Int>
): ReaderBookLocation? {
    val index = chapterIndices[chapterIdentity(activeChapter)] ?: -1
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
    return ReaderBookLocation(
        chapter = chapterOrder[index],
        chapterIndex = index,
        // The slider selects a chapter, rather than a position inside its
        // body.  Keeping a fractional offset here previously caused a release
        // in the middle of a chapter to scroll to that same middle fraction.
        chapterProgress = 0f,
        totalChapters = chapterOrder.size
    )
}

@Composable
private fun ReaderStatusBar(
    novelName: String,
    chapterName: String,
    chapterIndex: Int,
    totalChapters: Int,
    contentColor: Color
) {
    val title = listOf(novelName.trim(), chapterName.trim())
        .filter(String::isNotBlank)
        .joinToString(" · ")
    val statusColor = contentColor.copy(alpha = 0.56f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xs)
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
private fun ReaderProgressLens(
    location: ReaderBookLocation,
    canReturn: Boolean,
    onReturn: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xEE222222),
        contentColor = Color.White,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 14.dp, end = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = location.chapter.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val progressText = if (location.totalChapters > 0) {
                    "${location.chapterIndex + 1} / ${location.totalChapters}"
                } else {
                    "${location.chapterIndex + 1}"
                }
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.18f))
            )
            IconButton(
                onClick = onReturn,
                enabled = canReturn,
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.reader_preview_return),
                    tint = if (canReturn) Color.White else Color.White.copy(alpha = 0.38f),
                    modifier = Modifier.size(24.dp)
                )
            }
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
private fun ReaderProgressRail(
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
    val progressDescription = stringResource(
        id = R.string.reader_book_progress_percent,
        (progress.coerceIn(0f, 1f) * 100f).roundToInt()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            modifier = Modifier.semantics { contentDescription = previousDescription },
            enabled = previousEnabled,
            onClick = onPrevious
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = null
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

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val touchSlop = viewConfiguration.touchSlop
                        val startPos = down.position
                        var isDragging = false
                        var dragFinished = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) {
                                    break
                                }
                                val dx = change.position.x - startPos.x
                                val dy = change.position.y - startPos.y
                                if (!isDragging) {
                                    if (kotlin.math.abs(dx) > touchSlop) {
                                        isDragging = true
                                        down.consume()
                                        currentOnDragStart(progressAt(change.position.x))
                                    }
                                }
                                if (isDragging) {
                                    // If pulled vertically away (e.g. 56dp), abort and cancel
                                    if (kotlin.math.abs(dy) > 56f * density) {
                                        break
                                    }
                                    if (change.changedToUp()) {
                                        change.consume()
                                        dragFinished = true
                                        break
                                    }
                                    change.consume()
                                    currentOnDrag(progressAt(change.position.x))
                                } else if (change.changedToUp()) {
                                    change.consume()
                                    currentOnDragStart(progressAt(change.position.x))
                                    currentOnDragFinished()
                                    break
                                }
                            }
                        } finally {
                            if (isDragging) {
                                if (dragFinished) {
                                    currentOnDragFinished()
                                } else {
                                    currentOnDragCancelled()
                                }
                            }
                        }
                    }
                }
                .semantics {
                    contentDescription = progressDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        progress.coerceIn(0f, 1f),
                        0f..1f
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
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun ReaderWenkuVerificationState(
    unavailable: Boolean,
    storageUnavailable: Boolean,
    onVerify: () -> Unit,
    onBrowser: () -> Unit,
    onRetry: () -> Unit
) {
    Column {
        ReaderFeedbackState(
            title = stringResource(R.string.wenku_verification_title),
            message = stringResource(
                if (storageUnavailable) R.string.wenku_cookie_store_unavailable_desc
                else if (unavailable) R.string.wenku_webview_unavailable_desc
                else R.string.wenku_verification_message
            ),
            isError = false,
            actionLabel = stringResource(
                if (unavailable || storageUnavailable) R.string.wenku_open_browser
                else R.string.wenku_verification_open
            ),
            onAction = if (unavailable || storageUnavailable) onBrowser else onVerify
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun ReaderFeedbackState(
    title: String,
    message: String,
    isError: Boolean,
    actionLabel: String? = stringResource(R.string.retry),
    onAction: (() -> Unit)? = null
) {
    AppGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xl),
        spec = com.breakyuna.esjzone.ui.designsystem.glass.AppGlassSpec(
            tint = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            alpha = 0.84f,
            shape = AppShapes.prominent
        )
    ) {
        AppFeedback(
            title = title,
            message = message,
            actionLabel = actionLabel,
            onAction = onAction,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReaderToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
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
}

@Composable
private fun ReaderContentsSheet(
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

    AppSideSheet(
        visible = visible,
        edge = AppSideSheetEdge.START,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
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
                val contentsDescription = stringResource(R.string.reader_contents)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .semantics {
                            contentDescription = contentsDescription
                        }
                ) {
                    items(
                        items = chapters,
                        key = { chapter -> chapterIdentity(chapter) }
                    ) { item ->
                        val selected = sameReaderChapter(item, currentChapter)
                        Surface(
                            onClick = { onChapterSelected(item) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = AppSpacing.xs),
                            shape = AppShapes.standard,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            }
                        ) {
                            Text(
                                text = item.name,
                                modifier = Modifier.padding(
                                    horizontal = AppSpacing.md,
                                    vertical = AppSpacing.md
                                ),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
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
private fun ReaderSettingsSheet(
    visible: Boolean,
    settings: ReaderSettings,
    previewText: String,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    AppSideSheet(
        visible = visible,
        edge = AppSideSheetEdge.END,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
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
                    .padding(top = AppSpacing.md),
                shape = AppShapes.prominent,
                color = settings.background.containerColor().copy(alpha = 0.72f),
                contentColor = settings.background.contentColor()
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = AppSpacing.md,
                        vertical = AppSpacing.md
                    )
                ) {
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
                modifier = Modifier.padding(top = AppSpacing.lg, bottom = AppSpacing.sm)
            )
            ReaderSettingChoices(
                selected = settings.background,
                options = listOf(
                    ReaderBackground.SYSTEM to stringResource(id = R.string.reader_background_system),
                    ReaderBackground.PAPER to stringResource(id = R.string.reader_background_paper),
                    ReaderBackground.SEPIA to stringResource(id = R.string.reader_background_sepia),
                    ReaderBackground.DARK to stringResource(id = R.string.reader_background_dark),
                    ReaderBackground.MINT to stringResource(id = R.string.reader_background_mint),
                    ReaderBackground.LAVENDER to stringResource(id = R.string.reader_background_lavender),
                    ReaderBackground.SLATE to stringResource(id = R.string.reader_background_slate),
                    ReaderBackground.OLED to stringResource(id = R.string.reader_background_oled)
                ),
                onSelected = { background ->
                    onSettingsChange(settings.copy(background = background))
                }
            )

            Text(
                text = stringResource(id = R.string.reader_font),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = AppSpacing.lg, bottom = AppSpacing.sm)
            )
            ReaderSettingChoices(
                selected = settings.font,
                options = listOf(
                    ReaderFont.SYSTEM to stringResource(id = R.string.reader_font_system),
                    ReaderFont.SERIF to stringResource(id = R.string.reader_font_serif),
                    ReaderFont.MONOSPACE to stringResource(id = R.string.reader_font_monospace),
                    ReaderFont.SANS_SERIF to stringResource(id = R.string.reader_font_sans_serif),
                    ReaderFont.CURSIVE to stringResource(id = R.string.reader_font_cursive)
                ),
                onSelected = { font ->
                    onSettingsChange(settings.copy(font = font))
                }
            )

            Text(
                text = stringResource(id = R.string.reader_script),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = AppSpacing.lg, bottom = AppSpacing.sm)
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

            Text(
                text = stringResource(id = R.string.reader_paging_method),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = AppSpacing.lg, bottom = AppSpacing.sm)
            )
            ReaderSettingChoices(
                selected = settings.pageAnimation,
                options = listOf(
                    ReaderPageAnimation.VERTICAL_SCROLL to stringResource(id = R.string.reader_page_animation_vertical),
                    ReaderPageAnimation.HORIZONTAL_SLIDE to stringResource(id = R.string.reader_page_animation_slide),
                    ReaderPageAnimation.FADE to stringResource(id = R.string.reader_page_animation_fade),
                    ReaderPageAnimation.COVER to stringResource(id = R.string.reader_page_animation_cover)
                ),
                onSelected = { animation ->
                    onSettingsChange(settings.copy(pageAnimation = animation))
                }
            )

            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_font_size),
                value = settings.fontSizeSp,
                valueLabel = { "${it.roundToInt()}sp" },
                valueRange = 14f..30f,
                steps = 15,
                onValueChangeFinished = { value ->
                    onSettingsChange(settings.copy(fontSizeSp = value))
                }
            )
            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_letter_spacing),
                value = settings.letterSpacingSp,
                valueLabel = { "${(it * 10f).roundToInt() / 10f}sp" },
                valueRange = 0f..2f,
                steps = 19,
                onValueChangeFinished = { value ->
                    onSettingsChange(settings.copy(letterSpacingSp = value))
                }
            )
            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_line_spacing),
                value = settings.lineSpacingSp,
                valueLabel = { "${it.roundToInt()}sp" },
                valueRange = 4f..24f,
                steps = 19,
                onValueChangeFinished = { value ->
                    onSettingsChange(settings.copy(lineSpacingSp = value))
                }
            )
            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_paragraph_spacing),
                value = settings.paragraphSpacingDp,
                valueLabel = { "${it.roundToInt()}dp" },
                valueRange = 0f..32f,
                steps = 15,
                onValueChangeFinished = { value ->
                    onSettingsChange(settings.copy(paragraphSpacingDp = value))
                }
            )
            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_page_spacing),
                value = settings.pageSpacingDp,
                valueLabel = { "${it.roundToInt()}dp" },
                valueRange = 16f..80f,
                steps = 15,
                onValueChangeFinished = { value ->
                    onSettingsChange(settings.copy(pageSpacingDp = value))
                }
            )
            ReaderSettingSlider(
                label = stringResource(id = R.string.reader_horizontal_padding),
                value = settings.horizontalPaddingDp,
                valueLabel = { "${it.roundToInt()}dp" },
                valueRange = 12f..48f,
                steps = 8,
                onValueChangeFinished = { value ->
                    onSettingsChange(settings.copy(horizontalPaddingDp = value))
                }
            )
            Spacer(modifier = Modifier.height(AppSpacing.lg))
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
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
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
    valueLabel: (Float) -> String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (Float) -> Unit
) {
    var sliderPosition by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.padding(top = AppSpacing.md)) {
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
                text = valueLabel(sliderPosition),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onValueChangeFinished(sliderPosition) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
