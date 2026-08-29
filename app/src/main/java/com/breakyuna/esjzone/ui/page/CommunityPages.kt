package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getForumCategories
import com.breakyuna.esjzone.network.features.getForumThreads
import com.breakyuna.esjzone.novellibrary.community.ForumCategory
import com.breakyuna.esjzone.novellibrary.community.ForumThread
import com.breakyuna.esjzone.novellibrary.novel.CategoryNovel
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ForumPage : Screen {
    private fun readResolve(): Any = ForumPage

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel { ForumPageModel(authorization) }
        val state by model.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            AppBar(
                title = stringResource(id = R.string.forum),
                onBack = { navigator?.pop() }
            )

            CommunityStateContent(
                state = state,
                emptyText = stringResource(id = R.string.forum_empty)
            ) { categories ->
                val grouped = categories.groupBy { it.groupName.orEmpty() }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    grouped.forEach { (groupName, groupCategories) ->
                        if (groupName.isNotBlank()) {
                            item(key = "group-$groupName") {
                                Text(
                                    text = groupName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        start = 20.dp,
                                        end = 20.dp,
                                        top = 22.dp,
                                        bottom = 8.dp
                                    )
                                )
                            }
                        }
                        items(groupCategories, key = { "forum-category-${it.id}" }) { category ->
                            ForumCategoryCard(category) {
                                navigator?.pushIfNotCurrent(ForumCategoryPage(category))
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        LaunchedEffect(Unit) { model.load() }
    }
}

class ForumCategoryPage(private val category: ForumCategory) : Screen {
    override val key: ScreenKey =
        "ForumCategoryPage:" + category.id.ifBlank { category.url.trim() }

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel { ForumCategoryPageModel(authorization, category) }
        val state by model.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            AppBar(title = category.name, onBack = { navigator?.pop() })
            CommunityStateContent(
                state = state,
                emptyText = stringResource(id = R.string.forum_threads_empty)
            ) { threads ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(threads, key = { "forum-thread-${it.categoryId}-${it.id}" }) { thread ->
                        ForumThreadCard(thread) {
                            // Category entries point to a novel forum board, so
                            // resolve them to the canonical novel detail page.
                            navigator?.pushIfNotCurrent(
                                NovelPage(
                                    CategoryNovel(
                                        name = thread.title,
                                        url = EsjzoneUrls.novelDetailUrlFromForumBoard(thread.url)
                                            ?: "/detail/${thread.id}.html",
                                        forumUrl = thread.url
                                    )
                                )
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        LaunchedEffect(Unit) { model.load() }
    }
}

object GuestbookPage : Screen {
    private fun readResolve(): Any = GuestbookPage

    @Composable
    override fun Content() {
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel {
            CommentPageModel(authorization, EsjzoneUrls.Guestbook)
        }
        CommentListPage(
            title = stringResource(id = R.string.guestbook),
            model = model
        )
    }
}

class ChapterCommentsPage(
    private val chapterName: String,
    private val chapterUrl: String
) : Screen {
    override val key: ScreenKey =
        "ChapterCommentsPage:" + EsjzoneUrls.canonicalPageKey(chapterUrl)
            .ifBlank { chapterName.trim() }

    @Composable
    override fun Content() {
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel {
            CommentPageModel(authorization, chapterUrl)
        }
        CommentListPage(title = chapterName, model = model)
    }
}

@Composable
private fun ForumCategoryCard(category: ForumCategory, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Forum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                category.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            category.postCount?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ForumThreadCard(thread: ForumThread, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                thread.topicCount?.let {
                    Text(
                        text = stringResource(id = R.string.forum_topic_count, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                thread.replyCount?.let {
                    Text(
                        text = stringResource(id = R.string.forum_reply_count, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                thread.lastPostDate?.let {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private class ForumPageModel(
    private val authorization: Authorization
) : StateScreenModel<CommunityState<List<ForumCategory>>>(CommunityState.Loading) {
    private var loadStarted = false

    fun load() {
        if (loadStarted) return
        loadStarted = true
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = try {
                EsjzoneClient.getForumCategories(authorization).toCommunityState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("ForumPageModel", "Failed to load forum categories", error)
                CommunityState.Error
            }
        }
    }
}

private class ForumCategoryPageModel(
    private val authorization: Authorization,
    private val category: ForumCategory
) : StateScreenModel<CommunityState<List<ForumThread>>>(CommunityState.Loading) {
    private var loadStarted = false

    fun load() {
        if (loadStarted) return
        loadStarted = true
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = try {
                EsjzoneClient.getForumThreads(authorization, category).toCommunityState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(
                    "ForumCategoryPageModel",
                    "Failed to load forum category ${category.id}",
                    error
                )
                CommunityState.Error
            }
        }
    }
}

private fun <T> List<T>.toCommunityState(): CommunityState<List<T>> =
    if (isEmpty()) CommunityState.Empty else CommunityState.Result(this)
