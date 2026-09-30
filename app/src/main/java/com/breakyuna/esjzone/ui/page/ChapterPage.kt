@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.breakyuna.esjzone.ui.page
import com.breakyuna.esjzone.app.PresentationAccess

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.view.WindowCompat
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
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
import kotlinx.coroutines.NonCancellable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.AppThemeMode
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
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderScriptConverter
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.reader.ReaderChapterHeading
import com.breakyuna.esjzone.ui.reader.ReaderBlocks
import com.breakyuna.esjzone.ui.reader.ReaderPage
import com.breakyuna.esjzone.ui.reader.ReaderBoundaryTurn
import com.breakyuna.esjzone.ui.reader.resolveReaderBoundaryPage
import com.breakyuna.esjzone.ui.reader.ReaderPageContent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import kotlinx.coroutines.ensureActive
import com.breakyuna.esjzone.ui.reader.paginateReaderChapter
import com.breakyuna.esjzone.ui.reader.ReaderShell
import com.breakyuna.esjzone.ui.reader.readerTapPageDirection
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
        val stableTopInset = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()
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
        val appThemeMode by PresentationAccess.settings.themeMode
        val systemDark = isSystemInDarkTheme()
        val appIsLight = when (appThemeMode) {
            AppThemeMode.SYSTEM -> !systemDark
            AppThemeMode.LIGHT -> true
            AppThemeMode.DARK -> false
        }
        val currentAppIsLight by rememberUpdatedState(appIsLight)

        DisposableEffect(window, view) {
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            onDispose {
                controller?.let {
                    it.isAppearanceLightStatusBars = currentAppIsLight
                    it.isAppearanceLightNavigationBars = currentAppIsLight
                }
            }
        }

        LaunchedEffect(isLightBackground, appThemeMode, systemDark, window, view) {
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
            fontFamily = readerSettings.font.family(),
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
                                Uri.parse(EsjzoneUrls.resolve(pendingWenkuVerification.url)))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
        LaunchedEffect(showReaderSettings) {
            if (showReaderSettings) showToolbar = true
        }
        var readerToolbarHeightPx by remember { mutableIntStateOf(0) }

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
        var showMoreReaderSettings by rememberSaveable { mutableStateOf(false) }
        var pendingBoundaryTurn by remember { mutableStateOf<ReaderBoundaryTurn?>(null) }
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
                    val savedKey = chapterIdentity(Chapter(saved.chapterName, saved.chapterUrl, true))
                    val initialKey = chapterIdentity(chapter)
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
                pendingBoundaryTurn = null
                if (showToolbar && !showReaderSettings) {
                    showToolbar = false
                }
                if (progressPreview != null) {
                    dismissProgressPreview()
                }
            }
        }

        BackHandler(enabled = navigator != null) {
            when {
                showMoreReaderSettings -> showMoreReaderSettings = false
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
        var convertedText by remember { mutableStateOf<ReaderTextSnapshot?>(null) }
        val readerTextTransform: (String) -> String = remember(readerSettings.script, convertedText) {
            val script = readerSettings.script
            val snapshot = convertedText?.takeIf {
                it.script == script
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
        val pagedMode = readerSettings.pageAnimation != ReaderPageAnimation.VERTICAL_SCROLL
        var readerStatusHeightPx by remember { mutableStateOf(0) }
        val pageTopPadding = if (pagedMode) {
            ReaderLayout.pagedContentPadding + WindowInsets.statusBars.union(WindowInsets.displayCutout)
                .asPaddingValues().calculateTopPadding()
        } else ReaderLayout.contentTopPadding + stableTopInset
        val pageBottomPadding = if (pagedMode) {
            ReaderLayout.pagedContentPadding + with(density) { readerStatusHeightPx.toDp() }
        } else ReaderLayout.contentBottomPadding
        var readerViewport by remember { mutableStateOf(IntSize.Zero) }
        val pageContentWidthPx = with(density) {
            (readerViewport.width - 2 * readerSettings.horizontalPaddingDp.dp.roundToPx()).coerceAtLeast(0)
        }
        val pageContentHeightPx = with(density) {
            (readerViewport.height - pageTopPadding.roundToPx() - pageBottomPadding.roundToPx()).coerceAtLeast(0)
        }
        val headingStyle = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = readerSettings.font.family(),
            fontSize = (readerSettings.fontSizeSp + 6f).sp,
            lineHeight = (readerSettings.lineHeightSp + 6f).sp,
            letterSpacing = readerSettings.letterSpacingSp.sp
        )
        val fontResolver = LocalFontFamilyResolver.current
        val layoutDirection = LocalLayoutDirection.current
        // Only layout settings invalidate pagination; toggling chrome never measures text again.
        val paginationSettings = ReaderSettings(
            font = readerSettings.font,
            fontSizeSp = readerSettings.fontSizeSp,
            letterSpacingSp = readerSettings.letterSpacingSp,
            lineSpacingSp = readerSettings.lineSpacingSp,
            paragraphSpacingDp = readerSettings.paragraphSpacingDp
        )
        val paginationLayoutKey = listOf(pageContentWidthPx, pageContentHeightPx,
            paginationSettings, readerTextStyle, headingStyle, density, layoutDirection,
            fontResolver, readerSettings.script, convertedText?.values)
        val paginationKey = paginationLayoutKey + listOf(result?.chapters)
        var paginationSnapshot by remember { mutableStateOf<ReaderPaginationSnapshot?>(null) }
        val scriptReady = readerSettings.script == ReaderScript.ORIGINAL ||
            (convertedText?.script == readerSettings.script && convertedText?.chapters == result?.chapters)
        val paginationReady = !pagedMode || (scriptReady && paginationSnapshot?.key == paginationKey)
        val pagedDisplayItems = paginationSnapshot?.items.orEmpty().filter {
            it.entry in result?.chapters.orEmpty()
        }
        val displayItems = if (pagedMode) pagedDisplayItems else {
            remember(result?.chapters) {
                result?.chapters.orEmpty().flatMap { entry ->
                    val chapterKey = chapterIdentity(entry.chapter)
                    val blocks = entry.document.blocks
                    val count = blocks.size + 1
                    listOf(ReaderDisplayItem(chapterKey, chapterKey, entry, 0, count, emptyList())) +
                        blocks.mapIndexed { index, block ->
                            ReaderDisplayItem("$chapterKey|block:$index", chapterKey, entry,
                                index + 1, count, listOf(block))
                        }
                }
            }
        }
        val pageTurnInProgressForBoundary = remember { mutableStateOf(false) }
        // A departing Pager may still request keys during a mode switch. Keep its
        // count and provider on the same paginated snapshot, never scroll chunks.
        val currentPagedDisplayItems by rememberUpdatedState(pagedDisplayItems)
        val horizontalPagerState = rememberPagerState(pageCount = { currentPagedDisplayItems.size })
        LaunchedEffect(horizontalPagerState.isScrollInProgress, pagedMode) {
            if (pagedMode && horizontalPagerState.isScrollInProgress) {
                if (!pageTurnInProgressForBoundary.value && !isProgrammaticScroll) pendingBoundaryTurn = null
                if (showToolbar && !showReaderSettings) showToolbar = false
                if (progressPreview != null) dismissProgressPreview()
            }
        }
        val displayIndexByKey = remember(displayItems) {
            displayItems.mapIndexed { index, item -> item.key to index }.toMap()
        }
        val displayByKey = remember(displayItems) { displayItems.associateBy { it.key } }
        val hasPreviousVerification = result?.verificationChapter != null && result.verificationOffset < 0
        fun readerListIndex(bodyIndex: Int): Int =
            com.breakyuna.esjzone.ui.reader.readerBodyListIndex(bodyIndex, hasPreviousVerification)
        val pagerVisibleItem by remember(displayByKey, horizontalPagerState) {
            derivedStateOf {
                horizontalPagerState.layoutInfo.visiblePagesInfo
                    .firstOrNull { it.index == horizontalPagerState.currentPage }
                    ?.let { displayByKey[it.key.toString()] }
            }
        }
        val pagerLayoutReady by remember(displayItems, horizontalPagerState) {
            derivedStateOf {
                horizontalPagerState.layoutInfo.visiblePagesInfo.any {
                    it.index == horizontalPagerState.currentPage &&
                        it.key == displayItems.getOrNull(it.index)?.key
                }
            }
        }
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
                if (pagedMode) horizontalPagerState.scrollToPage(index)
                else scrollState.scrollToItem(readerListIndex(index), commentReturnOffset)
                commentReturnKey = null
                commentReturnChapterUrl = null
                commentRecoveryStarted = false
            } finally {
                isProgrammaticScroll = false
            }
        }
        var retainedActiveChapterKey by rememberSaveable {
            mutableStateOf(chapterIdentity(chapter))
        }
        val visibleActiveChapterKey by remember(displayByKey, scrollState, chapterActivationOffset,
            pagedMode, horizontalPagerState, pagerVisibleItem) {
            derivedStateOf {
                if (pagedMode) {
                    pagerVisibleItem?.chapterKey
                } else {
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

        val isAtEndOfChapter = remember(displayByKey, activeChapter, scrollState, pagedMode,
            horizontalPagerState, pagerVisibleItem) {
            derivedStateOf {
                if (pagedMode) {
                    val item = pagerVisibleItem
                    item != null && item.chapterKey == activeChapter?.chapter?.let(::chapterIdentity) &&
                        item.ordinal == item.itemCount - 1
                } else {
                    val layoutInfo = scrollState.layoutInfo
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                    val activeKey = activeChapter?.chapter?.let(::chapterIdentity)
                    val item = displayByKey[lastVisible.key.toString()]
                    item != null && item.chapterKey == activeKey &&
                        item.ordinal == item.itemCount - 1 &&
                        (lastVisible.offset + lastVisible.size <= layoutInfo.viewportEndOffset)
                }
            }
        }
        val measuredChapterProgress = if (isAtEndOfChapter.value) {
            1.0f
        } else if (pagedMode) {
            pagerVisibleItem
                ?.takeIf { it.chapterKey == activeChapter?.chapter?.let(::chapterIdentity) }
                ?.let { it.ordinal.toFloat() / it.itemCount.coerceAtLeast(1) }
        } else {
            activeChapterItem?.let { visible ->
                displayByKey[visible.key.toString()]?.let { item ->
                    (item.ordinal + (chapterProgressFor(visible.offset, visible.size) ?: 0f)) /
                        item.itemCount.toFloat()
                }
            }
        }
        var retainedChapterOrder by remember { mutableStateOf<List<Chapter>>(emptyList()) }
        val currentLoadedOrder = chapterPageModel.chapterOrderState.value
            .ifEmpty { result?.chapterOrder.orEmpty() }
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
        val measuredContentPosition = if (pagedMode) {
            pagerVisibleItem?.page?.contentPosition
        } else {
            activeChapterItem?.let { visible ->
                displayByKey[visible.key.toString()]?.let { item ->
                    item.ordinal - 1f + (chapterProgressFor(visible.offset, visible.size) ?: 0f)
                }
            }
        }
        val measuredBookLocation = activeChapter?.chapter
            ?.takeIf { measuredChapterProgress != null }
            ?.let {
                readerBookLocationFor(
                    activeChapter = it,
                    chapterProgress = measuredChapterProgress ?: 0f,
                    chapterOrder = bookChapterOrder,
                    chapterIndices = bookChapterIndices
                )?.copy(contentPosition = measuredContentPosition)
            }
        var retainedBookLocation by remember(requestedChapter.value.url) {
            mutableStateOf<ReaderBookLocation?>(null)
        }
        LaunchedEffect(measuredBookLocation) {
            measuredBookLocation?.let { retainedBookLocation = it }
        }
        val currentBookLocation = measuredBookLocation ?: retainedBookLocation
        val latestBookLocation by rememberUpdatedState(currentBookLocation)
        LaunchedEffect(pagedMode, paginationKey, scriptReady) {
            if (!pagedMode || !scriptReady || pageContentWidthPx <= 0 || pageContentHeightPx <= 0) return@LaunchedEffect
            val cached = paginationSnapshot?.takeIf { it.layoutKey == paginationLayoutKey }?.pages.orEmpty()
            val entries = result?.chapters.orEmpty()
            val snapshot = withContext(Dispatchers.Default) {
                // A private measurer/cache belongs to this worker, rather than sharing the UI measurer.
                val measurer = TextMeasurer(fontResolver, density, layoutDirection, cacheSize = 0)
                val pages = entries.associateWith { entry ->
                    ensureActive()
                    cached[entry] ?: paginateReaderChapter(
                        entry.chapter.name, entry.document.blocks, paginationSettings,
                        readerTextStyle, headingStyle, measurer, density,
                        pageContentWidthPx, pageContentHeightPx, readerTextTransform
                    )
                }
                val items = entries.flatMap { entry ->
                    val chapterKey = chapterIdentity(entry.chapter)
                    val chapterPages = pages.getValue(entry)
                    chapterPages.mapIndexed { index, page ->
                        ReaderDisplayItem("$chapterKey|page:$index", chapterKey, entry,
                            index, chapterPages.size, emptyList(), page)
                    }
                }
                ReaderPaginationSnapshot(paginationKey, paginationLayoutKey, pages, items)
            }
            if (paginationSnapshot?.layoutKey != paginationLayoutKey &&
                !resumePending && pendingSeekLocation == null) {
                pendingSeekLocation = latestBookLocation
            }
            paginationSnapshot = snapshot
        }
        // Keep the last measured chapter as the UI/history anchor while a
        // list update briefly leaves no matching visible item.
        val currentReadingChapter = currentBookLocation?.chapter
            ?: activeChapter?.chapter
            ?: requestedChapter.value
        val visibleReaderChapterKeys by remember(result, scrollState, pagedMode, horizontalPagerState, pagerVisibleItem) {
            derivedStateOf {
                val loadedKeys = result?.chapters
                    .orEmpty()
                    .map { chapterIdentity(it.chapter) }
                    .toSet()
                if (pagedMode) {
                    listOfNotNull(pagerVisibleItem?.chapterKey)
                        .filter { it in loadedKeys }.toSet()
                } else {
                    scrollState.layoutInfo.visibleItemsInfo
                        .mapNotNull { displayByKey[it.key.toString()]?.chapterKey }
                        .filter { it in loadedKeys }
                        .toSet()
                }
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
        LaunchedEffect(pagedMode, horizontalPagerState, displayItems, result, paginationReady) {
            if (!pagedMode || !paginationReady || result == null || displayItems.isEmpty()) return@LaunchedEffect
            snapshotFlow {
                horizontalPagerState.currentPage.takeIf { page ->
                    horizontalPagerState.layoutInfo.visiblePagesInfo.any {
                        it.index == page && it.key == displayItems.getOrNull(page)?.key
                    }
                }
            }.distinctUntilChanged()
                .collect { page ->
                    if (page == null) return@collect
                    if (page <= 1 && result.previous != null && !result.isLoadingPrevious) {
                        chapterPageModel.loadPreviousChapter()
                    }
                    if (page >= displayItems.lastIndex - 1 && result.next != null && !result.isLoadingNext) {
                        chapterPageModel.loadNextChapter()
                    }
                }
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
        val lastReadablePosition = remember { mutableStateOf<LocalReadingPosition?>(null) }
        if (activeChapter != null && sameReaderChapter(activeChapter.chapter, currentReadingChapter)) {
            lastReadablePosition.value = localHistoryPosition.value
        }

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
            snapshotFlow { if (resumePending) null else lastReadablePosition.value }
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
                    lastReadablePosition.value?.let { position ->
                        LocalReadingHistoryRecorder.upsert(position.toLocalReadingActivity(
                            activityId = localHistoryActivityId,
                            startedAt = localHistoryStartedAt
                        ))
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                if (!resumePending) {
                    lastReadablePosition.value?.let { position ->
                        LocalReadingHistoryRecorder.upsert(position.toLocalReadingActivity(
                            activityId = localHistoryActivityId,
                            startedAt = localHistoryStartedAt
                        ))
                    }
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
            pendingBoundaryTurn = null
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
                if (pagedMode) horizontalPagerState.scrollToPage(0)
                else scrollState.scrollToItem(readerListIndex(0))
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

        LaunchedEffect(pendingSeekLocation, displayItems, paginationReady, hasPreviousVerification) {
            if (!paginationReady) return@LaunchedEffect
            val target = pendingSeekLocation ?: return@LaunchedEffect
            val targetKey = chapterIdentity(target.chapter)
            val startIndex = firstItemByChapter[targetKey] ?: return@LaunchedEffect
            val itemCount = displayItems[startIndex].itemCount
            val scaled = (target.chapterProgress.coerceIn(0f, 1f) * itemCount)
                .coerceAtMost(itemCount - 0.001f)
            val contentPosition = target.contentPosition
            val chapterItems = displayItems.subList(startIndex, startIndex + itemCount)
            val sourceIndex = contentPosition?.let { position ->
                com.breakyuna.esjzone.ui.reader.readerItemForContentPosition(
                    chapterItems.map { it.page?.contentPosition ?: (it.ordinal - 1f) }, position
                )
            }
            val targetIndex = startIndex + (sourceIndex ?: scaled.toInt())
            val targetItemKey = displayItems[targetIndex].key

            isProgrammaticScroll = true
            try {
                if (pagedMode) horizontalPagerState.scrollToPage(targetIndex)
                else scrollState.scrollToItem(readerListIndex(targetIndex))
                val itemFraction = if (contentPosition != null) {
                    (contentPosition - (displayItems[targetIndex].ordinal - 1f)).coerceIn(0f, 1f)
                } else scaled - scaled.toInt()
                if (itemFraction > 0f && !pagedMode) {
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
                            (itemSize.second * itemFraction)
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
            pendingBoundaryTurn = null
            requestedChapter.value = target
            pendingSeekLocation = null
            resumePending = false
            suppressPreviousBootstrapFor = chapterIdentity(target)
            chapterPageModel.openChapter(target)
            scope.launch(Dispatchers.Main) {
                isProgrammaticScroll = true
                try {
                if (pagedMode) horizontalPagerState.scrollToPage(0)
                else scrollState.scrollToItem(readerListIndex(0))
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }

        val pageTranslation = remember { Animatable(0f) }
        val pageAlpha = remember { Animatable(1f) }
        var pageTurnInProgress by pageTurnInProgressForBoundary

        val reducedMotion = com.breakyuna.esjzone.ui.designsystem.rememberReaderReducedMotion()
        val readerDialogVisible = passwordRequired != null || wenkuVerificationChapter != null ||
            (pendingWenkuVerification != null && pendingWenkuVerification.url != dismissedWenkuPrompt)
        val pagingEnabled = readerResumed && paginationReady && (!pagedMode || pagerLayoutReady) &&
            state is ChapterPageModel.State.Result &&
            !showReaderSettings && !showMoreReaderSettings && !showReaderContents && !readerDialogVisible
        com.breakyuna.esjzone.ui.reader.ReaderSystemBars(
            showChrome = (showToolbar || showReaderSettings || showMoreReaderSettings || showReaderContents || readerDialogVisible),
            showSystemStatusBar = readerSettings.showSystemStatusBar,
            showSystemNavigationBar = readerSettings.showSystemNavigationBar
        )

        fun turnReaderPage(forward: Boolean) {
            if (pageTurnInProgress || !pagingEnabled) return
            pendingBoundaryTurn = null
            val viewportHeight = if (pagedMode) readerViewport.height.toFloat()
                else scrollState.layoutInfo.viewportSize.height.toFloat()
            if (viewportHeight <= 0f) return
            val targetPage = horizontalPagerState.currentPage + if (forward) 1 else -1
            if (pagedMode && targetPage !in displayItems.indices) {
                if ((forward && result?.next != null) || (!forward && result?.previous != null)) {
                    pendingBoundaryTurn = ReaderBoundaryTurn(
                        forward = forward,
                        anchorKey = (if (pagedMode) displayItems.getOrNull(horizontalPagerState.currentPage)
                            else if (forward) displayItems.lastOrNull() else displayItems.firstOrNull())?.key ?: return,
                        targetChapterKey = chapterIdentity((if (forward) result.next else result.previous) ?: return)
                    )
                    if (forward) chapterPageModel.loadNextChapter() else chapterPageModel.loadPreviousChapter()
                }
                return
            }
            if (!pagedMode && ((forward && !scrollState.canScrollForward) ||
                    (!forward && !scrollState.canScrollBackward))) {
                if ((forward && result?.next != null) || (!forward && result?.previous != null)) {
                    pendingBoundaryTurn = ReaderBoundaryTurn(
                        forward = forward,
                        anchorKey = (if (pagedMode) displayItems.getOrNull(horizontalPagerState.currentPage)
                            else if (forward) displayItems.lastOrNull() else displayItems.firstOrNull())?.key ?: return,
                        targetChapterKey = chapterIdentity((if (forward) result.next else result.previous) ?: return)
                    )
                    if (forward) chapterPageModel.loadNextChapter() else chapterPageModel.loadPreviousChapter()
                }
                return
            }
            // Keep a small overlap so the reader never loses the line at the page boundary.
            val distance = viewportHeight * 0.88f * if (forward) 1f else -1f
            val viewportWidth = (if (pagedMode) readerViewport.width
                else scrollState.layoutInfo.viewportSize.width).toFloat().coerceAtLeast(1f)
            pageTurnInProgress = true
            scope.launch {
                try {
                    if (pagedMode) {
                        if (reducedMotion) {
                            horizontalPagerState.scrollToPage(targetPage)
                        } else when (readerSettings.pageAnimation) {
                            ReaderPageAnimation.HORIZONTAL_SLIDE -> {
                                horizontalPagerState.animateScrollToPage(targetPage)
                            }
                            ReaderPageAnimation.FADE -> {
                                pageAlpha.animateTo(0f, tween(140))
                                horizontalPagerState.scrollToPage(targetPage)
                                pageAlpha.animateTo(1f, tween(180))
                            }
                            ReaderPageAnimation.COVER -> {
                                horizontalPagerState.scrollToPage(targetPage)
                                pageTranslation.snapTo(if (forward) viewportWidth else -viewportWidth)
                                pageTranslation.animateTo(0f, tween(260))
                            }
                            ReaderPageAnimation.VERTICAL_SCROLL -> Unit
                        }
                    } else if (reducedMotion) {
                        scrollState.scrollBy(distance)
                    } else when (readerSettings.pageAnimation) {
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
                    try {
                        withContext(NonCancellable) {
                            pageTranslation.snapTo(0f)
                            pageAlpha.snapTo(1f)
                        }
                    } finally {
                        pageTurnInProgress = false
                    }
                }
            }
        }

        LaunchedEffect(result, displayItems, pendingBoundaryTurn, pagingEnabled, paginationReady) {
            val pending = pendingBoundaryTurn ?: return@LaunchedEffect
            if (!pagingEnabled || result == null) return@LaunchedEffect
            val loading = if (pending.forward) result.isLoadingNext else result.isLoadingPrevious
            val targetLoaded = result.chapters.any { chapterIdentity(it.chapter) == pending.targetChapterKey }
            if (!targetLoaded) {
                if (!loading) pendingBoundaryTurn = null
                return@LaunchedEffect
            }
            if (!paginationReady) return@LaunchedEffect
            val targetIndex = resolveReaderBoundaryPage(
                pending, displayItems.map { it.key }, displayItems.map { it.chapterKey }
            ) ?: run { pendingBoundaryTurn = null; return@LaunchedEffect }
            if (pagedMode) {
                snapshotFlow {
                    horizontalPagerState.layoutInfo.visiblePagesInfo.any { page ->
                        page.key == pending.anchorKey && displayItems.getOrNull(page.index)?.key == page.key
                    }
                }.first { it }
                pendingBoundaryTurn = null
                if (displayItems.getOrNull(horizontalPagerState.currentPage)?.key == pending.anchorKey) {
                    turnReaderPage(pending.forward)
                }
            } else {
                snapshotFlow {
                    scrollState.layoutInfo.visibleItemsInfo.any { item ->
                        item.key == pending.anchorKey &&
                            displayItems.getOrNull(item.index - readerListIndex(0))?.key == item.key
                    }
                }.first { it }
                pendingBoundaryTurn = null
                scrollState.scrollToItem(readerListIndex(targetIndex))
            }
        }

        val currentTurnPage by rememberUpdatedState<(Boolean) -> Unit>(::turnReaderPage)
        DisposableEffect(readerSettings.volumeKeyPaging, pagingEnabled) {
            val registration = if (readerSettings.volumeKeyPaging && pagingEnabled) {
                ReaderVolumeKeyDispatcher.register { key ->
                    currentTurnPage(key == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
                    true
                }
            } else null
            onDispose { registration?.let(ReaderVolumeKeyDispatcher::unregister) }
        }

        val readerLoadingDescription = stringResource(R.string.reader_loading)
        Box(modifier = Modifier.fillMaxSize()) {
            ReaderShell(
                background = readerSettings.background.containerColor(),
                horizontalSwipeEnabled = pagedMode && readerSettings.horizontalSwipePagingEnabled &&
                    pagingEnabled && readerSettings.pageAnimation != ReaderPageAnimation.HORIZONTAL_SLIDE,
                onHorizontalSwipe = ::turnReaderPage,
                onReadingAreaTap = { xFraction, _ ->
                    if (progressPreview != null) {
                        dismissProgressPreview()
                    } else {
                        val forward = readerTapPageDirection(
                            xFraction, readerSettings.tapPagingEnabled, readerSettings.leftTapForward
                        )
                        if (showReaderSettings) showReaderSettings = false
                        else if (forward == null) showToolbar = !showToolbar
                        else turnReaderPage(forward)
                    }
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!pagedMode || state !is ChapterPageModel.State.Result) {
                    LazyColumn(
                        state = scrollState,
                        userScrollEnabled = !pagedMode,
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
                                top = pageTopPadding,
                                bottom = pageBottomPadding
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
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
                                                Uri.parse(EsjzoneUrls.resolve(readerResult.verificationChapter!!.url)))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
                                                Uri.parse(EsjzoneUrls.resolve(readerResult.verificationChapter!!.url)))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
                    } else {
                        HorizontalPager(
                            state = horizontalPagerState,
                            // Keep glyph overhang inside the pager's outer clip, in the page margins.
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = readerSettings.horizontalPaddingDp.dp
                            ),
                            pageSpacing = readerSettings.horizontalPaddingDp.dp * 2,
                            key = { index -> currentPagedDisplayItems.getOrNull(index)?.key ?: index },
                            userScrollEnabled = readerSettings.horizontalSwipePagingEnabled && pagingEnabled &&
                                readerSettings.pageAnimation == ReaderPageAnimation.HORIZONTAL_SLIDE,
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    translationX = pageTranslation.value
                                    alpha = pageAlpha.value
                                }
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                                .onSizeChanged { size ->
                                    if (readerViewport != size) {
                                        if (!resumePending && pendingSeekLocation == null) {
                                            pendingSeekLocation = currentBookLocation
                                        }
                                        readerViewport = size
                                    }
                                }
                        ) { pageIndex ->
                            currentPagedDisplayItems.getOrNull(pageIndex)?.page?.let { page ->
                                Box(
                                    Modifier.fillMaxSize()
                                        .padding(top = pageTopPadding, bottom = pageBottomPadding)
                                ) {
                                    ReaderPageContent(
                                        page, readerSettings, readerTextStyle,
                                        readerContentColor, readerTextTransform,
                                        with(density) { pageContentHeightPx.toDp() }
                                    )
                                }
                            }
                        }
                    }
                    if (pagedMode && result != null) {
                        val atPreviousBoundary = horizontalPagerState.currentPage <= 1
                        val atNextBoundary = horizontalPagerState.currentPage >= displayItems.lastIndex - 1
                        val verificationAtBoundary = result.verificationChapter != null &&
                            ((result.verificationOffset < 0 && atPreviousBoundary) ||
                                (result.verificationOffset > 0 && atNextBoundary))
                        if (verificationAtBoundary) {
                            Surface(
                                modifier = Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 560.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                ReaderWenkuVerificationState(
                                    unavailable = result.verificationWebViewUnavailable,
                                    storageUnavailable = result.verificationStorageUnavailable,
                                    onVerify = { wenkuVerificationChapter = result.verificationChapter },
                                    onBrowser = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW,
                                            Uri.parse(EsjzoneUrls.resolve(result.verificationChapter!!.url)))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    },
                                    onRetry = {
                                        dismissedWenkuPrompt = null
                                        chapterPageModel.retryPendingVerification()
                                    }
                                )
                            }
                        } else if (!paginationReady ||
                            (atPreviousBoundary && result.isLoadingPrevious) ||
                            (atNextBoundary && result.isLoadingNext)) {
                            CircularProgressIndicator(
                                Modifier.align(Alignment.Center).semantics {
                                    contentDescription = readerLoadingDescription
                                }, strokeWidth = 2.5.dp
                            )
                        }
                    }
                }
            }

            if (readerSettings.eyeProtectionEnabled) {
                Box(Modifier.fillMaxSize().background(Color(0x22FFC878)))
            }

            ReaderStatusBar(
                novelName = novelName,
                chapterName = currentChapterName,
                chapterIndex = currentBookLocation?.chapterIndex ?: -1,
                totalChapters = currentBookLocation?.totalChapters ?: bookChapterOrder.size,
                contentColor = readerContentColor,
                showChapterName = readerSettings.showChapterName,
                showTimeBattery = readerSettings.showTimeBattery,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { readerStatusHeightPx = it.height }
            )

            if ((state as? ChapterPageModel.State.Result)?.isOffline == true) {
                AppGlassSurface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
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
                        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
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
                    .onSizeChanged { readerToolbarHeightPx = it.height }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (showReaderSettings) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
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
                            shape = if (showReaderSettings) RoundedCornerShape(0.dp)
                                else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            alpha = if (showReaderSettings) 1f else 0.94f,
                            borderAlpha = if (showReaderSettings) 0f else 0.14f,
                            tintAlpha = if (showReaderSettings) 0f else 0.16f,
                            specularIntensity = if (showReaderSettings) 0f else 0.4f
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
                                        val visible = if (pagedMode) null else scrollState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { item -> item.key.toString() in displayByKey }
                                        val visibleKey = if (pagedMode) {
                                            displayItems.getOrNull(horizontalPagerState.currentPage)?.key
                                        } else visible?.key?.toString()
                                        commentReturnKey = visibleKey
                                        commentReturnChapterUrl = visibleKey
                                            ?.let { displayByKey[it]?.entry?.chapter?.url }
                                        commentReturnChapterName = visibleKey
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
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
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

            val readerChapters = chapterPageModel.chapterOrderState.value
                .ifEmpty { result?.chapterOrder.orEmpty() }
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
                onSettingsChange = { updated ->
                    if (updated.pageAnimation != readerSettings.pageAnimation ||
                        updated.font != readerSettings.font ||
                        updated.fontSizeSp != readerSettings.fontSizeSp ||
                        updated.letterSpacingSp != readerSettings.letterSpacingSp ||
                        updated.lineSpacingSp != readerSettings.lineSpacingSp ||
                        updated.paragraphSpacingDp != readerSettings.paragraphSpacingDp ||
                        updated.horizontalPaddingDp != readerSettings.horizontalPaddingDp ||
                        updated.script != readerSettings.script) {
                        pendingSeekLocation = currentBookLocation
                    }
                    updateReaderSettings(updated)
                },
                onDismiss = { showReaderSettings = false },
                onMoreSettings = { showMoreReaderSettings = true },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = with(density) { readerToolbarHeightPx.toDp() })
            )
            if (showMoreReaderSettings) ReaderMoreSettingsDialog(
                settings = readerSettings,
                onSettingsChange = ::updateReaderSettings,
                onDismiss = { showMoreReaderSettings = false }
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
            displayItems,
            pagedMode,
            hasPreviousVerification
        ) {
            if (pagedMode) return@LaunchedEffect
            var previousSnapshot: ReaderScrollSnapshot? = null
            snapshotFlow {
                val visibleChapterItems = scrollState.layoutInfo.visibleItemsInfo
                    .filter { it.key.toString() in displayByKey }
                val layoutMatchesLoadedWindow = visibleChapterItems.all { item ->
                    displayItems.getOrNull(item.index - readerListIndex(0))?.key == item.key
                }
                ReaderScrollSnapshot(
                    firstVisibleIndex = scrollState.firstVisibleItemIndex,
                    firstVisibleOffset = scrollState.firstVisibleItemScrollOffset,
                    firstVisibleChapterKey = visibleChapterItems.firstOrNull()
                        ?.takeIf { it.index == readerListIndex(0) }
                        ?.let { displayByKey[it.key.toString()]?.chapterKey },
                    lastVisibleChapterKey = visibleChapterItems.lastOrNull()
                        ?.let { displayByKey[it.key.toString()]?.chapterKey },
                    distanceToLoadedTail = visibleChapterItems.lastOrNull()
                        ?.takeIf { it.index == readerListIndex(displayItems.lastIndex) }?.let { item ->
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

private data class ReaderPaginationSnapshot(
    val key: List<Any?>,
    val layoutKey: List<Any?>,
    val pages: Map<ReaderChapter, List<ReaderPage>>,
    val items: List<ReaderDisplayItem>
)

private data class ReaderDisplayItem(
    val key: String,
    val chapterKey: String,
    val entry: ReaderChapter,
    val ordinal: Int,
    val itemCount: Int,
    val blocks: List<ReaderBlock>,
    val page: ReaderPage? = null
)

private data class ReaderBookLocation(
    val chapter: Chapter,
    val chapterIndex: Int,
    val chapterProgress: Float,
    val totalChapters: Int,
    val contentPosition: Float? = null
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
    contentColor: Color,
    showChapterName: Boolean,
    showTimeBattery: Boolean,
    modifier: Modifier = Modifier
) {
    val title = listOf(novelName.trim(), chapterName.trim())
        .filter(String::isNotBlank)
        .joinToString(" · ")
    val statusColor = contentColor.copy(alpha = 0.56f)
    val context = LocalContext.current
    var clockAndBattery by remember { mutableStateOf("") }
    LaunchedEffect(showTimeBattery) {
        if (!showTimeBattery) return@LaunchedEffect
        while (true) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val battery = context.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
            clockAndBattery = if (battery != null) "$time  $battery%" else time
            delay(30_000L)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xs)
            .zIndex(1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showChapterName) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = statusColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else Spacer(Modifier.weight(1f))
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
        if (showTimeBattery && clockAndBattery.isNotBlank()) {
            Spacer(modifier = Modifier.size(12.dp))
            Text(clockAndBattery, color = statusColor, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
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
                                .padding(vertical = AppSpacing.xxs),
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
                                    vertical = AppSpacing.sm
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
