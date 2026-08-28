package com.breakyuna.esjzone.ui.page

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getChapterDetail
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.util.AppLogger

class ChapterPage(
    private val novelId: String,
    private val chapter: Chapter,
    private val history: MutableState<Chapter?>,
    private val chapterOrder: List<Chapter> = emptyList()
) : Screen {

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current

        val textMeasurer = rememberTextMeasurer()
        val textStyle = LocalTextStyle.current
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()

        val requestedChapter = rememberSaveable {
            mutableStateOf(chapter)
        }

        val chapterPageModel =
            rememberScreenModel {
                ChapterPageModel(
                    authorization = authorization,
                    scope = scope,
                    requestedChapter = requestedChapter,
                    novelId = novelId,
                    chapterOrder = chapterOrder
                )
            }
        val state by chapterPageModel.state.collectAsState()

        var showToolbar by remember {
            mutableStateOf(false)
        }

        val scrollState = rememberScrollState()

        val maxScroll by remember {
            derivedStateOf {
                scrollState.maxValue
            }
        }

        var sliderPosition by remember { mutableFloatStateOf(0f) }

        var chapterName by remember {
            mutableStateOf(chapter.name)
        }

        val continuousLoadThreshold = with(LocalDensity.current) { 720.dp.toPx().toInt() }

        if (scrollState.isScrollInProgress && scrollState.maxValue > 0) {
            sliderPosition = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        }

        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = showToolbar,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 4.dp
                    ) {
                        AppBar(
                            title = chapterName,
                            onBack = {
                                navigator?.pop()
                            }
                        )
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = showToolbar,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            if (state is ChapterPageModel.State.Result) {
                                var rememberedHistory by rememberSaveable {
                                    history
                                }

                                val result = state as ChapterPageModel.State.Result

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    FilledTonalButton(
                                        enabled = result.previous != null,
                                        onClick = {
                                            result.previous?.let { previous ->
                                                if (novelId == previous.novelId()) {
                                                    rememberedHistory = previous
                                                }
                                                chapterName = previous.name
                                                chapterPageModel.openChapter(previous)
                                                scope.launch(Dispatchers.Main) {
                                                    scrollState.scrollTo(0)
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = stringResource(id = R.string.previous_chapter))
                                    }

                                    Text(
                                        text = "${(sliderPosition * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    FilledTonalButton(
                                        enabled = result.next != null,
                                        onClick = {
                                            result.next?.let { next ->
                                                if (novelId == next.novelId()) {
                                                    rememberedHistory = next
                                                }
                                                chapterName = next.name
                                                chapterPageModel.openChapter(next)
                                                scope.launch(Dispatchers.Main) {
                                                    scrollState.scrollTo(0)
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(text = stringResource(id = R.string.next_chapter))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Slider(
                                    value = sliderPosition,
                                    onValueChange = {
                                        sliderPosition = it
                                        val scrollTo = (maxScroll * it).toInt()
                                        scope.launch(Dispatchers.Main) {
                                            scrollState.animateScrollTo(scrollTo)
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { showToolbar = !showToolbar },
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = chapterName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 34.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    when (state) {
                        is ChapterPageModel.State.Loading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.5.dp)
                        }

                        is ChapterPageModel.State.Error -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.chapter_load_failed),
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        is ChapterPageModel.State.Result -> Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val result = state as ChapterPageModel.State.Result
                            for ((index, entry) in result.chapters.withIndex()) {
                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(32.dp))
                                    Text(
                                        text = entry.chapter.name,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 34.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(bottom = 24.dp)
                                    )
                                }

                                ChapterContent(
                                    detail = entry.detail,
                                    textMeasurer = textMeasurer,
                                    textStyle = textStyle,
                                    density = density
                                )
                            }

                            if (result.isLoadingNext) {
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

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        LaunchedEffect(currentCompositeKeyHash) {
            chapterPageModel.getDetail()
        }

        LaunchedEffect(scrollState, chapterPageModel, continuousLoadThreshold) {
            snapshotFlow { scrollState.value to scrollState.maxValue }
                .collect { (value, maxValue) ->
                    if (maxValue > 0 && value > 0 && maxValue - value <= continuousLoadThreshold) {
                        chapterPageModel.loadNextChapter()
                    }
                }
        }

        LaunchedEffect(state) {
            val result = state as? ChapterPageModel.State.Result
            if (result != null && result.chapters.size > 1) {
                history.value = result.chapters.last().chapter
            }
        }
    }

}

@Composable
private fun ChapterContent(
    detail: DetailedChapter,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: androidx.compose.ui.text.TextStyle,
    density: Density
) {
    for (component in detail.content) {
        if (component is TextComponent) {
            val (str, inlines) = component.toInlineAnnotatedString(
                textMeasurer,
                textStyle,
                density
            )
            Text(
                text = str,
                inlineContent = inlines,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 28.sp,
                    letterSpacing = 0.3.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
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
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

data class ReaderChapter(
    val chapter: Chapter,
    val detail: DetailedChapter
)

class ChapterPageModel(
    private val authorization: Authorization,
    private val scope: CoroutineScope,
    private val requestedChapter: MutableState<Chapter>,
    private val novelId: String,
    chapterOrder: List<Chapter>
) : StateScreenModel<ChapterPageModel.State>(State.Loading) {

    sealed class State {
        data object Loading : State()
        data object Error : State()
        data class Result(
            val chapters: List<ReaderChapter>,
            val previous: Chapter?,
            val next: Chapter?,
            val isLoadingNext: Boolean
        ) : State()
    }

    private val lock = Any()
    private val loadedChapters = mutableListOf<ReaderChapter>()
    private val prefetchedDetails = mutableMapOf<String, DetailedChapter>()
    private val prefetchJobs = mutableMapOf<String, Job>()
    private var orderedChapters = normalizeChapterOrder(chapterOrder)
    private var orderResolved = orderedChapters.isNotEmpty()
    private var sessionId = 0L
    private var initialJob: Job? = null
    private var appendJob: Job? = null
    private var orderJob: Job? = null
    private var orderRequestId = 0L
    private var loadingNext = false
    private var orderLoading = false
    private var pendingNextRequest = false

    fun getDetail() {
        openChapter(requestedChapter.value)
    }

    fun openChapter(chapter: Chapter) {
        requestedChapter.value = chapter
        var currentSession = 0L
        synchronized(lock) {
            sessionId += 1
            currentSession = sessionId
            initialJob?.cancel()
            appendJob?.cancel()
            orderJob?.cancel()
            orderRequestId += 1
            loadedChapters.clear()
            loadingNext = false
            orderLoading = false
            pendingNextRequest = false
        }
        mutableState.value = State.Loading

        initialJob = scope.launch(Dispatchers.IO) {
            val detail = loadDetail(chapter)
            if (!isCurrentSession(currentSession)) return@launch
            if (detail == null) {
                mutableState.value = State.Error
                return@launch
            }

            synchronized(lock) {
                if (isCurrentSessionLocked(currentSession)) {
                    loadedChapters += ReaderChapter(chapter, detail)
                }
            }
            publish(currentSession)

            val hasCanonicalOrder = synchronized(lock) { orderResolved }
            if (hasCanonicalOrder || novelId.isBlank()) {
                prefetchNext(chapter, detail)
            } else {
                requestChapterOrder(currentSession, chapter, detail)
            }
        }
    }

    /** Loads the next canonical TOC chapter when the reader reaches the end buffer. */
    fun loadNextChapter() {
        val shouldWaitForOrder = synchronized(lock) {
            !orderResolved && novelId.isNotBlank()
        }
        if (shouldWaitForOrder) {
            synchronized(lock) {
                pendingNextRequest = true
            }
            requestChapterOrder(sessionId, null, null)
            return
        }

        var currentSession: Long? = null
        var nextChapter: Chapter? = null
        synchronized(lock) {
            if (loadingNext || loadedChapters.isEmpty()) return
            val last = loadedChapters.last()
            val candidate = adjacentChapter(last.chapter, 1, last.detail) ?: return
            if (loadedChapters.any { sameChapter(it.chapter, candidate) }) return
            nextChapter = candidate
            loadingNext = true
            currentSession = sessionId
        }
        val chapterToLoad = nextChapter ?: return
        val session = currentSession ?: return
        publish(session)

        appendJob = scope.launch(Dispatchers.IO) {
            try {
                val detail = loadDetail(chapterToLoad)
                if (detail != null && isCurrentSession(session)) {
                    synchronized(lock) {
                        if (isCurrentSessionLocked(session) &&
                            loadedChapters.none { sameChapter(it.chapter, chapterToLoad) }
                        ) {
                            loadedChapters += ReaderChapter(chapterToLoad, detail)
                        }
                    }
                    publish(session)
                    prefetchNext(chapterToLoad, detail)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(
                    "ChapterPageModel",
                    "Failed to append chapter ${chapterToLoad.name}",
                    e
                )
            } finally {
                synchronized(lock) {
                    if (isCurrentSessionLocked(session)) {
                        loadingNext = false
                    }
                }
                publish(session)
            }
        }
    }

    private fun requestChapterOrder(
        currentSession: Long,
        chapter: Chapter?,
        detail: DetailedChapter?
    ) {
        var requestId = 0L
        synchronized(lock) {
            if (orderResolved || orderLoading) return
            orderLoading = true
            orderRequestId += 1
            requestId = orderRequestId
            orderJob = scope.launch(Dispatchers.IO) {
                try {
                    ensureChapterOrder()
                    if (!isCurrentSession(currentSession)) return@launch
                    publish(currentSession)
                    if (chapter != null && detail != null) {
                        prefetchNext(chapter, detail)
                    }
                    val shouldLoadNext = synchronized(lock) {
                        val requested = pendingNextRequest
                        pendingNextRequest = false
                        requested
                    }
                    if (shouldLoadNext) loadNextChapter()
                } finally {
                    synchronized(lock) {
                        if (orderRequestId == requestId) {
                            orderLoading = false
                            orderJob = null
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadDetail(chapter: Chapter): DetailedChapter? {
        val key = chapterKey(chapter)
        val prefetched = synchronized(lock) { prefetchedDetails.remove(key) }
        if (prefetched != null) return prefetched

        val prefetchJob = synchronized(lock) { prefetchJobs[key] }
        if (prefetchJob != null) {
            try {
                prefetchJob.join()
            } catch (e: CancellationException) {
                throw e
            }
            synchronized(lock) { prefetchedDetails.remove(key) }?.let { return it }
        }

        // The prefetch can finish between the first cache check and job lookup.
        // Check one more time before issuing a duplicate request.
        synchronized(lock) { prefetchedDetails.remove(key) }?.let { return it }

        return try {
            EsjzoneClient.getChapterDetail(authorization, chapter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(
                "ChapterPageModel",
                "Failed to load chapter detail for ${chapter.name}",
                e
            )
            null
        }
    }

    private fun prefetchNext(chapter: Chapter, detail: DetailedChapter) {
        adjacentChapter(chapter, 1, detail)?.let(::prefetch)
    }

    private fun prefetch(chapter: Chapter) {
        val key = chapterKey(chapter)
        if (key.isBlank()) return
        synchronized(lock) {
            if (loadedChapters.any { sameChapter(it.chapter, chapter) } ||
                prefetchedDetails.containsKey(key) || prefetchJobs.containsKey(key)
            ) {
                return
            }
            prefetchJobs[key] = scope.launch(Dispatchers.IO) {
                val detail = try {
                    EsjzoneClient.getChapterDetail(authorization, chapter)
                } catch (e: CancellationException) {
                    synchronized(lock) {
                        prefetchJobs.remove(key)
                    }
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(
                        "ChapterPageModel",
                        "Prefetch failed for chapter ${chapter.name}",
                        e
                    )
                    null
                }
                synchronized(lock) {
                    if (detail != null) prefetchedDetails[key] = detail
                    prefetchJobs.remove(key)
                }
            }
        }
    }

    private suspend fun ensureChapterOrder() {
        synchronized(lock) {
            if (orderResolved) return
            if (novelId.isBlank()) {
                orderResolved = true
                return
            }
        }

        val source = FavoriteNovel(
            name = "",
            url = "${EsjzoneUrls.Base}/detail/$novelId.html"
        )
        val fetchedOrder = try {
            EsjzoneClient.getNovelDetail(authorization, source)
                .chapterList
                .orderedChapters
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(
                "ChapterPageModel",
                "Failed to load canonical chapter order for novel $novelId",
                e
            )
            emptyList()
        }

        synchronized(lock) {
            if (fetchedOrder.isNotEmpty()) {
                orderedChapters = normalizeChapterOrder(fetchedOrder)
            }
            orderResolved = true
        }
    }

    private fun adjacentChapter(
        chapter: Chapter,
        offset: Int,
        fallback: DetailedChapter?
    ): Chapter? {
        val adjacent = synchronized(lock) {
            val index = orderedChapters.indexOfFirst { sameChapter(it, chapter) }
            if (index >= 0) orderedChapters.getOrNull(index + offset) else null
        }
        if (adjacent != null) return adjacent
        return if (offset > 0) fallback?.next else fallback?.previous
    }

    private fun publish(currentSession: Long? = null) {
        val snapshot: List<ReaderChapter>
        val previous: Chapter?
        val next: Chapter?
        val loading: Boolean
        synchronized(lock) {
            if (currentSession != null && !isCurrentSessionLocked(currentSession)) return
            snapshot = loadedChapters.toList()
            val first = snapshot.firstOrNull()
            previous = first?.let { adjacentChapter(it.chapter, -1, it.detail) }
            next = first?.let { adjacentChapter(it.chapter, 1, it.detail) }
            loading = loadingNext
        }
        if (snapshot.isNotEmpty()) {
            mutableState.value = State.Result(snapshot, previous, next, loading)
        }
    }

    private fun isCurrentSession(currentSession: Long): Boolean =
        synchronized(lock) { isCurrentSessionLocked(currentSession) }

    private fun isCurrentSessionLocked(currentSession: Long): Boolean = sessionId == currentSession

    private fun sameChapter(first: Chapter, second: Chapter): Boolean =
        chapterKey(first) == chapterKey(second)

    private fun chapterKey(chapter: Chapter): String =
        chapter.url.trim()
            .replaceFirst(Regex("^https?://[^/]+", RegexOption.IGNORE_CASE), "")
            .replaceFirst(Regex("^//[^/]+"), "")
            .substringBefore('#')

    private fun normalizeChapterOrder(chapters: List<Chapter>): List<Chapter> =
        chapters.asSequence()
            .filter { chapterKey(it).isNotBlank() }
            .distinctBy { chapterKey(it) }
            .toList()
}
