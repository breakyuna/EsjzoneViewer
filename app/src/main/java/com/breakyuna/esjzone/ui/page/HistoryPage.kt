package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getHistories
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.features.removeHistory
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.Loading
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent

object HistoryPage : Screen {

    private fun readResolve(): Any = HistoryPage

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        Content(showBack = true)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Content(showBack: Boolean) {
        val navigator = LocalBaseNavigator.current

        val authorization = LocalAuthorization.current
        val scope = rememberCoroutineScope()

        val historyPageModel = rememberScreenModel { HistoryPageModel(authorization) }
        val state by historyPageModel.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            if (showBack) {
                AppBar(
                    title = stringResource(id = R.string.history),
                    onBack = {
                        navigator?.pop()
                    }
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.history),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(id = R.string.history_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            when (state) {
                is HistoryPageModel.State.Loading -> Loading()

                is HistoryPageModel.State.Result -> {
                    val result = state as HistoryPageModel.State.Result

                    val novels = result.historyNovels.distinctBy { it.url.ifBlank { it.name } }

                    val cache = remember {
                        mutableStateMapOf<String, DetailedNovel>()
                    }

                    val adult by remember {
                        GlobalSettings.adult
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(
                            novels,
                            key = { item -> item.url.ifBlank { item.name } }
                        ) { historyNovel ->
                            var detailedNovel: DetailedNovel? by remember {
                                mutableStateOf(cache[historyNovel.url])
                            }

                            val historyChapter: MutableState<Chapter?> = rememberSaveable {
                                mutableStateOf(historyNovel.chapter)
                            }

                            val rememberedHistory by rememberSaveable {
                                historyChapter
                            }

                            val novel = detailedNovel
                            if (novel == null) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                }

                                LaunchedEffect(historyNovel.url) {
                                    try {
                                        val fetched = withContext(Dispatchers.IO) {
                                            EsjzoneClient.getNovelDetail(
                                                authorization,
                                                historyNovel
                                            )
                                        }
                                        detailedNovel = fetched
                                        cache[historyNovel.url] = fetched
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        com.breakyuna.esjzone.util.AppLogger.e("HistoryPage", "Failed to load novel detail for ${historyNovel.name}", e)
                                    }
                                }
                            } else {
                                if (adult || !novel.isAdult) {
                                    var deleted by remember {
                                        mutableStateOf(false)
                                    }
                                    var expanded by remember { mutableStateOf(false) }

                                    if (!deleted) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = {
                                                    expanded = false
                                                }
                                            ) {
                                                DropdownMenuItem(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                withContext(Dispatchers.IO) {
                                                                    EsjzoneClient.removeHistory(
                                                                        authorization,
                                                                        historyNovel.vid
                                                                    )
                                                                }
                                                                deleted = true
                                                            } catch (e: CancellationException) {
                                                                throw e
                                                            } catch (e: Exception) {
                                                                com.breakyuna.esjzone.util.AppLogger.e(
                                                                    "HistoryPage",
                                                                    "Failed to remove history for ${historyNovel.name}",
                                                                    e
                                                                )
                                                            }
                                                        }
                                                        expanded = false
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = R.string.delete_history))
                                                    }
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .combinedClickable(
                                                            onClick = {
                                                                navigator?.pushIfNotCurrent(
                                                                    NovelPage(
                                                                        historyNovel,
                                                                        history = ChapterStateHolder(historyChapter)
                                                                    )
                                                                )
                                                            },
                                                            onLongClick = {
                                                                expanded = true
                                                            }
                                                        ),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(72.dp)
                                                            .height(100.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(MaterialTheme.colorScheme.surface)
                                                    ) {
                                                        SubcomposeAsyncImage(
                                                            model = ImageRequest.Builder(LocalContext.current)
                                                                .data(
                                                                    EsjzoneUrls.coverOrEmpty(novel.coverUrl)
                                                                        .takeIf { it.isNotBlank() }
                                                                        ?: R.drawable.missing_cover
                                                                )
                                                                .crossfade(true)
                                                                .build(),
                                                            contentDescription = historyNovel.name,
                                                            imageLoader = MainActivity.imageLoader,
                                                            loading = {
                                                                CircularProgressIndicator(strokeWidth = 2.dp)
                                                            },
                                                            error = {
                                                                androidx.compose.foundation.Image(
                                                                    painter = androidx.compose.ui.res.painterResource(
                                                                        id = R.drawable.missing_cover
                                                                    ),
                                                                    contentDescription = historyNovel.name,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            },
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(12.dp))

                                                    Column {
                                                        Text(
                                                            text = novel.name,
                                                            style = MaterialTheme.typography.titleSmall.copy(
                                                                fontWeight = FontWeight.SemiBold
                                                            ),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            text = historyChapter.value?.name ?: "",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                FilledTonalIconButton(
                                                    onClick = {
                                                        rememberedHistory?.let { currChapter ->
                                                            navigator?.pushIfNotCurrent(
                                                                ChapterPage(
                                                                    novel.id(),
                                                                    currChapter,
                                                                    ChapterStateHolder(historyChapter),
                                                                    novel.chapterList.orderedChapters,
                                                                    novelName = novel.name
                                                                )
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PlayArrow,
                                                        contentDescription = stringResource(id = R.string.continue_reading)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = stringResource(id = R.string.the_end),
                                    modifier = Modifier.padding(16.dp)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            historyPageModel.getNovels()
        }
    }

}

class HistoryPageModel(
    private val authorization: Authorization
) : StateScreenModel<HistoryPageModel.State>(State.Loading) {

    private var loadJob: Job? = null
    private var loadStarted = false

    sealed class State {
        data object Loading : State()
        data class Result(val historyNovels: List<HistoryNovel>) : State()
    }

    fun getNovels() {
        if (loadStarted) return
        loadStarted = true
        loadJob?.cancel()
        loadJob = screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val histories = EsjzoneClient.getHistories(authorization)
                ensureActive()
                mutableState.value = State.Result(histories)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e("HistoryPageModel", "Failed to load histories", e)
            }
        }
    }

}
