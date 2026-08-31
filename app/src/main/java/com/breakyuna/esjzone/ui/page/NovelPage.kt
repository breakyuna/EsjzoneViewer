package com.breakyuna.esjzone.ui.page

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.changeFavorites
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.offline.DownloadProgress
import com.breakyuna.esjzone.offline.DownloadedNovelManifest
import com.breakyuna.esjzone.offline.NovelDownloadManager
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.offline.NovelExporter
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.ChapterList
import com.breakyuna.esjzone.ui.component.Description
import com.breakyuna.esjzone.ui.component.Loading
import com.breakyuna.esjzone.ui.component.LoadError
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.BooleanStateHolder
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent

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
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        val screenModel = rememberScreenModel { NovelPageModel(authorization, novel) }
        val commentModel = rememberScreenModel {
            CommentPageModel(authorization, novel.url)
        }
        val state by screenModel.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            AppBar(
                title = novel.name,
                onBack = {
                    navigator?.pop()
                }
            )

            when (state) {
                is NovelPageModel.State.Loading -> Loading()

                is NovelPageModel.State.Error -> LoadError(
                    onRetry = screenModel::retry
                )

                is NovelPageModel.State.Result -> {
                    val result = state as NovelPageModel.State.Result
                    val chapterList = result.detailed.chapterList

                    val historyState = history.state()
                    historyState.value = chapterList.toRead

                    val hasHistory = rememberSaveable {
                        mutableStateOf(chapterList.hasHistory)
                    }

                    val rememberedHistory by historyState
                    val rememberedHasHistory by rememberSaveable { hasHistory }
                    val favoriteState = favorite.state()
                    var rememberedFavorite by rememberSaveable(novel.url) {
                        mutableStateOf(favoriteState.value)
                    }

                    LaunchedEffect(result.detailed.isFavorite) {
                        if (favoriteState.value == rememberedFavorite) {
                            favoriteState.value = result.detailed.isFavorite
                            rememberedFavorite = result.detailed.isFavorite
                        }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "novel-hero") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(
                                                    EsjzoneUrls.coverOrEmpty(result.detailed.coverUrl)
                                                        .takeIf { it.isNotBlank() }
                                                        ?: R.drawable.missing_cover
                                                )
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = result.detailed.name,
                                            imageLoader = MainActivity.imageLoader,
                                            loading = {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        strokeWidth = 2.dp,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            },
                                            error = {
                                                androidx.compose.foundation.Image(
                                                    painter = androidx.compose.ui.res.painterResource(
                                                        id = R.drawable.missing_cover
                                                    ),
                                                    contentDescription = result.detailed.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            },
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(112.dp)
                                                .height(156.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = result.detailed.name,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            NovelInfoRow(
                                                label = stringResource(id = R.string.novel_type),
                                                value = result.detailed.type
                                            )
                                            NovelInfoRow(
                                                label = stringResource(id = R.string.author),
                                                value = result.detailed.author,
                                                icon = Icons.Filled.Person
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                NovelStatRow(
                                                    icon = Icons.Filled.RemoveRedEye,
                                                    label = stringResource(
                                                        id = R.string.novel_views,
                                                        result.detailed.views
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                NovelStatRow(
                                                    icon = Icons.Filled.ThumbUp,
                                                    label = stringResource(
                                                        id = R.string.novel_likes,
                                                        result.detailed.likes
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                NovelStatRow(
                                                    icon = Icons.Filled.Topic,
                                                    label = stringResource(
                                                        id = R.string.novel_words,
                                                        result.detailed.words
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            result.detailed.sourceUrl?.let { sourceUrl ->
                                                NovelInfoRow(
                                                    label = stringResource(id = R.string.novel_source),
                                                    value = sourceUrl,
                                                    onClick = { openExternal(context, sourceUrl) }
                                                )
                                            }
                                            result.detailed.updatedAt?.let { updatedAt ->
                                                NovelInfoRow(
                                                    label = stringResource(id = R.string.novel_updated),
                                                    value = updatedAt
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = {
                                                    val previousFavorite = rememberedFavorite
                                                    val nextFavorite = !previousFavorite
                                                    favoriteState.value = nextFavorite
                                                    rememberedFavorite = nextFavorite
                                                    scope.launch {
                                                        try {
                                                            withContext(Dispatchers.IO) {
                                                                EsjzoneClient.changeFavorites(
                                                                    authorization,
                                                                    novel
                                                                )
                                                            }
                                                        } catch (error: CancellationException) {
                                                            throw error
                                                        } catch (error: Exception) {
                                                            favoriteState.value = previousFavorite
                                                            rememberedFavorite = previousFavorite
                                                            com.breakyuna.esjzone.util.AppLogger.e(
                                                                "NovelPage",
                                                                "Failed to change favorite for ${novel.name}",
                                                                error
                                                            )
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = ButtonDefaults.ContentPadding
                                            ) {
                                                Icon(
                                                    imageVector = if (rememberedFavorite) {
                                                        Icons.Filled.Favorite
                                                    } else {
                                                        Icons.Filled.FavoriteBorder
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(
                                                        id = if (rememberedFavorite) {
                                                            R.string.novel_favorited
                                                        } else {
                                                            R.string.novel_favorite
                                                        }
                                                    )
                                                )
                                            }
                                            if (result.detailed.forumUrl.isNotBlank()) {
                                                OutlinedButton(
                                                    onClick = {
                                                        openExternal(
                                                            context,
                                                            EsjzoneUrls.resolve(result.detailed.forumUrl)
                                                        )
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentPadding = ButtonDefaults.ContentPadding
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Forum,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(text = stringResource(id = R.string.novel_open_forum))
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Button(
                                        enabled = rememberedHistory != null,
                                        onClick = {
                                            rememberedHistory?.let { currChapter ->
                                                navigator?.pushIfNotCurrent(
                                                    ChapterPage(
                                                        result.detailed.id(),
                                                        currChapter,
                                                        history,
                                                        chapterList.orderedChapters,
                                                        novelName = result.detailed.name,
                                                        novelUrl = result.detailed.url,
                                                        novelCoverUrl = result.detailed.coverUrl
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val labelRes = if (rememberedHasHistory) {
                                            R.string.continue_reading
                                        } else {
                                            R.string.start_reading
                                        }
                                        Text(
                                            text = stringResource(id = labelRes),
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    NovelDownloadActions(
                                        novel = result.detailed,
                                        authorization = authorization
                                    )
                                }
                            }
                        }

                        if (result.detailed.tags.isNotEmpty()) {
                            item(key = "novel-tags") {
                                NovelTags(
                                    tags = result.detailed.tags,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }

                        item(key = "novel-description") {
                            Description(
                                description = result.detailed.description,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }

                        item(key = "novel-chapter-list") {
                            ChapterList(
                                chapterList = chapterList,
                                novelId = result.detailed.id(),
                                novelName = result.detailed.name,
                                novelCoverUrl = result.detailed.coverUrl,
                                history = historyState,
                                hasHistory = hasHistory,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        item(key = "novel-comments") {
                            CommentSectionHost(
                                model = commentModel,
                                showHeader = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item(key = "novel-detail-bottom-spacer") {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            screenModel.getDetail()
        }
    }

}

@Composable
private fun NovelStatRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NovelInfoRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val displayValue = value.trim().takeIf { it.isNotBlank() } ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick?.invoke() }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = "$label：",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(58.dp)
        )
        Text(
            text = displayValue,
            style = MaterialTheme.typography.bodySmall,
            color = if (onClick != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NovelTags(
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = R.string.novel_tags),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tags.forEach { tag ->
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private enum class NovelExportFormat {
    TXT,
    EPUB
}

@Composable
private fun NovelDownloadActions(
    novel: DetailedNovel,
    authorization: Authorization,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloaded by remember(novel.url) {
        mutableStateOf<DownloadedNovelManifest?>(NovelDownloadStore.manifest(novel.url))
    }
    var downloading by remember(novel.url) { mutableStateOf(false) }
    var progress by remember(novel.url) { mutableStateOf<DownloadProgress?>(null) }
    var downloadStatusVersion by remember(novel.url) { mutableStateOf(0) }
    var requestedWorkId by rememberSaveable(novel.url) { mutableStateOf<String?>(null) }

    LaunchedEffect(novel.url, downloadStatusVersion) {
        var startupPolls = 0
        do {
            val status = runCatching {
                withContext(Dispatchers.IO) {
                    NovelDownloadManager.status(context, novel.url)
                }
            }.getOrNull()
            startupPolls += 1
            val waitingForEnqueue = requestedWorkId != null &&
                status?.id != requestedWorkId &&
                startupPolls <= 8
            downloading = status?.running == true || waitingForEnqueue
            status?.progress?.let { progress = it }
            if (status?.finished == true) {
                downloaded = withContext(Dispatchers.IO) {
                    NovelDownloadStore.manifest(novel.url)
                }
                if (requestedWorkId == status.id) {
                    Toast.makeText(
                        context,
                        if (status.succeeded) {
                            R.string.novel_download_success
                        } else {
                            R.string.novel_download_failed
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                    requestedWorkId = null
                }
            }
            if (downloading) delay(750)
        } while (isActive && downloading)
    }

    fun export(uri: Uri, format: NovelExportFormat) {
        scope.launch {
            val succeeded = runCatching {
                withContext(Dispatchers.IO) {
                    val manifest = NovelDownloadStore.manifest(novel.url)
                        ?.takeIf { it.complete }
                        ?: error("Novel download is incomplete")
                    val output = context.contentResolver.openOutputStream(uri, "w")
                        ?: error("Unable to open the selected file")
                    output.use { stream ->
                        val loader = { record: com.breakyuna.esjzone.offline.DownloadedChapterRecord ->
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
            }.onFailure { error ->
                com.breakyuna.esjzone.util.AppLogger.e(
                    "NovelPage",
                    "Failed to export ${novel.name} as ${format.name}",
                    error
                )
            }.isSuccess
            Toast.makeText(
                context,
                if (succeeded) R.string.novel_export_success else R.string.novel_export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val txtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { export(it, NovelExportFormat.TXT) }
    }
    val epubLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/epub+zip")
    ) { uri ->
        uri?.let { export(it, NovelExportFormat.EPUB) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        FilledTonalButton(
            enabled = !downloading && novel.chapterList.orderedChapters.isNotEmpty(),
            onClick = {
                progress = DownloadProgress(
                    completed = downloaded?.chapters?.count { it.downloaded } ?: 0,
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
                    downloadStatusVersion += 1
                }.onFailure { error ->
                    downloading = false
                    com.breakyuna.esjzone.util.AppLogger.e(
                        "NovelPage",
                        "Unable to schedule background novel download",
                        error
                    )
                    Toast.makeText(
                        context,
                        R.string.novel_download_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (downloaded?.complete == true) {
                    Icons.Filled.DownloadDone
                } else {
                    Icons.Filled.Download
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    id = if (downloaded?.complete == true) {
                        R.string.novel_download_update
                    } else {
                        R.string.novel_download
                    }
                )
            )
        }

        if (downloading) {
            val currentProgress = progress
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = if (currentProgress == null || currentProgress.total <= 0) {
                    0f
                } else {
                    currentProgress.completed.toFloat() / currentProgress.total.toFloat()
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (currentProgress != null) {
                Text(
                    text = if (currentProgress.chapterName.isBlank()) {
                        stringResource(
                            id = R.string.novel_downloading_count,
                            currentProgress.completed,
                            currentProgress.total
                        )
                    } else {
                        stringResource(
                            id = R.string.novel_downloading,
                            currentProgress.completed,
                            currentProgress.total,
                            currentProgress.chapterName
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (downloaded?.complete == true && !downloading) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        txtLauncher.launch(NovelExporter.suggestedFileName(novel.name, "txt"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextSnippet,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(id = R.string.novel_export_txt))
                }
                FilledTonalButton(
                    onClick = {
                        epubLauncher.launch(NovelExporter.suggestedFileName(novel.name, "epub"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(id = R.string.novel_export_epub))
                }
            }
        }
    }
}

private fun openExternal(context: Context, rawUrl: String) {
    val url = rawUrl.trim()
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        )
    }.onFailure { error ->
        if (error !is ActivityNotFoundException) {
            com.breakyuna.esjzone.util.AppLogger.w(
                "NovelPage",
                "Unable to open external URL $url",
                error
            )
        }
    }
}

class NovelPageModel(
    private val authorization: Authorization,
    private val novel: Novel
) : StateScreenModel<NovelPageModel.State>(State.Loading) {

    private val detailLoadLock = Any()
    private var detailLoadStarted = false

    sealed class State {
        data object Loading : State()
        data object Error : State()
        data class Result(val detailed: DetailedNovel) : State()
    }

    fun getDetail() {
        synchronized(detailLoadLock) {
            if (detailLoadStarted) return
            detailLoadStarted = true
        }
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val fetchedDetail = EsjzoneClient.getNovelDetail(
                    authorization = authorization,
                    novel = novel,
                    includeComments = false
                )
                val detail = if (fetchedDetail.chapterList.orderedChapters.isEmpty()) {
                    NovelDownloadStore.readDetailedNovel(novel.url) ?: fetchedDetail
                } else {
                    fetchedDetail
                }
                ensureActive()
                mutableState.value = State.Result(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val downloaded = NovelDownloadStore.readDetailedNovel(novel.url)
                if (downloaded != null) {
                    mutableState.value = State.Result(downloaded)
                    com.breakyuna.esjzone.util.AppLogger.w(
                        "NovelPageModel",
                        "Using downloaded novel detail for ${novel.name}",
                        e
                    )
                } else {
                    mutableState.value = State.Error
                    com.breakyuna.esjzone.util.AppLogger.e(
                        "NovelPageModel",
                        "Failed to load novel detail for ${novel.name}",
                        e
                    )
                }
            }
        }
    }

    fun retry() {
        synchronized(detailLoadLock) {
            detailLoadStarted = false
        }
        getDetail()
    }

}
