package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getHomeData
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.page.NovelListPage
import com.breakyuna.esjzone.ui.page.NovelPage
import com.breakyuna.esjzone.ui.page.ForumPage
import com.breakyuna.esjzone.ui.page.GuestbookPage

object HomeTab : Tab {

    private fun readResolve(): Any = HomeTab

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 0u,
            title = stringResource(id = R.string.screen_main_tab_home),
            icon = rememberVectorPainter(image = Icons.Filled.Home)
        )

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val scope = rememberCoroutineScope()

        val homeTabModel = rememberScreenModel { HomeTabModel(authorization, scope) }
        val state by homeTabModel.state.collectAsState()

        val adult by remember {
            GlobalSettings.adult
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "Explore light novels & translations",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .clickable {
                            navigator?.pushIfNotCurrent(SearchTab)
                        },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 3.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(id = R.string.screen_main_tab_search),
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CommunityShortcut(
                    icon = Icons.Filled.Forum,
                    title = stringResource(id = R.string.forum),
                    subtitle = stringResource(id = R.string.forum_description),
                    modifier = Modifier.weight(1f),
                    onClick = { navigator?.pushIfNotCurrent(ForumPage) }
                )
                CommunityShortcut(
                    icon = Icons.Filled.ChatBubbleOutline,
                    title = stringResource(id = R.string.guestbook),
                    subtitle = stringResource(id = R.string.guestbook_description),
                    modifier = Modifier.weight(1f),
                    onClick = { navigator?.pushIfNotCurrent(GuestbookPage) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recommendation Section
            SectionHeader(
                icon = Icons.Filled.Recommend,
                title = stringResource(id = R.string.tab_home_recommendation),
                onMoreClick = null
            )
            if (state !is HomeTabModel.State.Result) {
                LoadingPlaceholder()
            } else {
                NovelSets(novels = (state as HomeTabModel.State.Result).homeData.recommendation)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Translated Section
            SectionHeader(
                icon = Icons.Filled.Translate,
                title = stringResource(id = R.string.tab_home_recentlyupdate_tranlated),
                onMoreClick = {
                    navigator?.pushIfNotCurrent(NovelListPage(1, 1, false))
                }
            )
            if (state !is HomeTabModel.State.Result) {
                LoadingPlaceholder()
            } else {
                NovelSets(novels = (state as HomeTabModel.State.Result).homeData.recentlyUpdateTranslated)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Original Section
            SectionHeader(
                icon = Icons.Filled.AutoStories,
                title = stringResource(id = R.string.tab_home_recentlyupdate_original),
                onMoreClick = {
                    navigator?.pushIfNotCurrent(NovelListPage(2, 1, false))
                }
            )
            if (state !is HomeTabModel.State.Result) {
                LoadingPlaceholder()
            } else {
                NovelSets(novels = (state as HomeTabModel.State.Result).homeData.recentlyUpdateOriginal)
            }

            if (adult) {
                Spacer(modifier = Modifier.height(16.dp))

                // Translated R18 Section
                SectionHeader(
                    icon = Icons.Filled.LocalFireDepartment,
                    title = stringResource(id = R.string.tab_home_recentlyupdate_tranlated_r18),
                    onMoreClick = {
                        navigator?.pushIfNotCurrent(NovelListPage(1, 1, true))
                    }
                )
                if (state !is HomeTabModel.State.Result) {
                    LoadingPlaceholder()
                } else {
                    NovelSets(novels = (state as HomeTabModel.State.Result).homeData.recentlyUpdateTranslatedR18)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Original R18 Section
                SectionHeader(
                    icon = Icons.Filled.LocalFireDepartment,
                    title = stringResource(id = R.string.tab_home_recentlyupdate_original_r18),
                    onMoreClick = {
                        navigator?.pushIfNotCurrent(NovelListPage(2, 1, true))
                    }
                )
                if (state !is HomeTabModel.State.Result) {
                    LoadingPlaceholder()
                } else {
                    NovelSets(novels = (state as HomeTabModel.State.Result).homeData.recentlyUpdateOriginalR18)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        LaunchedEffect(Unit) {
            homeTabModel.getHomeData()
        }
    }

}

@Composable
private fun CommunityShortcut(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    onMoreClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onMoreClick != null) Modifier.clickable(onClick = onMoreClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (onMoreClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(32.dp)
        )
    }
}

class HomeTabModel(
    private val authorization: Authorization,
    private val scope: CoroutineScope
) : StateScreenModel<HomeTabModel.State>(State.Loading) {

    sealed class State {
        data object Loading : State()
        data class Result(val homeData: HomeData) : State()
    }

    fun getHomeData() {
        scope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val data = EsjzoneClient.getHomeData(authorization)
                ensureActive()
                mutableState.value = State.Result(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e("HomeTabModel", "Failed to load home data", e)
            }
        }
    }

}

@Composable
fun NovelSets(novels: List<CoveredNovel>) {
    val navigator = LocalBaseNavigator.current
    val adult by remember {
        GlobalSettings.adult
    }

    val finalNovels = novels.filter {
        !(it.isAdult && !adult)
    }.distinctBy { it.url.ifBlank { it.name } }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            finalNovels,
            key = { item -> item.url.ifBlank { item.name } }
        ) { novel ->
            Card(
                modifier = Modifier
                    .width(148.dp)
                    .clickable {
                        navigator?.pushIfNotCurrent(NovelPage(novel))
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(194.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp
                                )
                            )
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(novel.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = novel.name,
                            imageLoader = MainActivity.imageLoader,
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            },
                            error = {
                                Image(
                                    painter = painterResource(id = R.drawable.empty_cover),
                                    contentDescription = "",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f)
                                        )
                                    )
                                )
                        )

                        if (novel.isAdult) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = "18+",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
                    ) {
                        Text(
                            text = novel.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 18.sp
                            ),
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NovelMetric(
                                icon = Icons.Filled.RemoveRedEye,
                                value = formatCount(novel.views),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                            NovelMetric(
                                icon = Icons.Filled.ThumbUp,
                                value = formatCount(novel.likes),
                                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelMetric(
    icon: ImageVector,
    value: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = tint
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

@Preview
@Composable
fun FooterPreview() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Text(text = "Powered by ")
        Image(
            painter = painterResource(id = R.drawable.jetpack_compose_high),
            contentScale = ContentScale.Inside,
            contentDescription = "jetpack compose",
            modifier = Modifier.height(20.dp)
        )
        Text(text = "Jetpack Compose", fontWeight = FontWeight.Bold)
    }
}
