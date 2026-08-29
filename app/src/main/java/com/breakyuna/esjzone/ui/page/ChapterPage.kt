package com.breakyuna.esjzone.ui.page

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.layout.onSizeChanged
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
import cafe.adriel.voyager.core.screen.ScreenKey
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.reader.ReaderBackground
import com.breakyuna.esjzone.ui.reader.ReaderFont
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.reader.ReaderSettingsStore
import com.breakyuna.esjzone.util.AppLogger

class ChapterPage(
    private val novelId: String,
    private val chapter: Chapter,
    private val history: MutableState<Chapter?>,
    private val chapterOrder: List<Chapter> = emptyList()
) : Screen {

    override val key: ScreenKey =
        "ChapterPage:" +
            novelId.trim().ifBlank { chapter.novelId() } +
            ":" +
            chapterIdentity(chapter)

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current

        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        var readerSettings by remember(context) {
            mutableStateOf(ReaderSettingsStore.load(context))
        }
        var showReaderSettings by rememberSaveable {
            mutableStateOf(false)
        }

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
                    scope = scope,
                    requestedChapter = requestedChapter,
                    novelId = novelId,
                    chapterOrder = chapterOrder
                )
            }
        val state by chapterPageModel.state.collectAsState()

        BackHandler(enabled = navigator != null && !showReaderSettings) {
            navigator?.pop()
        }

        var showToolbar by remember {
            mutableStateOf(false)
        }

        val scrollState = rememberScrollState()

        var sliderPosition by remember { mutableFloatStateOf(0f) }
        var isSliderDragging by remember { mutableStateOf(false) }
        val chapterHeights = remember { mutableStateMapOf<String, Int>() }

        val continuousLoadThreshold = with(LocalDensity.current) { 720.dp.toPx().toInt() }
        val chapterActivationOffset = with(density) { 56.dp.toPx() }
        val firstChapterTop = with(density) { 32.dp.toPx().roundToInt() }
        val result = state as? ChapterPageModel.State.Result
        val chapterLayouts by remember(result, chapterHeights, firstChapterTop) {
            derivedStateOf {
                buildReaderChapterLayouts(
                    chapters = result?.chapters.orEmpty(),
                    heights = chapterHeights,
                    firstTop = firstChapterTop
                )
            }
        }
        val activeChapterIndex by remember(result, chapterLayouts, scrollState, chapterActivationOffset) {
            derivedStateOf {
                if (result == null) {
                    0
                } else {
                    val marker = scrollState.value.toFloat() + chapterActivationOffset
                    result.chapters.indexOfLast { entry ->
                        chapterLayouts[entry.chapter.url]?.let { layout ->
                            layout.height > 0 && layout.top <= marker
                        } == true
                    }.coerceAtLeast(0)
                }
            }
        }
        val activeChapter = result?.chapters?.getOrNull(activeChapterIndex)
        val activeLayout = activeChapter?.let { chapterLayouts[it.chapter.url] }
        val chapterProgress = chapterProgressFor(scrollState.value, activeLayout)
        val displayedProgress = if (isSliderDragging) sliderPosition else chapterProgress
        val currentChapterName = activeChapter?.chapter?.name ?: requestedChapter.value.name

        LaunchedEffect(requestedChapter.value.url) {
            chapterHeights.clear()
            sliderPosition = 0f
            scrollState.scrollTo(0)
        }

        LaunchedEffect(scrollState, activeChapterIndex, activeLayout, isSliderDragging) {
            if (isSliderDragging) return@LaunchedEffect
            snapshotFlow { scrollState.value }.collect { scrollValue ->
                sliderPosition = chapterProgressFor(scrollValue, activeLayout)
            }
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
                            title = currentChapterName,
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                FilledTonalIconButton(
                                    onClick = { showReaderSettings = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = stringResource(
                                            id = R.string.reader_settings
                                        )
                                    )
                                }
                            }

                            if (state is ChapterPageModel.State.Result) {
                                var rememberedHistory by rememberSaveable {
                                    history
                                }
                                val readerResult = state as ChapterPageModel.State.Result

                                val activePrevious = if (activeChapterIndex > 0) {
                                    readerResult.chapters.getOrNull(activeChapterIndex - 1)?.chapter
                                } else {
                                    readerResult.previous
                                }
                                val activeNext = readerResult.chapters
                                    .getOrNull(activeChapterIndex + 1)?.chapter
                                    ?: readerResult.next

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    FilledTonalButton(
                                        enabled = activePrevious != null,
                                        onClick = {
                                            activePrevious?.let { previous ->
                                                if (novelId == previous.novelId()) {
                                                    rememberedHistory = previous
                                                }
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
                                        text = "${(displayedProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    FilledTonalButton(
                                        enabled = activeNext != null,
                                        onClick = {
                                            activeNext?.let { next ->
                                                if (novelId == next.novelId()) {
                                                    rememberedHistory = next
                                                }
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
                                    value = displayedProgress,
                                    onValueChange = {
                                        isSliderDragging = true
                                        sliderPosition = it
                                        activeLayout?.let { layout ->
                                            val scrollTo = (layout.top + layout.height * it)
                                                .roundToInt()
                                                .coerceIn(0, scrollState.maxValue)
                                            scope.launch(Dispatchers.Main) {
                                                scrollState.animateScrollTo(scrollTo)
                                            }
                                        }
                                    },
                                    onValueChangeFinished = {
                                        isSliderDragging = false
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                val commentChapter = activeChapter?.chapter
                                    ?: requestedChapter.value
                                FilledTonalButton(
                                    onClick = {
                                        navigator?.pushIfNotCurrent(
                                            ChapterCommentsPage(
                                                chapterName = commentChapter.name,
                                                chapterUrl = commentChapter.url
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Forum,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(id = R.string.comments))
                                }
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
                color = readerSettings.background.containerColor()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = readerSettings.horizontalPaddingDp.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(32.dp))

                    when (state) {
                        is ChapterPageModel.State.Loading -> Column {
                            ChapterHeading(currentChapterName, readerSettings)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.5.dp)
                            }
                        }

                        is ChapterPageModel.State.Error -> Column {
                            ChapterHeading(currentChapterName, readerSettings)
                            Box(
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
                        }

                        is ChapterPageModel.State.Result -> {
                            val readerResult = state as ChapterPageModel.State.Result
                            for ((index, entry) in readerResult.chapters.withIndex()) {
                                ReaderChapterBlock(
                                    entry = entry,
                                    index = index,
                                    textMeasurer = textMeasurer,
                                    textStyle = readerTextStyle,
                                    density = density,
                                    settings = readerSettings,
                                    contentColor = readerContentColor,
                                    onSizeChanged = { height ->
                                        chapterHeights[entry.chapter.url] = height
                                    }
                                )
                            }

                            if (readerResult.isLoadingNext) {
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

        LaunchedEffect(Unit) {
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

        LaunchedEffect(activeChapter?.chapter?.url) {
            activeChapter?.chapter?.let { current ->
                if (novelId == current.novelId()) {
                    history.value = current
                }
            }
        }

        if (showReaderSettings) {
            ReaderSettingsSheet(
                settings = readerSettings,
                onSettingsChange = { updated -> updateReaderSettings(updated) },
                onDismiss = { showReaderSettings = false }
            )
        }
    }

}

private data class ReaderChapterLayout(
    val top: Int,
    val height: Int
)

private fun buildReaderChapterLayouts(
    chapters: List<ReaderChapter>,
    heights: Map<String, Int>,
    firstTop: Int
): Map<String, ReaderChapterLayout> {
    val layouts = mutableMapOf<String, ReaderChapterLayout>()
    var top = firstTop
    for (entry in chapters) {
        val height = heights[entry.chapter.url] ?: 0
        layouts[entry.chapter.url] = ReaderChapterLayout(top = top, height = height)
        top += height
    }
    return layouts
}

private fun chapterProgressFor(
    scrollValue: Int,
    layout: ReaderChapterLayout?
): Float {
    if (layout == null || layout.height <= 0) return 0f
    return ((scrollValue - layout.top).toFloat() / layout.height.toFloat())
        .coerceIn(0f, 1f)
}

@Composable
private fun ChapterHeading(name: String, settings: ReaderSettings) {
    Text(
        text = name,
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
private fun ReaderChapterBlock(
    entry: ReaderChapter,
    index: Int,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: androidx.compose.ui.text.TextStyle,
    density: Density,
    settings: ReaderSettings,
    contentColor: androidx.compose.ui.graphics.Color,
    onSizeChanged: (height: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size -> onSizeChanged(size.height) }
    ) {
        if (index > 0) {
            Spacer(modifier = Modifier.height(settings.pageSpacingDp.dp))
        }
        ChapterHeading(entry.chapter.name, settings)
        ChapterContent(
            detail = entry.detail,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            density = density,
            settings = settings,
            contentColor = contentColor
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
    contentColor: androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
    private var initialLoadStarted = false

    fun getDetail() {
        synchronized(lock) {
            if (initialLoadStarted) return
            initialLoadStarted = true
        }
        openChapter(requestedChapter.value)
    }

    fun openChapter(chapter: Chapter) {
        requestedChapter.value = chapter
        var currentSession = 0L
        var jobsToCancel = emptyList<Job>()
        synchronized(lock) {
            sessionId += 1
            currentSession = sessionId
            jobsToCancel = buildList {
                initialJob?.let(::add)
                appendJob?.let(::add)
                orderJob?.let(::add)
                addAll(prefetchJobs.values)
            }.distinct()
            initialJob = null
            appendJob = null
            orderJob = null
            orderRequestId += 1
            loadedChapters.clear()
            prefetchedDetails.clear()
            loadingNext = false
            orderLoading = false
            pendingNextRequest = false
        }
        // Cancel outside the model lock: cancellation handlers may publish or
        // remove their own entries while unwinding.
        jobsToCancel.forEach(Job::cancel)
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
            val last = snapshot.lastOrNull()
            previous = first?.let { adjacentChapter(it.chapter, -1, it.detail) }
            next = last?.let { adjacentChapter(it.chapter, 1, it.detail) }
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

    private fun chapterKey(chapter: Chapter): String = chapterIdentity(chapter)

    private fun normalizeChapterOrder(chapters: List<Chapter>): List<Chapter> =
        chapters.asSequence()
            .filter { chapterKey(it).isNotBlank() }
            .distinctBy { chapterKey(it) }
            .toList()
}

private fun chapterIdentity(chapter: Chapter): String =
    EsjzoneUrls.canonicalPageKey(chapter.url).takeIf { it.isNotBlank() && it != "/" }
        ?: chapter.name.trim()
