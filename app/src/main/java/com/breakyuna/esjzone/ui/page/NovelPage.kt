package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.changeFavorites
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.ChapterList
import com.breakyuna.esjzone.ui.component.Description
import com.breakyuna.esjzone.ui.component.Loading
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator

class NovelPage(
    private val novel: Novel,
    private val history: MutableState<Chapter?> = mutableStateOf(null),
    private val favorite: MutableState<Boolean> = mutableStateOf(false)
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val configuration = LocalConfiguration.current
        val scope = rememberCoroutineScope()

        val screenModel = rememberScreenModel { NovelPageModel(authorization, scope, novel) }
        val state by screenModel.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            AppBar(
                title = novel.name,
                onBack = {
                    navigator.pop()
                }
            )

            when (state) {
                is NovelPageModel.State.Loading -> Loading()

                is NovelPageModel.State.Result -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    val result = state as NovelPageModel.State.Result
                    val chapterList = result.detailed.chapterList

                    val historyState = rememberSaveable {
                        this@NovelPage.history
                    }
                    historyState.value = chapterList.toRead

                    val hasHistory = rememberSaveable {
                        mutableStateOf(chapterList.hasHistory)
                    }

                    val rememberedHistory by rememberSaveable { historyState }
                    val rememberedHasHistory by rememberSaveable { hasHistory }
                    var rememberedFavorite by rememberSaveable { favorite }
                    rememberedFavorite = result.detailed.isFavorite

                    // Top Novel Hero Card
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
                                verticalAlignment = Alignment.Top
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(result.detailed.coverUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = novel.name,
                                    imageLoader = MainActivity.imageLoader,
                                    loading = {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                        }
                                    },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(105.dp)
                                        .height(145.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = result.detailed.author,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Stats Badges
                                    NovelStatRow(
                                        icon = Icons.Filled.RemoveRedEye,
                                        label = "${result.detailed.views} views"
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    NovelStatRow(
                                        icon = Icons.Filled.ThumbUp,
                                        label = "${result.detailed.likes} likes"
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    NovelStatRow(
                                        icon = Icons.Filled.Topic,
                                        label = "${result.detailed.words} words"
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            EsjzoneClient.changeFavorites(authorization, novel)
                                        }
                                        rememberedFavorite = !rememberedFavorite
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (rememberedFavorite) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                                        contentColor = if (rememberedFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (rememberedFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = "Bookmark",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action button
                            Button(
                                enabled = rememberedHistory != null,
                                onClick = {
                                    rememberedHistory?.let { currChapter ->
                                        navigator.push(
                                            ChapterPage(
                                                result.detailed.id(),
                                                currChapter,
                                                historyState
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
                                val labelRes = if (rememberedHasHistory) R.string.continue_reading else R.string.start_reading
                                Text(
                                    text = stringResource(id = labelRes),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    Description(
                        description = result.detailed.description,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    ChapterList(
                        chapterList = chapterList,
                        novelId = result.detailed.id(),
                        history = historyState,
                        hasHistory = hasHistory,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        LaunchedEffect(currentCompositeKeyHash) {
            screenModel.getDetail()
        }
    }

}

@Composable
private fun NovelStatRow(
    icon: ImageVector,
    label: String
) {
    Row(
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

class NovelPageModel(
    private val authorization: Authorization,
    private val scope: CoroutineScope,
    private val novel: Novel
) : StateScreenModel<NovelPageModel.State>(State.Loading) {

    sealed class State {
        data object Loading : State()
        data class Result(val detailed: DetailedNovel) : State()
    }

    fun getDetail() {
        scope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val detail = EsjzoneClient.getNovelDetail(authorization, novel)
                mutableState.value = State.Result(detail)
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e("NovelPageModel", "Failed to load novel detail for ${novel.name}", e)
            }
        }
    }

}


